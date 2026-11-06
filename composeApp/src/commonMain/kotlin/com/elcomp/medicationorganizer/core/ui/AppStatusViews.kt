package com.elcomp.medicationorganizer.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.resources.Res
import com.elcomp.medicationorganizer.resources.component_error_title
import com.elcomp.medicationorganizer.resources.component_loading
import com.elcomp.medicationorganizer.resources.component_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppLoadingState(
    modifier: Modifier = Modifier,
    message: String = stringResource(Res.string.component_loading),
) {
    AppStatusMessage(title = null, message = message, modifier = modifier) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

@Composable
fun AppEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    AppStatusMessage(title = title, message = message, modifier = modifier) {
        Canvas(Modifier.size(32.dp)) {
            val strokeWidth = 2.dp.toPx()
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width * 0.15f, size.height * 0.22f),
                size = Size(size.width * 0.7f, size.height * 0.56f),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(strokeWidth),
            )
            drawLine(
                color = tint,
                start = Offset(size.width * 0.4f, center.y),
                end = Offset(size.width * 0.6f, center.y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun AppErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val tint = MaterialTheme.colorScheme.error
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppStatusMessage(
            title = stringResource(Res.string.component_error_title),
            message = message,
        ) {
            Canvas(Modifier.size(32.dp)) {
                val strokeWidth = 2.dp.toPx()
                drawCircle(tint, radius = size.minDimension / 2 - strokeWidth, style = Stroke(strokeWidth))
                drawLine(
                    color = tint,
                    start = Offset(center.x, size.height * 0.29f),
                    end = Offset(center.x, size.height * 0.53f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth / 2,
                    center = Offset(center.x, size.height * 0.68f),
                )
            }
        }
        if (onRetry != null) {
            AppActionButton(text = stringResource(Res.string.component_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun AppStatusMessage(
    title: String?,
    message: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
