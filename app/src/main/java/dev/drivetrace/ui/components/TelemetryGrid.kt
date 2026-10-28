package dev.drivetrace.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.drivetrace.model.Frame
import dev.drivetrace.model.ObdMetric
import dev.drivetrace.model.value
import dev.drivetrace.ui.theme.Instruments
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Secondary

@Composable fun TelemetryGrid(frame: Frame,supported: Set<ObdMetric>) {
    ObdMetric.entries.filter { it !in setOf(ObdMetric.RPM,ObdMetric.SPEED,ObdMetric.LOAD) }.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            row.forEach { metric ->
                val value=frame.value(metric)
                CardBox(Modifier.weight(1f)) {
                    Text(metric.title,fontSize=11.sp,color=Muted,minLines=2)
                    Text(value?.let { "%.1f".format(it) } ?: "—",fontFamily=Instruments,fontSize=24.sp,modifier=Modifier.padding(top=7.dp))
                    Text(if(metric !in supported)"Не поддерживается" else if(value==null)"Нет свежих данных" else metric.unit,fontSize=10.sp,color=if(metric !in supported)Muted else Secondary,modifier=Modifier.padding(top=4.dp))
                }
            }
            if(row.size==1)Spacer(Modifier.weight(1f))
        }
    }
    CardBox(Modifier.fillMaxWidth()) {
        val pressure=if(frame.manifoldKpa!=null && frame.baroKpa!=null)frame.manifoldKpa-frame.baroKpa else null
        Text("Давление относительно атмосферы",fontSize=12.sp,color=Muted)
        Text(pressure?.let { "%.2f бар".format(it/100) } ?: "—",fontFamily=Instruments,fontSize=22.sp,modifier=Modifier.padding(top=8.dp))
        Text("Впуск минус атмосферное давление. При разрежении значение отрицательное.",fontSize=10.sp,color=Muted,lineHeight=16.sp,modifier=Modifier.padding(top=5.dp))
    }
}
