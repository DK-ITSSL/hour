package com.donghaengrok.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** AAOS CarUxRestrictionsManager로 주행 규제 상태를 받는다. */
class PleosUxRestrictionSource(private val context: Context) : UxRestrictionSource {
    private val _state = MutableStateFlow(UxState(source = "pleos"))
    override val state: StateFlow<UxState> = _state.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    override fun start() {
        if (manager != null) return
        runCatching {
            val c = Car.createCar(context)
            val m = c?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            car = c
            manager = m
            m?.currentCarUxRestrictions?.let(::update)
            m?.registerListener { update(it) }
        }.onFailure { Log.w(TAG, "ux restrictions unavailable: $it") }
    }

    private fun update(r: CarUxRestrictions) {
        _state.value = UxState(r.isRequiresDistractionOptimization, r.activeRestrictions, "pleos")
        Log.i(TAG, "ux restrictions DO=${r.isRequiresDistractionOptimization} active=${r.activeRestrictions}")
    }

    override fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private companion object { const val TAG = "DHR.ux" }
}
