package com.hour.app.ui.art

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.color
import com.hour.domain.unlock.AssetSlot
import com.hour.domain.unlock.UnlockRule
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 캐릭터·애셋 그림.
 *
 * 퀘스트와 애셋은 게임 디자인 단계에서 다시 정한다. 그때까지는 코드로 그린 임시 그림을 쓰고,
 * 실제 그림 파일을 assets/art/ 아래에 같은 이름으로 넣으면 자동으로 그 파일을 쓴다.
 *   art/character/base.png   기본 캐릭터(정사각형, 투명 배경)
 *   art/layer/<규칙 id>.png  캐릭터 위에 겹칠 레이어(base와 같은 크기·위치)
 *   art/icon/<규칙 id>.png   옷장·선물 화면 아이콘(정사각형)
 * 규칙 id는 domain/src/main/resources/unlock_rules.json의 id다.
 */

enum class Glyph { RAINCOAT, CAPE, BOOTS, GLOVES, UMBRELLA, BADGE, PASSPORT, BASKET, CHARM, FLOWERS, BOOK, HAT, STAR }

data class ArtSpec(val glyph: Glyph, val main: Color, val sub: Color = Color.White)

object ArtCatalog {
    private val specs = mapOf(
        "trip_raincoat" to ArtSpec(Glyph.RAINCOAT, Color(0xFFF4C430), Color(0xFFB8860B)),
        "trip_star_cape" to ArtSpec(Glyph.CAPE, Color(0xFF4B3F8F), Color(0xFFFFE27A)),
        "trip_passport" to ArtSpec(Glyph.PASSPORT, Color(0xFF1F3A68), Color(0xFFE8C26A)),
        "trip_gangwon_raincoat" to ArtSpec(Glyph.RAINCOAT, Color(0xFF2E8FD1), Color(0xFFFFFFFF)),
        "trip_safe_charm" to ArtSpec(Glyph.CHARM, Color(0xFFD9443B), Color(0xFFF2C14E)),
        "commute_umbrella" to ArtSpec(Glyph.UMBRELLA, Color(0xCCBFD9EC), Color(0xFF5B7A93)),
        "commute_badge" to ArtSpec(Glyph.BADGE, Color(0xFF3D6FB6), Color(0xFFFFFFFF)),
        "date_couple_umbrella" to ArtSpec(Glyph.UMBRELLA, Color(0xFFF29BB0), Color(0xFFB3566C)),
        "date_bouquet" to ArtSpec(Glyph.FLOWERS, Color(0xFFE0607E), Color(0xFF5E9440)),
        "date_escort_gloves" to ArtSpec(Glyph.GLOVES, Color(0xFFFAF7F0), Color(0xFFB9B2A6)),
        "daily_boots" to ArtSpec(Glyph.BOOTS, Color(0xFF6A9F3D), Color(0xFF3F6B1F)),
        "daily_basket" to ArtSpec(Glyph.BASKET, Color(0xFFC08A4E), Color(0xFF8A5A2B)),
        "any_diary" to ArtSpec(Glyph.BOOK, Color(0xFF8C5A3C), Color(0xFFF2E6CF)),
    )

    fun spec(rule: UnlockRule): ArtSpec = specs[rule.id] ?: ArtSpec(
        glyph = when (rule.slot) {
            AssetSlot.HAT -> Glyph.HAT
            AssetSlot.OUTFIT -> Glyph.CAPE
            AssetSlot.ACCESSORY -> Glyph.STAR
            AssetSlot.ROOM -> Glyph.BOOK
        },
        main = rule.category?.color ?: HourColors.Amber,
    )
}

/** assets/art/의 실제 그림. 없으면 null(임시 그림으로 대체). */
object ArtFiles {
    const val BASE = "art/character/base.png"
    fun icon(id: String) = "art/icon/$id.png"
    fun layer(id: String) = "art/layer/$id.png"

    private val cache = HashMap<String, ImageBitmap?>()

