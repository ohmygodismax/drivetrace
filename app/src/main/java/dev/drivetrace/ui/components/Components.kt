package dev.drivetrace.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.drivetrace.R
import dev.drivetrace.domain.route.LoadBand
import dev.drivetrace.domain.route.estimatedLoadPct
import dev.drivetrace.model.Frame
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Bg
import dev.drivetrace.ui.theme.Ink
import dev.drivetrace.ui.theme.Instruments
import dev.drivetrace.ui.theme.Line
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Panel
import dev.drivetrace.ui.theme.Secondary
import kotlin.math.*

@Composable fun Brand(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val showTagline = maxWidth >= 240.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DriveTrace", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, maxLines = 1)
            if(showTagline) {
                Box(Modifier.width(1.dp).height(16.dp).background(Line))
                Text("бортовой журнал", color = Muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
@Composable fun Badge(text: String, color: Color = Secondary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(when(text) { "SIMULATED" -> "Симуляция"; "LIVE" -> "Поток"; "SIMULATOR" -> "Симулятор"; else -> text }, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
@Composable fun Eyebrow(text: String) { Text(text.lowercase().replaceFirstChar { it.titlecase() }, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp) }
@Composable fun Heading(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp); Text(subtitle, fontSize = 12.sp, color = Muted) } }
@Composable fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Column(modifier.clip(RoundedCornerShape(6.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(6.dp)).padding(16.dp), content = content) }
@Composable fun Action(text: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.height(50.dp), enabled = enabled, shape = RoundedCornerShape(5.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
}

@Composable fun VehiclePhoto(modifier: Modifier = Modifier) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Box(modifier.clip(RoundedCornerShape(5.dp))) {
        Image(painter = androidx.compose.ui.res.painterResource(R.drawable.tayron), contentDescription = "Volkswagen Tayron 2023, вид спереди", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Text("User3204 · CC BY-SA 4.0", color = Color.White, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.55f)).clickable { uri.openUri("https://commons.wikimedia.org/wiki/File:2023_FAW-Volkswagen_Tayron_300TSI_(facelift,_front).jpg") }.padding(5.dp, 3.dp))
    }
}

@Composable fun MiniValue(label: String, value: String, unit: String) { Column { Text(label.lowercase().replaceFirstChar { it.titlecase() }, fontSize = 10.sp, color = Muted); Text(value, fontSize = 22.sp, fontFamily = Instruments, modifier = Modifier.padding(top = 5.dp)); Text(unit, fontSize = 9.sp, color = Muted) } }
@Composable fun Sparkline(frames: List<Frame>, modifier: Modifier, position: Float? = null, rpm: Boolean = false) {
    Canvas(modifier) {
        for(i in 1..3) drawLine(Line,Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1f)
        if(frames.size < 2) return@Canvas
        val path = Path(); var drawing = false
        frames.forEach { f ->
            val value = if(rpm) f.rpm?.let { it / 5000f } else f.speed?.let { (it / 140).toFloat() }
            if(value == null) drawing = false else {
                val x = size.width * (f.elapsed - frames.first().elapsed).toFloat() / max(1L, frames.last().elapsed - frames.first().elapsed)
                val y = size.height * (1 - value.coerceIn(0f, 1f))
                if(!drawing) path.moveTo(x,y) else path.lineTo(x,y)
                drawing = true
            }
        }
        drawPath(path,if(rpm) Secondary else Accent,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
        position?.let { drawLine(Ink,Offset(size.width*it,0f),Offset(size.width*it,size.height),2f) }
    }
}

@Composable fun LoadLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(LoadBand.NORMAL, LoadBand.ELEVATED, LoadBand.HIGH).forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Box(Modifier.width(13.dp).height(3.dp).background(Color(android.graphics.Color.parseColor(band.hex)))); Text(band.label, fontSize = 10.sp, color = Muted) }
        }
    }
}

@Composable fun LoadReading(frame: Frame) {
    val load = frame.estimatedLoadPct
    val color = Color(android.graphics.Color.parseColor(LoadBand.of(load).hex))
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if(frame.engineLoad != null) "Нагрузка ЭБУ" else "Оценка нагрузки", fontSize = 11.sp, color = Muted); Text(load?.let { "%.0f %%".format(it) } ?: "—", fontSize = 12.sp, fontFamily = Instruments, color = color) }
        LinearProgressIndicator(progress = { ((load ?: 0.0) / 100).toFloat() }, modifier = Modifier.fillMaxWidth().height(3.dp), color = color, trackColor = Line, drawStopIndicator = {})
    }
}
