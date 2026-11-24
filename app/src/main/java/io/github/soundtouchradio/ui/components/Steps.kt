package io.github.soundtouchradio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.soundtouchradio.discovery.LinkStep
import io.github.soundtouchradio.discovery.StepState
import io.github.soundtouchradio.ui.theme.Tabular

/** Lista de pasos con luz piloto: gris pendiente, verde correcto, ámbar aviso, rojo fallo. Compartida por vincular y Wi-Fi. */
@Composable
fun StepList(steps: List<LinkStep>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        steps.forEach { s ->
            val stepState = when (s.state) {
                StepState.OK -> "correcto"; StepState.RUNNING -> "en curso"; StepState.WARNING -> "aviso"
                StepState.FAILED -> "fallo"; StepState.PENDING -> "pendiente"
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) { stateDescription = stepState },
                verticalAlignment = Alignment.Top,
            ) {
                PilotLight(
                    when (s.state) {
                        StepState.OK -> Pilot.ON; StepState.RUNNING -> Pilot.BUSY; StepState.WARNING -> Pilot.WARN
                        StepState.FAILED -> Pilot.ERROR; StepState.PENDING -> Pilot.OFF
                    },
                    Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge,
                        color = if (s.state == StepState.PENDING) cs.onSurfaceVariant else cs.onSurface)
                    s.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium.merge(io.github.soundtouchradio.ui.theme.ProseNumbers),
                            color = when (s.state) { StepState.FAILED -> cs.error; StepState.WARNING -> cs.tertiary; else -> cs.onSurfaceVariant })
                    }
                }
            }
        }
    }
}