    fun load(context: Context, path: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(path) {
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
}

@Composable
private fun rememberArt(path: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(path) { ArtFiles.load(context, path) }
}

/** 옷장·선물 아이콘. 잠긴 애셋은 실루엣으로 그린다. */
@Composable
fun ItemIcon(rule: UnlockRule, locked: Boolean, modifier: Modifier = Modifier) {
    val art = rememberArt(ArtFiles.icon(rule.id))
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = rule.name,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            colorFilter = if (locked) ColorFilter.tint(HourColors.Locked) else null,
        )
        return
    }
    val spec = ArtCatalog.spec(rule).let { if (locked) it.copy(main = HourColors.Locked, sub = Color(0xFFC9C3B8)) else it }
    Canvas(modifier) {
        val side = size.minDimension * 0.82f
        drawItem(spec, Offset((size.width - side) / 2, (size.height - side) / 2), side)
    }
}

/**
 * 캐릭터와 방. 레이어 순서: 방 → (망토) → 몸 → 옷 → 소품 → 모자.
 * 레이어 그림 파일이 있으면 그 파일을, 없으면 임시 그림을 쓴다.
 */
@Composable
fun CharacterStage(equipped: List<UnlockRule>, modifier: Modifier = Modifier, room: Boolean = true) {
    val roomItems = equipped.filter { it.slot == AssetSlot.ROOM }
    val wearables = equipped.filter { it.slot != AssetSlot.ROOM }
        .sortedBy { z(it) }
    val base = rememberArt(ArtFiles.BASE)
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        if (room) Canvas(Modifier.matchParentSize()) { drawRoom(roomItems) }
        Box(Modifier.fillMaxHeight(if (room) 0.84f else 1f).aspectRatio(1f)) {
            wearables.filter { z(it) < 1 }.forEach { Layer(it) }
            if (base != null) {
                Image(base, "아워", Modifier.matchParentSize(), contentScale = ContentScale.Fit)
            } else {
                Canvas(Modifier.matchParentSize()) { drawBody() }
            }
            wearables.filter { z(it) >= 1 }.forEach { Layer(it) }
        }
    }
}

