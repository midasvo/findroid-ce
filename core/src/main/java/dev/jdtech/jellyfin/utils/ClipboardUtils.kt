package dev.jdtech.jellyfin.utils

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import kotlinx.coroutines.launch

@Composable
fun Modifier.copyOnLongClick(
    text: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
): Modifier {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(CoreR.string.copied_text_label)

    return this.combinedClickable(
        enabled = enabled,
        onClick = onClick ?: {},
        onLongClickLabel = stringResource(CoreR.string.copy_text_action),
        onLongClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, text)))
            }
            // Only show a toast for Android 12 and lower; newer versions show their own confirmation.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        },
    )
}
