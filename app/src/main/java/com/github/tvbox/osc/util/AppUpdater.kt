package com.github.tvbox.osc.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.github.catvod.net.OkHttp
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** 远端版本清单(与 APK 同目录部署) */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String,
)

/**
 * 应用自更新:检测版本 -> 下载 APK(带进度) -> 调起系统安装器。
 *
 * 不依赖 GitHub,版本清单与安装包都由自有服务器提供(见 [MANIFEST_URL])。
 * 安装包落在应用外部私有目录 `Android/data/<pkg>/files/update/`,经 [FileProvider] 授权给系统安装器,
 * 因此不需要任何存储权限;Android 8+ 首次安装需用户允许"安装未知来源应用"。
 */
object AppUpdater {

    private const val MANIFEST_URL = "https://tvbox.moliys.icu/apk/version.json"
    private const val CHECK_TIMEOUT_MS = 8000L
    private const val CONNECT_TIMEOUT_MS = 15000L
    private const val BUFFER_SIZE = 64 * 1024

    @Suppress("DEPRECATION")
    fun currentVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else info.versionCode
    } catch (e: Exception) {
        LOG.e("AppUpdater", "read versionCode failed", e)
        0
    }

    /** 拉取远端版本清单;网络失败或格式错误返回 null */
    fun fetchLatest(): UpdateInfo? {
        val text = OkHttp.string(MANIFEST_URL, CHECK_TIMEOUT_MS)
        if (text.isBlank()) return null
        return try {
            val json = JSONObject(text)
            val url = json.optString("url").trim()
            if (url.isEmpty()) {
                null
            } else {
                UpdateInfo(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName").trim(),
                    url = url,
                    notes = json.optString("notes").trim(),
                )
            }
        } catch (e: Exception) {
            LOG.e("AppUpdater", "parse version manifest failed", e)
            null
        }
    }

    /** 下载 APK,进度为 0..100 回调;成功返回文件,失败返回 null */
    fun download(context: Context, url: String, onProgress: (Int) -> Unit): File? {
        return try {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val dir = File(base, "update")
            if (!dir.exists() && !dir.mkdirs()) return null
            val target = File(dir, "moliys-shell-${System.currentTimeMillis()}.apk")
            val client = OkHttp.client(CONNECT_TIMEOUT_MS).newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val total = body.contentLength()
                var written = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val percent = (written * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent.coerceIn(0, 100))
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (target.length() > 0) target else null
        } catch (e: Exception) {
            LOG.e("AppUpdater", "download apk failed", e)
            null
        }
    }

    /** 是否已获得"安装未知来源应用"权限(Android 8 以下恒为 true) */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** 跳到"允许安装未知来源应用"设置页 */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            LOG.e("AppUpdater", "open install permission failed", e)
        }
    }

    /** 调起系统安装器安装本地 APK */
    fun installApk(context: Context, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (e: Exception) {
            LOG.e("AppUpdater", "install apk failed", e)
        }
    }
}
