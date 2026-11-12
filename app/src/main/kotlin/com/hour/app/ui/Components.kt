package com.hour.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.hour.app.text.Korean
import com.hour.app.ui.theme.HourColors
import com.hour.app.ui.theme.HourType
import com.hour.app.ui.theme.color
import com.hour.domain.journey.GeoPoint
import com.hour.domain.journey.JourneySummary
import com.hour.domain.journey.TripCategory

@Composable
fun HourCard(modifier: Modifier = Modifier, padding: Int = 24, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(HourColors.Card)
            .padding(padding.dp),
        content = content,
    )
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false, size: TextUnit = HourType.Small, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (filled) Color.White else color, fontSize = size, fontWeight = FontWeight.SemiBold)
    }
}

/** 큰 버튼(차량 터치 영역 최소 76dp). */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = HourColors.Accent, filled: Boolean = true, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) (if (enabled) color else HourColors.Locked) else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(2.dp, color, RoundedCornerShape(20.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) Color.White else color, fontSize = HourType.Heading, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CategoryChip(category: TripCategory, selected: Boolean, recommended: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = category.color
    Column(
        modifier
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) c else c.copy(alpha = 0.10f))
            .border(if (selected) 0.dp else 2.dp, c.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(category.korean, fontSize = HourType.Title, fontWeight = FontWeight.Bold, color = if (selected) Color.White else c)
        if (recommended) {
            Text("추천", fontSize = HourType.Small, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White.copy(alpha = 0.9f) else c)
        }
    }
}

data class SenseTag(val label: String, val color: Color)

/** 여정에서 느낀 감각을 짧은 태그로. 없는 감각은 빼고 보여준다. */
fun senseTags(s: JourneySummary): List<SenseTag> = buildList {
    if (s.rain.present) add(SenseTag("비 ${s.rain.minutes.coerceAtLeast(1)}분", HourColors.Rain))
    if (s.light.sunsetDrive) add(SenseTag("노을", HourColors.Warm))
    if (s.light.starlight) add(SenseTag("별빛 ${Korean.km(s.light.highBeamKm)}", HourColors.Star))
    else if (s.light.nightMinutes > 0) add(SenseTag("밤길", HourColors.Star))
    if (s.wind.present) add(SenseTag("바람 ${Korean.km(s.wind.km)}", HourColors.Wind))
    val warm = s.warmth.seatHeatMinutes + s.warmth.defrostMinutes
    if (warm > 0) add(SenseTag("온기 ${warm}분", HourColors.Warm))
    if (s.companions.count >= 2) add(SenseTag("동행 ${s.companions.count}명", HourColors.Companion))
    if (s.moments.isNotEmpty()) add(SenseTag("순간 ${s.moments.size}", HourColors.Voice))
}

@Composable
fun SenseDot(tag: SenseTag, size: TextUnit = HourType.Small) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(tag.color))
        Text(" ${tag.label}", fontSize = size, color = HourColors.Ink)
    }
}

/** 지도 타일 없이 경로 모양만 그린다. */
@Composable
fun PathSketch(points: List<GeoPoint>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }
        val spanLat = (maxLat - minLat).coerceAtLeast(1e-6)
        val spanLon = (maxLon - minLon).coerceAtLeast(1e-6)
        val pad = size.minDimension * 0.12f
        val scale = minOf((size.width - pad * 2) / spanLon.toFloat(), (size.height - pad * 2) / spanLat.toFloat())
        val offX = (size.width - spanLon.toFloat() * scale) / 2
        val offY = (size.height - spanLat.toFloat() * scale) / 2
        fun map(p: GeoPoint) = Offset(offX + (p.lon - minLon).toFloat() * scale, offY + (maxLat - p.lat).toFloat() * scale)
        val path = Path()
        points.forEachIndexed { i, p -> val o = map(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White, 14f, map(points.first()))
        drawCircle(color, 14f, map(points.first()), style = Stroke(6f))
        drawCircle(color, 16f, map(points.last()))
    }
}

@Composable
fun Caption(text: String, color: Color = HourColors.Sub, size: TextUnit = HourType.Small, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = size, modifier = modifier)
}
