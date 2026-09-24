@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.tvbox.osc.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.server.ControlManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val QR_BITMAP_SIZE = 640

private const val QR_POLL_INTERVAL_MS = 1500L

/**
 * 扫码输入面板:内嵌在「添加订阅」对话框里使用(TV 上无处输入长地址)。
 *
 * 工作方式:
 * ① 用内置的 [com.github.tvbox.osc.server.RemoteServer] 起一个局域网页 `/qr-input`;
 * ② 把该地址生成二维码显示在电视上;
 * ③ 手机扫码后在网页里输入地址并提交,电视端轮询 `/qr-poll` 取回并回调 [onReceived]。
 *
 * ⚠️ 不能作为独立弹层套在「添加订阅」弹层之上:本项目覆盖层宿主只有一个槽位
 * (见 `SheetHostState.request`),嵌套会互相顶替。故这里只提供"面板内容",由宿主对话框内嵌。
 */
@Composable
fun QrInputPanel(
    onReceived: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var address by remember { mutableStateOf<String?>(null) }
    var pollBase by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val qrBitmap = remember(address) { address?.let { generateQrBitmap(it + "qr-input") } }

    LaunchedEffect(Unit) {
        // 二维码里放局域网地址(手机要能访问);轮询走回环地址(电视自己访问最稳)。
        val addresses = withContext(Dispatchers.IO) {
            runCatching { ControlManager.get().getAddress(false) to ControlManager.get().getAddress(true) }.getOrNull()
        }
        val lan = addresses?.first
        if (lan.isNullOrBlank()) {
            failed = true
        } else {
            address = lan
            pollBase = addresses?.second
        }
    }

    LaunchedEffect(pollBase) {
        val base = pollBase ?: return@LaunchedEffect
        while (isActive) {
            delay(QR_POLL_INTERVAL_MS)
            val value = withContext(Dispatchers.IO) { pollQrInput(base) }
            if (!value.isNullOrBlank()) {
                onReceived(value)
                break
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            failed -> Text(
                text = stringResource(R.string.config_qr_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )

            qrBitmap != null -> Image(
                bitmap = qrBitmap,
                contentDescription = stringResource(R.string.config_scan_qr),
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(8.dp),
            )

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContainedLoadingIndicator(Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.config_qr_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!failed) {
            Text(
                text = stringResource(R.string.config_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int = QR_BITMAP_SIZE): ImageBitmap? = runCatching {
    val hints = hashMapOf<EncodeHintType, Any>(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val row = y * sizePx
        for (x in 0 until sizePx) {
            pixels[row + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    }.asImageBitmap()
}.getOrNull()

private fun pollQrInput(baseUrl: String): String? = runCatching {
    val conn = URL(baseUrl + "qr-poll").openConnection() as HttpURLConnection
    conn.connectTimeout = 1500
    conn.readTimeout = 1500
    conn.requestMethod = "GET"
    try {
        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    } finally {
        conn.disconnect()
    }
}.getOrNull()
