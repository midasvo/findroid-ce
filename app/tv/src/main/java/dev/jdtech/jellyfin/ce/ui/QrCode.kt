package dev.jdtech.jellyfin.ce.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * CE (#211): a QR code, dark on a white card, so a phone can open [text] (a link) from across the
 * room. Error correction L keeps the modules as large as possible for a long link.
 */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix =
        remember(text) {
            QRCodeWriter()
                .encode(
                    text,
                    BarcodeFormat.QR_CODE,
                    0,
                    0,
                    mapOf(
                        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                        EncodeHintType.MARGIN to 0,
                    ),
                )
        }
    Canvas(
        modifier =
            modifier
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .size(size)
    ) {
        val module = this.size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * module, y * module),
                        // A hair larger, so neighbouring modules don't leave seams.
                        size = Size(module + 0.5f, module + 0.5f),
                    )
                }
            }
        }
    }
}
