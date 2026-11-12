package com.donghaengrok.car

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.donghaengrok.domain.signal.AnchorType
import com.donghaengrok.domain.signal.DriveGear
import com.donghaengrok.domain.signal.PassPointKind
import com.donghaengrok.domain.signal.SeatPosition
import com.donghaengrok.domain.signal.SignalEvent
import com.donghaengrok.domain.signal.WindowPosition
import com.donghaengrok.domain.signal.WiperLevel
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.awaitClose

/**
 * Pleos Vehicle/NaviHelper/FusedLocation SDK와 AAOS Car API를 [SignalEvent] 흐름으로 감싼다.
 * - 모든 리스너는 callbackFlow 안에서 등록하고, 같은 객체·area로 해제한다.
 * - 개별 신호 실패는 [SignalEvent.Unavailable]로 내보내고 나머지 신호는 계속 흐른다.
 */
class PleosCarSignalSource(private val context: Context) : CarSignalSource {

    private val injected = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 64)
    private var vehicle: Vehicle? = null

    @Synchronized
    private fun vehicle(): Vehicle = vehicle ?: Vehicle(context).also {
        it.initialize()
        vehicle = it
        Log.i(TAG, "Vehicle initialized")
    }

    @Synchronized
    private fun releaseVehicle() {
        vehicle?.let { runCatching { it.release() } }
        vehicle = null
    }

    override fun inject(event: SignalEvent) {
        injected.tryEmit(event)
    }

    override fun signals(): Flow<SignalEvent> = merge(
        injected,
        guarded("gear") { gear() },
        guarded("odometer") { odometer() },
        guarded("wiper") { wiper() },
        guarded("highBeam") { light(high = true) },
        guarded("lowBeam") { light(high = false) },
        guarded("window") { windows() },
        guarded("seat") { seats() },
        guarded("seatHeat") { seatHeat() },
        guarded("defroster") { defroster() },
        guarded("aaos") { aaosProperties().throttleSpeed() },
        guarded("navi") { navi() },
        guarded("location") { location() },
    ).dedupe().onCompletion { releaseVehicle() }

    // ---------------------------------------------------------------- vehicle

    private fun gear(): Flow<SignalEvent> = callbackFlow {
        val api = vehicle().getGear()
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                trySend(SignalEvent.Gear(now(), gear?.toDriveGear() ?: DriveGear.UNKNOWN))
            }
            override fun onFailed(e: Exception) {
                trySend(unavailable("gear", e))
            }
        }
        api.getCurrentGearSelectionState({ g -> g?.let { trySend(SignalEvent.Gear(now(), it.toDriveGear())) } }, { e -> trySend(unavailable("gear.get", e)) })
        api.registerGearSelection(listener)
        awaitClose { runCatching { api.unregisterGearSelection(listener) } }
    }

    private fun odometer(): Flow<SignalEvent> = callbackFlow {
        val api = vehicle().getOdometer()
        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                value?.let { trySend(SignalEvent.Odometer(now(), it)) }
            }
            override fun onFailed(e: Exception) {
                trySend(unavailable("odometer", e))
            }
        }
        api.registerDistanceDriven(listener)
        awaitClose { runCatching { api.unregisterDistanceDriven(listener) } }
    }

    private fun wiper(): Flow<SignalEvent> = callbackFlow {
        val api = vehicle().getWiper()
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val poll = AtomicReference<Job?>(null)

        fun readSwitch(on: Boolean) {
            api.getCurrentWiperSwitch(area, { sw ->
                Log.d(TAG, "wiper switch=$sw on=$on")
                trySend(SignalEvent.Wiper(now(), on, if (on) wiperLevelOf(sw?.name) else WiperLevel.OFF))
            }, { e -> trySend(unavailable("wiper.switch", e)) })
        }

        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                val on = state == WiperState.ON
                Log.d(TAG, "wiper state=$state")
                readSwitch(on)
                poll.getAndSet(null)?.cancel()
                // WiperSwitch는 리스너가 없으므로 와이퍼가 켜져 있는 동안만 주기적으로 읽는다.
                if (on) poll.set(launch { while (isActive) { delay(SWITCH_POLL_MS); readSwitch(true) } })
            }
            override fun onFailed(e: Exception) {
                trySend(unavailable("wiper", e))
            }
        }
        api.registerWiperState(area, listener)
        awaitClose {
            poll.get()?.cancel()
            runCatching { api.unregisterWiperState(area, listener) }
        }
    }

    private fun light(high: Boolean): Flow<SignalEvent> = callbackFlow {
        val api = vehicle().getLight()
        val listener = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                val on = state == LightState.ON
                trySend(if (high) SignalEvent.HighBeam(now(), on) else SignalEvent.LowBeam(now(), on))
            }
            override fun onFailed(e: Exception) {
                trySend(unavailable(if (high) "highBeam" else "lowBeam", e))
            }
        }
        if (high) api.registerHighBeamLightState(listener) else api.registerLowBeamLightState(listener)
        awaitClose {
            runCatching { if (high) api.unregisterHighBeamLightState(listener) else api.unregisterLowBeamLightState(listener) }
        }
    }

    private fun windows(): Flow<SignalEvent> = callbackFlow {
        val api = vehicle().getWindow()
        val registered = mutableListOf<Pair<WindowArea, WindowPositionListener>>()
        api.checkWindowPositionCapabilities({ config, bounds ->
            Log.i(TAG, "window caps config=$config areas=${bounds.keys.map { it.values.toList() }}")
            bounds.forEach { (area, range) ->
                val pos = area.values.firstOrNull()?.toWindowPosition() ?: return@forEach
                val min = range.minValue
                val max = range.maxValue
                val listener = object : WindowPositionListener {
                    override fun onWindowPositionUpdated(position: Int?) {
                        if (position == null) return
                        Log.d(TAG, "window $pos raw=$position bounds=$min..$max")
                        trySend(SignalEvent.Window(now(), pos, openRatio(position, min, max)))
                    }
                    override fun onFailed(e: Exception) {
                        trySend(unavailable("window.$pos", e))
                    }
                }
                api.registerWindowPosition(area, listener)
                synchronized(registered) { registered += area to listener }
            }
        }, { e -> trySend(unavailable("window.caps", e)) })
        awaitClose {
            synchronized(registered) { registered.forEach { (a, l) -> runCatching { api.unregisterWindowPosition(a, l) } } }
        }
    }

    private fun seats(): Flow<SignalEvent> = callbackFlow {
        val api = vehicle().getSeat()
        val occupancy = mutableListOf<Pair<SeatArea, SeatOccupancyListener>>()
        val belts = mutableListOf<Pair<SeatArea, SeatBeltBuckledStateListener>>()

        api.checkSeatOccupancyCapabilities({ config, areas ->
            Log.i(TAG, "seat occupancy caps config=$config areas=${areas.map { it.values.toList() }}")
            areas.forEach { area ->
                val seat = area.values.firstOrNull()?.toSeatPosition() ?: return@forEach
                val listener = object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
                        occupancy?.let { trySend(SignalEvent.SeatOccupancy(now(), seat, it)) }
                    }
                    override fun onFailed(e: Exception) {
                        trySend(unavailable("seat.occupancy.$seat", e))
                    }
                }
                api.registerSeatOccupancy(area, listener)
                synchronized(occupancy) { occupancy += area to listener }
            }
        }, { e -> trySend(unavailable("seat.occupancy.caps", e)) })

        api.checkSeatBeltBuckledCapabilities({ config, areas ->
            Log.i(TAG, "seat belt caps config=$config areas=${areas.map { it.values.toList() }}")
            areas.forEach { area ->
                val seat = area.values.firstOrNull()?.toSeatPosition() ?: return@forEach
                val listener = object : SeatBeltBuckledStateListener {
                    override fun onSeatBeltBuckledStateUpdated(state: Boolean?) {
                        state?.let { trySend(SignalEvent.SeatBelt(now(), seat, it)) }
                    }
                    override fun onFailed(e: Exception) {
                        trySend(unavailable("seat.belt.$seat", e))
                    }
                }
                api.registerSeatBeltBuckledState(area, listener)
                synchronized(belts) { belts += area to listener }
            }
        }, { e -> trySend(unavailable("seat.belt.caps", e)) })

        awaitClose {
            synchronized(occupancy) { occupancy.forEach { (a, l) -> runCatching { api.unregisterSeatOccupancy(a, l) } } }
            synchronized(belts) { belts.forEach { (a, l) -> runCatching { api.unregisterSeatBeltBuckledState(a, l) } } }
        }
    }

    private fun seatHeat(): Flow<SignalEvent> = callbackFlow {
        val api = vehicle().getSeat()
        val registered = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT).map { value ->
            val area = SeatArea(value)
            val seat = value.toSeatPosition() ?: SeatPosition.DRIVER
            val listener = object : HvacSeatTemperatureListener {
                override fun onHvacSeatTemperatureUpdated(temperature: Int?) {
                    temperature?.let { trySend(SignalEvent.SeatHeat(now(), seat, it)) }
                }
                override fun onFailed(e: Exception) {
                    trySend(unavailable("seatHeat.$seat", e))
                }
            }
            api.registerHvacSeatTemperature(area, listener)
            area to listener
        }
        awaitClose { registered.forEach { (a, l) -> runCatching { api.unregisterHvacSeatTemperature(a, l) } } }
    }

    private fun defroster(): Flow<SignalEvent> = callbackFlow {
        val api = vehicle().getHVAC()
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val listener = object : HvacDefrosterStateListener {
            override fun onHvacDefrosterStateUpdated(state: Boolean?) {
                state?.let { trySend(SignalEvent.Defroster(now(), it)) }
            }
            override fun onFailed(e: Exception) {
                trySend(unavailable("defroster", e))
            }
        }
        api.registerHvacDefrosterState(area, listener)
        awaitClose { runCatching { api.unregisterHvacDefrosterState(area, listener) } }
    }

    // ---------------------------------------------------------------- AAOS

    private fun aaosProperties(): Flow<SignalEvent> = callbackFlow {
        val car = Car.createCar(context)
        val cpm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (cpm == null) {
            trySend(unavailable("aaos", "CarPropertyManager unavailable"))
            runCatching { car?.disconnect() }
            close()
            return@callbackFlow
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                when (value.propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                        (value.value as? Float)?.let { trySend(SignalEvent.Speed(now(), it * 3.6f)) }
                    VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE ->
                        (value.value as? Float)?.let { trySend(SignalEvent.OutsideTemperature(now(), it)) }
                    VehiclePropertyIds.GEAR_SELECTION ->
                        Log.d(TAG, "aaos GEAR_SELECTION=${value.value}")
                }
            }
            override fun onErrorEvent(propertyId: Int, zone: Int) {
                trySend(unavailable("aaos.${VehiclePropertyIds.toString(propertyId)}", "error zone=$zone"))
            }
        }
        listOf(
            VehiclePropertyIds.PERF_VEHICLE_SPEED to 2f,
            VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE to 1f,
            VehiclePropertyIds.GEAR_SELECTION to CarPropertyManager.SENSOR_RATE_ONCHANGE,
        ).forEach { (id, rate) ->
            val name = VehiclePropertyIds.toString(id)
            try {
                val ok = cpm.registerCallback(callback, id, rate)
                Log.i(TAG, "aaos register $name ok=$ok")
                if (!ok) trySend(unavailable("aaos.$name", "registerCallback returned false"))
            } catch (e: Exception) {
                trySend(unavailable("aaos.$name", e))
            }
        }
        awaitClose {
            runCatching { cpm.unregisterCallback(callback) }
            runCatching { car.disconnect() }
        }
    }

    // ---------------------------------------------------------------- navi

    private fun navi(): Flow<SignalEvent> = callbackFlow {
        val navi = NaviHelper(context)
        val listener = object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                trySend(SignalEvent.NaviAddress(now(), currentLocationInfo.address, currentLocationInfo.latitude, currentLocationInfo.longitude))
            }
            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                trySend(SignalEvent.NaviArrived(now(), destinationArrivedInfo.name, destinationArrivedInfo.address))
            }
            override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
                tbtInfoList.forEach { tbt ->
                    passPointOf(tbt.type.name)?.let { trySend(SignalEvent.NaviPassPoint(now(), it, tbt.distance)) }
                }
            }
            override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
                trySend(SignalEvent.NaviSpeedLimit(now(), speedLimit))
            }
            override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
                cautionPointList.forEach { trySend(SignalEvent.NaviCaution(now(), it.type.name, it.remainDistance)) }
            }
            override fun onNavigationStatus(status: NavigationStatus) {
                trySend(SignalEvent.NaviStatus(now(), status.name))
            }
            override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
                Log.i(TAG, "bookmarks=${bookmarkInfoList.map { "${it.category}:${it.name}" }}")
                bookmarkInfoList.forEach { b ->
                    // 미등록 즐겨찾기는 이름·주소가 비고 좌표가 0,0으로 온다(에뮬레이터 확인). 등록으로 보지 않는다.
                    if (b.name.isBlank() && b.address.isBlank() && b.latitude == 0.0 && b.longitude == 0.0) return@forEach
                    val type = when (b.category) {
                        BookmarkType.HOME -> AnchorType.HOME
                        BookmarkType.OFFICE -> AnchorType.OFFICE
                        else -> null
                    } ?: return@forEach
                    trySend(SignalEvent.NaviAnchor(now(), type, b.name, b.address, b.latitude, b.longitude))
                }
            }
            override fun onBookMarkChanged() {
                navi.getBookmarkInfo()
            }
            override fun onError(errorCode: NaviErrorCode) {
                trySend(unavailable("navi", "error=$errorCode"))
            }
        }
        navi.initialize({
            Log.i(TAG, "NaviHelper connected")
            navi.addListener(listener)
            navi.getBookmarkInfo()
            navi.getCurrentLocationInfo()
            navi.getDestinationInfo()
        }, { t -> trySend(unavailable("navi.init", t)) })
        awaitClose {
            runCatching { navi.removeListener(listener) }
            runCatching { navi.release() }
        }
    }

    // ---------------------------------------------------------------- location

    private fun location(): Flow<SignalEvent> = callbackFlow {
        if (!granted(PERM_FUSED_LOCATION)) {
            trySend(unavailable("location", "permission $PERM_FUSED_LOCATION not granted"))
            close()
            return@callbackFlow
        }
        val fused = FusedLocation(context)
        if (!fused.isSupported()) {
            trySend(unavailable("location", "FusedLocation service not supported"))
            close()
            return@callbackFlow
        }
        fused.initialize()
        val listener = object : FusedLocationListener {
            override fun onChanged(data: LocationData?) {
                data?.let { trySend(SignalEvent.Location(now(), it.latitude, it.longitude, it.altitude)) }
            }
            override fun onError(e: Exception) {
                trySend(unavailable("location", e))
            }
        }
        fused.registerFusedLocationCallback(listener)
        awaitClose {
            runCatching { fused.unregisterFusedLocationCallback(listener) }
            runCatching { fused.release() }
        }
    }

    // ---------------------------------------------------------------- probe (D1 검증)

    override suspend fun probe(): List<ProbeItem> {
        val items = mutableListOf<ProbeItem>()
        PROBE_PERMISSIONS.forEach { p ->
            items += ProbeItem("perm ${p.substringAfterLast('.')} (${p.substringBefore(".permission")})", if (granted(p)) ProbeStatus.OK else ProbeStatus.FAILED)
        }
        val v = runCatching { vehicle() }.getOrElse {
            items += ProbeItem("vehicle.initialize", ProbeStatus.FAILED, it.toString())
            return items
        }
        items += check("gear.current") { ok, fail -> v.getGear().getCurrentGearSelectionState({ ok("$it") }, fail) }
        items += check("odometer.current") { ok, fail -> v.getOdometer().getCurrentDistanceDriven({ ok("$it km") }, fail) }
        items += check("wiper.stateCaps") { ok, fail -> v.getWiper().checkWiperStateCapabilities({ c, m -> ok("$c ${m.mapKeys { it.key.values.toList() }}") }, fail) }
        items += check("wiper.switchCaps") { ok, fail -> v.getWiper().checkWiperSwitchCapabilities({ c, m -> ok("$c ${m.mapKeys { it.key.values.toList() }}") }, fail) }
        items += check("highBeam.stateCaps") { ok, fail -> v.getLight().checkHighBeamLightStateCapabilities({ c, l -> ok("$c $l") }, fail) }
        items += check("window.positionCaps") { ok, fail -> v.getWindow().checkWindowPositionCapabilities({ c, m -> ok("$c ${m.map { (a, b) -> "${a.values.toList()}=${b.minValue}..${b.maxValue}" }}") }, fail) }
        items += check("seat.occupancyCaps") { ok, fail -> v.getSeat().checkSeatOccupancyCapabilities({ c, l -> ok("$c ${l.map { it.values.toList() }}") }, fail) }
        items += check("seat.beltCaps") { ok, fail -> v.getSeat().checkSeatBeltBuckledCapabilities({ c, l -> ok("$c ${l.map { it.values.toList() }}") }, fail) }
        items += check("seat.heat.driver") { ok, fail -> v.getSeat().getHvacSeatTemperature(SeatArea(SeatValue.ROW_1_LEFT), { ok("$it") }, fail) }
        items += check("defroster.front") { ok, fail -> v.getHVAC().getHvacDefrosterState(WindowArea(WindowValue.FRONT_WINDSHIELD), { ok("$it") }, fail) }
        items += ProbeItem("fusedLocation.isSupported", if (runCatching { FusedLocation(context).isSupported() }.getOrDefault(false)) ProbeStatus.OK else ProbeStatus.UNSUPPORTED)
        items += check("navi.initialize") { ok, fail ->
            val navi = NaviHelper(context)
            navi.initialize({ ok("connected"); runCatching { navi.release() } }, { t -> fail(Exception(t)) })
        }
        return items
    }

    private suspend fun check(name: String, block: (ok: (String) -> Unit, fail: (Exception) -> Unit) -> Unit): ProbeItem =
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    block(
                        { detail -> if (cont.isActive) cont.resume(ProbeItem(name, ProbeStatus.OK, detail)) },
                        { e -> if (cont.isActive) cont.resume(ProbeItem(name, statusOf(e), e.toString())) },
                    )
                }.onFailure { e -> if (cont.isActive) cont.resume(ProbeItem(name, ProbeStatus.FAILED, e.toString())) }
            }
        } ?: ProbeItem(name, ProbeStatus.FAILED, "timeout ${PROBE_TIMEOUT_MS}ms")

    // ---------------------------------------------------------------- helpers

    private fun guarded(name: String, block: () -> Flow<SignalEvent>): Flow<SignalEvent> =
        runCatching(block).getOrElse { e -> kotlinx.coroutines.flow.flowOf(unavailable(name, e)) }
            .catch { e -> emit(unavailable(name, e)) }

    /** 같은 신호의 같은 값이 연달아 오면 버린다(등록 직후 초기값이 중복으로 오고, 주행거리계는 같은 값을 주기적으로 다시 보낸다). */
    private fun Flow<SignalEvent>.dedupe(): Flow<SignalEvent> {
        val last = HashMap<String, SignalEvent>()
        return filter { e ->
            val key = when (e) {
                is SignalEvent.Window -> "Window.${e.position}"
                is SignalEvent.SeatOccupancy -> "Occ.${e.seat}"
                is SignalEvent.SeatBelt -> "Belt.${e.seat}"
                is SignalEvent.SeatHeat -> "Heat.${e.seat}"
                is SignalEvent.Moment, is SignalEvent.Unavailable, is SignalEvent.NaviPassPoint, is SignalEvent.NaviCaution,
                is SignalEvent.Speed, is SignalEvent.NaviSpeedLimit -> return@filter true
                else -> e::class.simpleName ?: "?"
            }
            val prev = last[key]
            val same = prev != null && stripTime(prev) == stripTime(e)
            last[key] = e
            !same
        }
    }

    private fun stripTime(e: SignalEvent): SignalEvent = when (e) {
        is SignalEvent.Gear -> e.copy(at = 0)
        is SignalEvent.Odometer -> e.copy(at = 0)
        is SignalEvent.Wiper -> e.copy(at = 0)
        is SignalEvent.HighBeam -> e.copy(at = 0)
        is SignalEvent.LowBeam -> e.copy(at = 0)
        is SignalEvent.Window -> e.copy(at = 0)
        is SignalEvent.SeatOccupancy -> e.copy(at = 0)
        is SignalEvent.SeatBelt -> e.copy(at = 0)
        is SignalEvent.SeatHeat -> e.copy(at = 0)
        is SignalEvent.Defroster -> e.copy(at = 0)
        is SignalEvent.OutsideTemperature -> e.copy(at = 0)
        is SignalEvent.NaviAddress -> e.copy(at = 0)
        is SignalEvent.NaviSpeedLimit -> e.copy(at = 0)
        is SignalEvent.NaviStatus -> e.copy(at = 0)
        is SignalEvent.NaviAnchor -> e.copy(at = 0)
        is SignalEvent.Location -> e.copy(at = 0)
        else -> e
    }

    /** 속도는 요청한 주기와 무관하게 초당 5~10회 들어온다(에뮬레이터 확인). 1km/h 이상 변하거나 1초가 지났을 때만 내보낸다. */
    private fun Flow<SignalEvent>.throttleSpeed(): Flow<SignalEvent> {
        var lastKmh = Float.NaN
        var lastAt = 0L
        return filter { e ->
            if (e !is SignalEvent.Speed) return@filter true
            val pass = lastKmh.isNaN() || kotlin.math.abs(e.kmh - lastKmh) >= 1f || e.at - lastAt >= 1_000L
            if (pass) { lastKmh = e.kmh; lastAt = e.at }
            pass
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun unavailable(name: String, e: Throwable): SignalEvent {
        Log.w(TAG, "unavailable $name: $e")
        return SignalEvent.Unavailable(now(), name, e.toString())
    }

    private fun unavailable(name: String, reason: String): SignalEvent {
        Log.w(TAG, "unavailable $name: $reason")
        return SignalEvent.Unavailable(now(), name, reason)
    }

    private fun statusOf(e: Exception): ProbeStatus =
        if (e.toString().contains("NOT_SUPPORTED", ignoreCase = true)) ProbeStatus.UNSUPPORTED else ProbeStatus.FAILED

    companion object {
        private const val TAG = "DHR.car"
        private const val SWITCH_POLL_MS = 5_000L
        private const val PROBE_TIMEOUT_MS = 4_000L
        const val PERM_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"

        val PROBE_PERMISSIONS = listOf(
            "pleos.car.permission.CAR_DRIVE",
            "pleos.car.permission.CAR_WINDOWS",
            "pleos.car.permission.CAR_LIGHTS",
            "pleos.car.permission.CAR_SEATS",
            "pleos.car.permission.CAR_CLIMATE",
            "pleos.car.permission.NAVI_ROUTE",
            "pleos.car.permission.NAVI_ROUTE_SEARCH",
            "pleos.car.permission.TTS_SERVICE",
            "pleos.car.permission.STT_SERVICE",
            PERM_FUSED_LOCATION,
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_POWERTRAIN",
            "android.car.permission.CAR_EXTERIOR_ENVIRONMENT",
            "android.permission.RECORD_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.INTERNET",
        )

        private fun now() = System.currentTimeMillis()

        private fun VehicleGear.toDriveGear(): DriveGear = when {
            this == VehicleGear.GEAR_PARK -> DriveGear.PARK
            this == VehicleGear.GEAR_REVERSE -> DriveGear.REVERSE
            this == VehicleGear.GEAR_NEUTRAL -> DriveGear.NEUTRAL
            this == VehicleGear.GEAR_DRIVE || name.removePrefix("GEAR_").toIntOrNull() != null -> DriveGear.DRIVE
            else -> DriveGear.UNKNOWN
        }

        private fun WindowValue.toWindowPosition(): WindowPosition? = when (this) {
            WindowValue.ROW_1_LEFT -> WindowPosition.FRONT_LEFT
            WindowValue.ROW_1_RIGHT -> WindowPosition.FRONT_RIGHT
            WindowValue.ROW_2_LEFT -> WindowPosition.REAR_LEFT
            WindowValue.ROW_2_RIGHT -> WindowPosition.REAR_RIGHT
            WindowValue.ROOF_TOP_1, WindowValue.ROOF_TOP_2 -> WindowPosition.SUNROOF
            else -> null
        }

        /** 좌핸들(LHD) 기준: 1열 왼쪽이 운전석. */
        private fun SeatValue.toSeatPosition(): SeatPosition? = when (this) {
            SeatValue.ROW_1_LEFT -> SeatPosition.DRIVER
            SeatValue.ROW_1_RIGHT -> SeatPosition.FRONT_PASSENGER
            SeatValue.ROW_2_LEFT -> SeatPosition.REAR_LEFT
            SeatValue.ROW_2_CENTER -> SeatPosition.REAR_CENTER
            SeatValue.ROW_2_RIGHT -> SeatPosition.REAR_RIGHT
            else -> null
        }

        private fun passPointOf(turnType: String): PassPointKind? = when (turnType) {
            "TURN_TUNNEL" -> PassPointKind.TUNNEL
            "TURN_ENTER_SERVICEAREA" -> PassPointKind.SERVICE_AREA
            "TURN_RESTAREA" -> PassPointKind.REST_AREA
            "TURN_TOLL" -> PassPointKind.TOLLGATE
            else -> null
        }
    }
}