@Composable
private fun Layer(rule: UnlockRule) {
    val art = rememberArt(ArtFiles.layer(rule.id))
    if (art != null) {
        Image(art, rule.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    } else {
        val spec = ArtCatalog.spec(rule)
        Canvas(Modifier.fillMaxSize()) { drawWearable(rule.slot, spec) }
    }
}

private fun z(rule: UnlockRule): Int {
    val spec = ArtCatalog.spec(rule)
    return when {
        rule.slot == AssetSlot.OUTFIT && spec.glyph == Glyph.CAPE -> 0
        rule.slot == AssetSlot.OUTFIT -> 2
        rule.slot == AssetSlot.ACCESSORY -> 3
        else -> 4
    }
}

// ---------------------------------------------------------------- 그리기

private fun Color.dark(f: Float = 0.72f) = Color(red * f, green * f, blue * f, alpha)

private val Cream = Color(0xFFFFF1D6)
private val Edge = Color(0xFFE2C79A)
private val InkBrown = Color(0xFF3B3226)

/** 기본 캐릭터 '아워'(가칭): 자명종 시계 모양의 동반자. */
private fun DrawScope.drawBody() {
    val s = size.minDimension
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    drawOval(Color(0x22000000), topLeft = o(0.27f, 0.86f), size = Size(0.46f * s, 0.06f * s))
    // 종(자명종)
    for (x in listOf(0.31f, 0.69f)) {
        drawCircle(HourColors.Amber, 0.075f * s, o(x, 0.25f))
        drawCircle(Color(0xFFD98A1F), 0.075f * s, o(x, 0.25f), style = Stroke(0.012f * s))
    }
    // 발
    drawOval(Edge, topLeft = o(0.36f, 0.79f), size = Size(0.12f * s, 0.07f * s))
    drawOval(Edge, topLeft = o(0.52f, 0.79f), size = Size(0.12f * s, 0.07f * s))
    // 몸(시계판)
    drawCircle(Cream, 0.30f * s, o(0.5f, 0.53f))
    drawCircle(Edge, 0.30f * s, o(0.5f, 0.53f), style = Stroke(0.016f * s))
    for (i in 0 until 12) {
        val a = (i * 30.0 - 90.0) * PI / 180.0
        val r1 = if (i % 3 == 0) 0.235f else 0.252f
        val r2 = 0.272f
        drawLine(
            Edge,
            Offset((0.5f + r1 * cos(a).toFloat()) * s, (0.53f + r1 * sin(a).toFloat()) * s),
            Offset((0.5f + r2 * cos(a).toFloat()) * s, (0.53f + r2 * sin(a).toFloat()) * s),
            strokeWidth = 0.012f * s, cap = StrokeCap.Round,
        )
    }
    // 손
    for (x in listOf(0.19f, 0.81f)) {
        drawCircle(Cream, 0.045f * s, o(x, 0.62f))
        drawCircle(Edge, 0.045f * s, o(x, 0.62f), style = Stroke(0.012f * s))
    }
    // 얼굴
    for (x in listOf(0.43f, 0.57f)) {
        drawCircle(InkBrown, 0.024f * s, o(x, 0.50f))
        drawCircle(Color.White, 0.008f * s, o(x + 0.008f, 0.492f))
    }
    drawOval(Color(0x55F28B9B), topLeft = o(0.34f, 0.55f), size = Size(0.07f * s, 0.035f * s))
    drawOval(Color(0x55F28B9B), topLeft = o(0.59f, 0.55f), size = Size(0.07f * s, 0.035f * s))
    drawArc(
        InkBrown, startAngle = 20f, sweepAngle = 140f, useCenter = false,
        topLeft = o(0.465f, 0.53f), size = Size(0.07f * s, 0.05f * s),
        style = Stroke(0.012f * s, cap = StrokeCap.Round),
    )
}

/** 캐릭터 몸에 맞춘 착용 그림. */
private fun DrawScope.drawWearable(slot: AssetSlot, spec: ArtSpec) {
    val s = size.minDimension
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    val rightHand = o(0.81f, 0.62f)
    val leftHand = o(0.19f, 0.62f)
    when (spec.glyph) {
        Glyph.CAPE -> {
            val p = Path().apply {
                moveTo(0.30f * s, 0.46f * s); lineTo(0.70f * s, 0.46f * s)
                lineTo(0.88f * s, 0.88f * s)
                quadraticTo(0.5f * s, 0.95f * s, 0.12f * s, 0.88f * s); close()
            }
            drawPath(p, spec.main)
            for ((x, y, r) in listOf(Triple(0.18f, 0.80f, 0.03f), Triple(0.82f, 0.76f, 0.028f), Triple(0.78f, 0.87f, 0.022f), Triple(0.24f, 0.88f, 0.02f))) {
                drawStar(o(x, y), r * s, spec.sub)
            }
        }
        Glyph.RAINCOAT -> {
            drawArc(
                spec.main, startAngle = 195f, sweepAngle = 150f, useCenter = false,
                topLeft = o(0.18f, 0.21f), size = Size(0.64f * s, 0.64f * s), style = Stroke(0.06f * s, cap = StrokeCap.Round),
            )
            val p = Path().apply {
                moveTo(0.23f * s, 0.66f * s)
                quadraticTo(0.5f * s, 0.61f * s, 0.77f * s, 0.66f * s)
                lineTo(0.80f * s, 0.83f * s)
                quadraticTo(0.5f * s, 0.92f * s, 0.20f * s, 0.83f * s); close()
            }
            drawPath(p, spec.main)
            drawCircle(spec.sub, 0.018f * s, o(0.5f, 0.72f))
            drawCircle(spec.sub, 0.018f * s, o(0.5f, 0.80f))
        }
        Glyph.BOOTS -> for (x in listOf(0.35f, 0.52f)) {
            drawRoundRect(spec.main, topLeft = o(x, 0.74f), size = Size(0.13f * s, 0.14f * s), cornerRadius = CornerRadius(0.03f * s))
            drawLine(spec.sub, o(x + 0.01f, 0.77f), o(x + 0.12f, 0.77f), strokeWidth = 0.012f * s)
        }
        Glyph.GLOVES -> for (c in listOf(leftHand, rightHand)) {
            drawCircle(spec.main, 0.056f * s, c)
            drawCircle(spec.sub, 0.056f * s, c, style = Stroke(0.012f * s))
        }
        Glyph.HAT -> {
            drawRoundRect(spec.main, topLeft = o(0.34f, 0.07f), size = Size(0.32f * s, 0.17f * s), cornerRadius = CornerRadius(0.06f * s))
            drawRect(spec.sub, topLeft = o(0.34f, 0.19f), size = Size(0.32f * s, 0.03f * s))
            drawOval(spec.main.dark(0.85f), topLeft = o(0.24f, 0.21f), size = Size(0.52f * s, 0.07f * s))
        }
        Glyph.BADGE -> {
            val lanyard = Path().apply {
                moveTo(0.40f * s, 0.40f * s); lineTo(0.5f * s, 0.62f * s); lineTo(0.60f * s, 0.40f * s)
            }
            drawPath(lanyard, spec.main, style = Stroke(0.012f * s))
            drawRoundRect(Color.White, topLeft = o(0.44f, 0.61f), size = Size(0.12f * s, 0.15f * s), cornerRadius = CornerRadius(0.015f * s))
            drawRoundRect(spec.main, topLeft = o(0.44f, 0.61f), size = Size(0.12f * s, 0.15f * s), cornerRadius = CornerRadius(0.015f * s), style = Stroke(0.01f * s))
            drawRect(spec.main.copy(alpha = 0.5f), topLeft = o(0.465f, 0.635f), size = Size(0.07f * s, 0.05f * s))
        }
        Glyph.UMBRELLA -> {
            // 머리 위로 비스듬히 든 우산
            drawLine(spec.sub, o(0.62f, 0.20f), rightHand, strokeWidth = 0.014f * s, cap = StrokeCap.Round)
            drawArc(spec.main, 180f, 180f, useCenter = true, topLeft = o(0.30f, 0.02f), size = Size(0.64f * s, 0.36f * s))
            drawArc(spec.sub, 180f, 180f, useCenter = false, topLeft = o(0.30f, 0.02f), size = Size(0.64f * s, 0.36f * s), style = Stroke(0.008f * s))
        }
        Glyph.CHARM -> drawItem(spec, Offset(leftHand.x - 0.11f * s, leftHand.y - 0.12f * s), 0.22f * s)
        else -> {
            val side = if (slot == AssetSlot.ACCESSORY) 0.22f * s else 0.2f * s
            drawItem(spec, Offset(rightHand.x - side / 2, rightHand.y - side * 0.6f), side)
        }
    }
}

/** 방 배경과 진열 소품. */
private fun DrawScope.drawRoom(items: List<UnlockRule>) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFBF7F0), Color(0xFFF1E9DC))), size = Size(w, h * 0.78f))
    drawRect(Color(0xFFE7DAC4), topLeft = Offset(0f, h * 0.78f), size = Size(w, h * 0.22f))
    drawLine(Color(0xFFD9C8AB), Offset(0f, h * 0.78f), Offset(w, h * 0.78f), strokeWidth = 3f)
    // 창문
    // 왼쪽 위 이름·소개 글자(약 100dp)와 겹치지 않게 그 아래부터 그린다.
    val textArea = 104.dp.toPx()
    val wx = w * 0.07f
    val wy = maxOf(h * 0.10f, textArea)
    val ww = minOf(w * 0.2f, (h * 0.78f - wy) * 0.6f, h * 0.32f)
    val wh = ww * 1.15f
    drawRoundRect(Color(0xFFCFE6F5), topLeft = Offset(wx, wy), size = Size(ww, wh), cornerRadius = CornerRadius(12f))
    drawRoundRect(Color(0xFFC9B89C), topLeft = Offset(wx, wy), size = Size(ww, wh), cornerRadius = CornerRadius(12f), style = Stroke(8f))
    drawLine(Color(0xFFC9B89C), Offset(wx + ww / 2, wy), Offset(wx + ww / 2, wy + wh), strokeWidth = 6f)
    drawLine(Color(0xFFC9B89C), Offset(wx, wy + wh / 2), Offset(wx + ww, wy + wh / 2), strokeWidth = 6f)
    if (items.isNotEmpty()) {
        // 오른쪽 벽 선반에 진열한다.
        val side = minOf(h * 0.14f, w * 0.17f)
        val shelfY = maxOf(h * 0.30f, textArea + side)
        val shelfX = w * 0.95f - (side * 1.1f) * items.size - side * 0.2f
        drawRoundRect(Color(0xFFC9B89C), topLeft = Offset(shelfX, shelfY), size = Size(w * 0.95f - shelfX, 10f), cornerRadius = CornerRadius(4f))
        items.forEachIndexed { i, rule ->
            drawItem(ArtCatalog.spec(rule), Offset(shelfX + side * 0.1f + i * side * 1.1f, shelfY - side * 0.98f), side)
        }
    }
}

