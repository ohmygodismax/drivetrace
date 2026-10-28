package dev.drivetrace.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.drivetrace.ui.App
import dev.drivetrace.ui.garage.Garage
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Amber
import dev.drivetrace.ui.theme.Bg
import dev.drivetrace.ui.theme.Ink
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Panel

@Composable fun BottomNavigation(selected: String, alertCount: Int, onSelect: (String) -> Unit) {
    // App applies safeDrawingPadding; this inset keeps the surface clear of rounded display corners.
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp),
        shape = RoundedCornerShape(28.dp), color = Panel, border = BorderStroke(1.dp, Color(0xFF303943)), shadowElevation = 6.dp) {
        BoxWithConstraints {
            val compact = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
            Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    Triple("live", "Приборы", Icons.Outlined.Speed),
                    Triple("trips", "Поездки", Icons.Outlined.Route),
                    Triple("health", "Динамика", Icons.Outlined.MonitorHeart),
                    Triple("garage", "Гараж", Icons.Outlined.Garage),
                    Triple("settings", "Настройки", Icons.Outlined.Settings)
                ).forEach { (id, title, icon) ->
                    val active = selected == id
                    val size = if(compact && !active) Modifier.width(48.dp) else Modifier.weight(1f)
                    val interaction = size.clip(RoundedCornerShape(20.dp))
                        .semantics(mergeDescendants = true) { contentDescription = if(id == "health" && alertCount > 0) "$title, наблюдений: $alertCount" else title }
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(id) })
                    if(compact) {
                        Row(interaction.height(56.dp).background(if(active) Accent else Color.Transparent, RoundedCornerShape(20.dp)).padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Icon(icon, null, tint = if(active) Bg else Muted, modifier = Modifier.size(21.dp))
                                if(id == "health" && alertCount > 0) Box(Modifier.align(Alignment.TopEnd).size(5.dp).background(if(active) Bg else Amber, RoundedCornerShape(3.dp)))
                            }
                            if(active) Text(title, fontSize = 12.sp, color = Bg, fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                        }
                    } else {
                        Column(interaction.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.width(52.dp).height(30.dp).background(if(active) Accent else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = if(active) Bg else Muted, modifier = Modifier.size(21.dp))
                                if(id == "health" && alertCount > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 3.dp).size(6.dp)
                                    .background(if(active) Bg else Amber, RoundedCornerShape(3.dp)))
                            }
                            Text(title, fontSize = 11.sp, lineHeight = 13.sp, color = if(active) Ink else Muted,
                                fontWeight = if(active) FontWeight.Medium else FontWeight.Normal, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