private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = (i * 36.0 - 90.0) * PI / 180.0
        val x = center.x + rr * cos(a).toFloat()
        val y = center.y + rr * sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

/** 애셋 단독 그림(아이콘·손에 든 소품·방 소품). origin에서 한 변이 side인 정사각형 안에 그린다. */
private fun DrawScope.drawItem(spec: ArtSpec, origin: Offset, side: Float) {
    fun o(x: Float, y: Float) = Offset(origin.x + x * side, origin.y + y * side)
    fun sz(w: Float, h: Float) = Size(w * side, h * side)
    val main = spec.main
    val sub = spec.sub
    when (spec.glyph) {
        Glyph.RAINCOAT -> {
            val p = Path().apply {
                moveTo(o(0.32f, 0.34f).x, o(0.32f, 0.34f).y); lineTo(o(0.68f, 0.34f).x, o(0.68f, 0.34f).y)
                lineTo(o(0.86f, 0.86f).x, o(0.86f, 0.86f).y)
                quadraticTo(o(0.5f, 0.96f).x, o(0.5f, 0.96f).y, o(0.14f, 0.86f).x, o(0.14f, 0.86f).y); close()
            }
            drawPath(p, main)
            drawCircle(main, 0.18f * side, o(0.5f, 0.30f))
            drawCircle(Cream, 0.11f * side, o(0.5f, 0.32f))
            drawCircle(sub, 0.025f * side, o(0.5f, 0.58f))
            drawCircle(sub, 0.025f * side, o(0.5f, 0.70f))
        }
        Glyph.CAPE -> {
            val p = Path().apply {
                moveTo(o(0.30f, 0.20f).x, o(0.30f, 0.20f).y); lineTo(o(0.70f, 0.20f).x, o(0.70f, 0.20f).y)
                lineTo(o(0.90f, 0.88f).x, o(0.90f, 0.88f).y)
                quadraticTo(o(0.5f, 0.96f).x, o(0.5f, 0.96f).y, o(0.10f, 0.88f).x, o(0.10f, 0.88f).y); close()
            }
            drawPath(p, main)
            drawRoundRect(main.dark(), topLeft = o(0.27f, 0.14f), size = sz(0.46f, 0.09f), cornerRadius = CornerRadius(0.04f * side))
            drawStar(o(0.38f, 0.48f), 0.06f * side, sub)
            drawStar(o(0.63f, 0.62f), 0.05f * side, sub)
            drawStar(o(0.44f, 0.76f), 0.04f * side, sub)
        }
        Glyph.BOOTS -> for (x in listOf(0.14f, 0.52f)) {
            drawRoundRect(main, topLeft = o(x, 0.18f), size = sz(0.22f, 0.52f), cornerRadius = CornerRadius(0.05f * side))
            drawRoundRect(main, topLeft = o(x, 0.58f), size = sz(0.34f, 0.18f), cornerRadius = CornerRadius(0.08f * side))
            drawLine(sub, o(x + 0.02f, 0.26f), o(x + 0.20f, 0.26f), strokeWidth = 0.03f * side)
        }
        Glyph.GLOVES -> for ((x, y) in listOf(0.18f to 0.30f, 0.50f to 0.38f)) {
            drawRoundRect(main, topLeft = o(x, y), size = sz(0.30f, 0.40f), cornerRadius = CornerRadius(0.14f * side))
            drawRoundRect(sub, topLeft = o(x, y), size = sz(0.30f, 0.40f), cornerRadius = CornerRadius(0.14f * side), style = Stroke(0.025f * side))
            drawOval(main, topLeft = o(x - 0.07f, y + 0.12f), size = sz(0.12f, 0.18f))
            drawOval(sub, topLeft = o(x - 0.07f, y + 0.12f), size = sz(0.12f, 0.18f), style = Stroke(0.025f * side))
        }
        Glyph.UMBRELLA -> {
            drawLine(sub, o(0.5f, 0.43f), o(0.5f, 0.82f), strokeWidth = 0.035f * side, cap = StrokeCap.Round)
            drawArc(sub, 0f, 180f, useCenter = false, topLeft = o(0.5f, 0.76f), size = sz(0.14f, 0.12f), style = Stroke(0.035f * side, cap = StrokeCap.Round))
            drawArc(main, 180f, 180f, useCenter = true, topLeft = o(0.08f, 0.16f), size = sz(0.84f, 0.54f))
            drawArc(sub, 180f, 180f, useCenter = false, topLeft = o(0.08f, 0.16f), size = sz(0.84f, 0.54f), style = Stroke(0.02f * side))
        }
        Glyph.BADGE -> {
            val lanyard = Path().apply {
                moveTo(o(0.30f, 0.04f).x, o(0.30f, 0.04f).y); lineTo(o(0.5f, 0.38f).x, o(0.5f, 0.38f).y); lineTo(o(0.70f, 0.04f).x, o(0.70f, 0.04f).y)
            }
            drawPath(lanyard, main, style = Stroke(0.04f * side))
            drawRoundRect(Color.White, topLeft = o(0.28f, 0.36f), size = sz(0.44f, 0.54f), cornerRadius = CornerRadius(0.05f * side))
            drawRoundRect(main, topLeft = o(0.28f, 0.36f), size = sz(0.44f, 0.54f), cornerRadius = CornerRadius(0.05f * side), style = Stroke(0.03f * side))
            drawRect(main.copy(alpha = 0.45f), topLeft = o(0.37f, 0.45f), size = sz(0.26f, 0.2f))
            drawLine(main, o(0.36f, 0.74f), o(0.64f, 0.74f), strokeWidth = 0.03f * side)
        }
        Glyph.PASSPORT -> {
            drawRoundRect(main, topLeft = o(0.24f, 0.12f), size = sz(0.52f, 0.74f), cornerRadius = CornerRadius(0.05f * side))
            drawCircle(sub, 0.11f * side, o(0.5f, 0.42f), style = Stroke(0.025f * side))
            drawRect(sub, topLeft = o(0.36f, 0.64f), size = sz(0.28f, 0.04f))
        }
        Glyph.BASKET -> {
            drawArc(main.dark(), 180f, 180f, useCenter = false, topLeft = o(0.26f, 0.16f), size = sz(0.48f, 0.52f), style = Stroke(0.045f * side))
            val p = Path().apply {
                moveTo(o(0.14f, 0.42f).x, o(0.14f, 0.42f).y); lineTo(o(0.86f, 0.42f).x, o(0.86f, 0.42f).y)
                lineTo(o(0.75f, 0.86f).x, o(0.75f, 0.86f).y); lineTo(o(0.25f, 0.86f).x, o(0.25f, 0.86f).y); close()
            }
            drawPath(p, main)
            for (y in listOf(0.56f, 0.70f)) drawLine(sub, o(0.2f, y), o(0.8f, y), strokeWidth = 0.025f * side)
        }
        Glyph.CHARM -> {
            drawCircle(sub, 0.07f * side, o(0.5f, 0.17f), style = Stroke(0.03f * side))
            val p = Path().apply {
                moveTo(o(0.36f, 0.30f).x, o(0.36f, 0.30f).y); lineTo(o(0.64f, 0.30f).x, o(0.64f, 0.30f).y)
                quadraticTo(o(0.86f, 0.86f).x, o(0.86f, 0.86f).y, o(0.5f, 0.88f).x, o(0.5f, 0.88f).y)
                quadraticTo(o(0.14f, 0.86f).x, o(0.14f, 0.86f).y, o(0.36f, 0.30f).x, o(0.36f, 0.30f).y); close()
            }
            drawPath(p, main)
            drawLine(sub, o(0.36f, 0.35f), o(0.64f, 0.35f), strokeWidth = 0.035f * side)
            drawCircle(sub, 0.07f * side, o(0.5f, 0.62f))
        }
        Glyph.FLOWERS -> {
            for (x in listOf(0.32f, 0.5f, 0.68f)) drawLine(sub, o(0.5f, 0.86f), o(x, 0.38f), strokeWidth = 0.03f * side)
            val wrap = Path().apply {
                moveTo(o(0.32f, 0.56f).x, o(0.32f, 0.56f).y); lineTo(o(0.68f, 0.56f).x, o(0.68f, 0.56f).y)
                lineTo(o(0.5f, 0.94f).x, o(0.5f, 0.94f).y); close()
            }
            drawPath(wrap, Color(0xFFF2E6CF))
            drawCircle(main, 0.11f * side, o(0.32f, 0.36f))
            drawCircle(main.dark(0.88f), 0.12f * side, o(0.5f, 0.26f))
            drawCircle(HourColors.Amber, 0.11f * side, o(0.68f, 0.36f))
            for ((x, y) in listOf(0.32f to 0.36f, 0.5f to 0.26f, 0.68f to 0.36f)) drawCircle(Color(0xFFFFF4DC), 0.035f * side, o(x, y))
        }
        Glyph.BOOK -> {
            drawRoundRect(main, topLeft = o(0.18f, 0.18f), size = sz(0.64f, 0.64f), cornerRadius = CornerRadius(0.04f * side))
            drawRect(sub, topLeft = o(0.76f, 0.22f), size = sz(0.05f, 0.56f))
            drawRect(main.dark(), topLeft = o(0.28f, 0.18f), size = sz(0.06f, 0.64f))
            drawRoundRect(sub, topLeft = o(0.42f, 0.34f), size = sz(0.28f, 0.12f), cornerRadius = CornerRadius(0.02f * side))
        }
        Glyph.HAT -> {
            drawRoundRect(main, topLeft = o(0.28f, 0.26f), size = sz(0.44f, 0.40f), cornerRadius = CornerRadius(0.08f * side))
            drawRect(sub, topLeft = o(0.28f, 0.54f), size = sz(0.44f, 0.06f))
            drawOval(main.dark(0.85f), topLeft = o(0.10f, 0.60f), size = sz(0.80f, 0.16f))
        }
        Glyph.STAR -> {
            drawStar(o(0.5f, 0.52f), 0.40f * side, main)
            drawStar(o(0.5f, 0.52f), 0.16f * side, sub)
        }
    }
}
