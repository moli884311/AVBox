@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.tvbox.osc.ui.page

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.components.AVBoxAlertDialog
import com.github.tvbox.osc.ui.components.AppTopBarScaffold
import com.github.tvbox.osc.ui.components.AVBoxBottomSheet
import com.github.tvbox.osc.ui.components.LocalSheetDismiss
import com.github.tvbox.osc.ui.components.LocalSheetDismissThen
import com.github.tvbox.osc.ui.components.SettingsCard
import com.github.tvbox.osc.ui.components.SettingsCardPosition
import com.github.tvbox.osc.ui.components.SettingsGroup
import com.github.tvbox.osc.ui.components.SettingsOptionMenuRow
import com.github.tvbox.osc.ui.components.SettingsRow
import com.github.tvbox.osc.ui.tv.tvControlFocus
import com.github.tvbox.osc.ui.activity.ConfigManageActivity
import com.github.tvbox.osc.ui.activity.DanmuSettingsActivity
import com.github.tvbox.osc.ui.activity.PlaySettingsActivity
import com.github.tvbox.osc.ui.activity.PreferenceSettingsActivity
import com.github.tvbox.osc.ui.activity.PreloadSettingsActivity
import com.github.tvbox.osc.ui.activity.ThemeSettingsActivity
import com.github.tvbox.osc.util.AppUpdater
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.UpdateInfo
import com.github.tvbox.osc.util.HistoryMerge
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.MusicSettings
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val playType: Int,
    val playRender: Int,
    val playScale: Int,
    val ijkCodec: String,
    val exoDecode: String,
    val ijkCachePlay: Boolean,
    val playTunnel: Boolean,
    val preferAac: Boolean,
    val musicPlayerPage: Boolean,
    val autoSwitchLine: Boolean,
    val m3u8Purify: Boolean,
    val incognito: Boolean,
    val gestureControlDisabled: Boolean,
    val navAnimationDisabled: Boolean,
    val danmuOpen: Boolean,
    val danmuApi: String,
    val defaultLoadLive: Boolean,
    val historyNumIndex: Int,
    val historyMerge: Boolean,
    val searchThreads: Int,
    val longPressSpeed: Int,
    val bufferTimes: Int,
    val preloadNextEpisode: Boolean,
    val preloadDuration: Int,
    val playCache: Boolean,
    val exoCacheSizeMb: Int,
    val dohIndex: Int,
    val cacheSizeText: String = "",
)

class SettingsViewModel : ViewModel() {
    private var cacheSizeText: String = ""

    private val _state = mutableStateOf(loadState())

    init {
        refresh()
    }

    val state: androidx.compose.runtime.State<SettingsState> = _state

    fun refresh() {
        _state.value = loadState()
        refreshCacheSize()
    }

    /**
     * 只重读 KV 状态,不重算缓存大小。
     *
     * <p>{@code getCacheSize()} 是整棵缓存目录的递归遍历,绑到"每次配置变化"上会白跑;
     * 各行的值(播放内核/默认启动页/历史条数/弹幕 API 等)在别的二级页也能改,回本页时重读一次即可。
     */
    fun refreshState() {
        _state.value = loadState()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { FileUtils.formatCacheSize(FileUtils.getCacheSize()) }
            applyCacheSizeText(text)
        }
    }

    fun clearCache(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                FileUtils.clearCache()
                FileUtils.formatCacheSize(FileUtils.getCacheSize())
            }
            applyCacheSizeText(text)
            onCleared()
        }
    }

    private fun applyCacheSizeText(text: String) {
        if (text != cacheSizeText) {
            cacheSizeText = text
            _state.value = _state.value.copy(cacheSizeText = text)
        }
    }

    private fun loadState(): SettingsState = SettingsState(
        playType = KV.get(HawkConfig.PLAY_TYPE, 2),
        playRender = KV.get(HawkConfig.PLAY_RENDER, 1),
        playScale = KV.get(HawkConfig.PLAY_SCALE, 0),
        ijkCodec = KV.get(HawkConfig.IJK_CODEC, "硬解码"), // i18n: keep
        exoDecode = KV.get(HawkConfig.EXO_DECODE, "硬解码"), // i18n: keep
        ijkCachePlay = KV.get(HawkConfig.IJK_CACHE_PLAY, false),
        playTunnel = KV.get(HawkConfig.PLAY_TUNNEL, false),
        preferAac = KV.get(HawkConfig.PLAY_PREFER_AAC, false),
        musicPlayerPage = MusicSettings.autoOpenPage(),
        autoSwitchLine = KV.get(HawkConfig.AUTO_SWITCH_LINE, false),
        m3u8Purify = KV.get(HawkConfig.M3U8_PURIFY, false),
        incognito = KV.get(HawkConfig.INCOGNITO, false),
        gestureControlDisabled = KV.get(HawkConfig.GESTURE_CONTROL_DISABLED, false),
        navAnimationDisabled = KV.get(HawkConfig.NAV_ANIMATION_DISABLED, false),
        danmuOpen = KV.get(HawkConfig.DANMU_OPEN, true),
        danmuApi = KV.get(HawkConfig.DANMU_API, ""),
        defaultLoadLive = KV.get(HawkConfig.DEFAULT_LOAD_LIVE, false),
        historyNumIndex = KV.get(HawkConfig.HISTORY_NUM, 0),
        historyMerge = HistoryMerge.isEnabled(),
        searchThreads = KV.get(HawkConfig.SEARCH_THREADS, HawkConfig.SEARCH_THREADS_DEFAULT),
        longPressSpeed = KV.get(HawkConfig.LONG_PRESS_SPEED, HawkConfig.LONG_PRESS_SPEED_DEFAULT),
        bufferTimes = KV.get(HawkConfig.BUFFER_TIMES, HawkConfig.BUFFER_TIMES_DEFAULT),
        preloadNextEpisode = KV.get(HawkConfig.PRELOAD_NEXT_EPISODE, false),
        preloadDuration = KV.get(HawkConfig.PRELOAD_DURATION, HawkConfig.PRELOAD_DURATION_DEFAULT),
        playCache = KV.get(HawkConfig.PLAY_CACHE, false),
        exoCacheSizeMb = KV.get(HawkConfig.EXO_CACHE_SIZE_MB, HawkConfig.EXO_CACHE_SIZE_MB_DEFAULT),
        dohIndex = KV.get(HawkConfig.DOH_URL, 0),
        cacheSizeText = cacheSizeText,
    )

    fun <T> put(key: String, value: T) {
        KV.put(key, value)
        refresh()
    }
}

@Composable
fun SettingsPage(
    vm: SettingsViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    // 页面保持全出血(背景延伸到导航栏之下,玻璃才有内容可取),只把内容让开
    val navStart = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    val navBottom = contentPadding.calculateBottomPadding()
    val state by vm.state
    // 各行的值都来自 KV,而 loadState() 只在 ViewModel 构造时读一次;播放设置/偏好设置/预载设置等
    // 二级页也能改同一批 KV,退回本页时若不重读就会显示旧值 —— 故回本页(宿主 Activity 的 ON_RESUME)
    // 重读一次。缓存大小走独立那条:它是整棵缓存目录的递归遍历,不该跟着每次状态重读一起跑。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshState()
        vm.refreshCacheSize()
    }
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (ignored: Exception) {
            LOG.d("SettingsPage", "read versionName failed")
            ""
        }
    }
    var aboutSheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var updateStage by remember { mutableStateOf<UpdateStage>(UpdateStage.Idle) }
    var updateProgress by remember { mutableStateOf(0) }
    var pendingApk by remember { mutableStateOf<File?>(null) }

    // 从"允许安装未知来源应用"授权页返回后,若已获权限则自动接着安装
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val apk = pendingApk
        if (apk != null && AppUpdater.canInstall(context)) {
            pendingApk = null
            AppUpdater.installApk(context, apk)
        }
    }

    // 检测更新:远端清单 versionCode > 本地才算有新版本
    val runUpdateCheck: () -> Unit = {
        if (updateStage !is UpdateStage.Checking && updateStage !is UpdateStage.Downloading) {
            updateStage = UpdateStage.Checking
            scope.launch {
                val latest = withContext(Dispatchers.IO) { AppUpdater.fetchLatest() }
                updateStage = when {
                    latest == null -> UpdateStage.CheckFailed
                    latest.versionCode <= AppUpdater.currentVersionCode(context) -> UpdateStage.Latest
                    else -> UpdateStage.Available(latest)
                }
            }
        }
    }

    // 下载 APK 并调起安装;首次安装会被系统拦到"未知来源"授权页
    val startUpdateDownload: (UpdateInfo) -> Unit = { info ->
        updateStage = UpdateStage.Downloading(info)
        updateProgress = 0
        scope.launch {
            val apk = withContext(Dispatchers.IO) {
                AppUpdater.download(context, info.url) { percent -> updateProgress = percent }
            }
            if (apk == null) {
                updateStage = UpdateStage.DownloadFailed
            } else {
                updateStage = UpdateStage.Idle
                if (AppUpdater.canInstall(context)) {
                    AppUpdater.installApk(context, apk)
                    Toast.makeText(context, R.string.update_install_start, Toast.LENGTH_SHORT).show()
                } else {
                    pendingApk = apk
                    AppUpdater.openInstallPermission(context)
                    Toast.makeText(context, R.string.update_install_permission, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val listState = rememberScrollState()

    AppTopBarScaffold(
        topBarStartInset = navStart,
        titleContent = {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) { topPad, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listState)
                .padding(horizontal = 16.dp)
                .padding(start = navStart, bottom = 8.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(Modifier.height(topPad - 20.dp))

            AppInfoHeaderCard(versionName)

            SettingsGroup(title = null) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsRow(
                        title = stringResource(R.string.settings_config_manage),
                        subtitle = stringResource(R.string.settings_config_manage_subtitle),
                        iconRes = R.drawable.ic_settings_api,
                        onClick = { ConfigManageActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.settings_theme),
                        subtitle = stringResource(R.string.settings_theme_subtitle),
                        iconRes = R.drawable.ic_settings_theme,
                        onClick = { ThemeSettingsActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.settings_play),
                        subtitle = stringResource(R.string.settings_play_subtitle),
                        iconRes = R.drawable.ic_settings_play,
                        onClick = { PlaySettingsActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.danmu_settings),
                        subtitle = stringResource(R.string.settings_danmu_subtitle),
                        iconRes = R.drawable.ic_settings_danmu,
                        onClick = { DanmuSettingsActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.settings_preference_title),
                        subtitle = stringResource(R.string.settings_preference_subtitle),
                        iconRes = R.drawable.ic_settings_preference,
                        onClick = { PreferenceSettingsActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsRow(
                        title = stringResource(R.string.settings_preload),
                        subtitle = stringResource(R.string.settings_preload_subtitle),
                        iconRes = R.drawable.ic_settings_preload,
                        onClick = { PreloadSettingsActivity.start(context) },
                    )
                }
            }

            SettingsGroup(title = null) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.settings_default_page),
                        subtitle = stringResource(R.string.settings_default_page_subtitle),
                        iconRes = R.drawable.ic_settings_start,
                        valueText = stringResource(if (state.defaultLoadLive) R.string.common_live else R.string.common_vod),
                        options = listOf(
                            stringResource(R.string.common_vod),
                            stringResource(R.string.common_live),
                        ),
                        selectedIndex = if (state.defaultLoadLive) 1 else 0,
                        onSelect = { idx -> vm.put(HawkConfig.DEFAULT_LOAD_LIVE, idx == 1) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.settings_history_limit),
                        subtitle = stringResource(R.string.settings_history_limit_subtitle),
                        iconRes = R.drawable.ic_settings_history,
                        valueText = stringResource(
                            R.string.settings_history_limit_value,
                            HistoryHelper.getHisNum(state.historyNumIndex),
                        ),
                        options = listOf(0, 1, 2).map {
                            stringResource(R.string.settings_history_limit_value, HistoryHelper.getHisNum(it))
                        },
                        selectedIndex = state.historyNumIndex,
                        onSelect = { idx -> vm.put(HawkConfig.HISTORY_NUM, idx) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.settings_clear_cache),
                        subtitle = stringResource(R.string.settings_clear_cache_subtitle),
                        iconRes = R.drawable.ic_delete,
                        valueText = state.cacheSizeText,
                        onClick = {
                            vm.clearCache {
                                Toast.makeText(context, context.getString(R.string.toast_cache_cleared), Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsOptionMenuRow(
                        title = "DOH",
                        subtitle = stringResource(R.string.settings_doh_subtitle),
                        iconRes = R.drawable.ic_settings_doh,
                        valueText = if (state.dohIndex == 0) stringResource(R.string.common_off)
                        else OkGoHelper.dnsHttpsList.getOrNull(state.dohIndex)
                            ?: stringResource(R.string.common_off),
                        options = OkGoHelper.dnsHttpsList.mapIndexed { index, name ->
                            if (index == 0) context.getString(R.string.common_off) else name
                        },
                        selectedIndex = state.dohIndex,
                        onSelect = { idx -> vm.put(HawkConfig.DOH_URL, idx) },
                    )
                }
            }

            SettingsGroup(title = null) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_subtitle),
                        iconRes = R.drawable.ic_settings_about,
                        onClick = { aboutSheet = true },
                    )
                }
            
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsRow(
                        title = stringResource(R.string.settings_check_update),
                        subtitle = if (updateStage is UpdateStage.Checking) {
                            stringResource(R.string.update_checking)
                        } else {
                            stringResource(R.string.settings_check_update_subtitle)
                        },
                        iconRes = R.drawable.ic_settings_update,
                        onClick = runUpdateCheck,
                    )
                }
            }
        }
    }

    if (aboutSheet) {
        AboutSheet(versionName = versionName, onDismiss = { aboutSheet = false })
    }

    when (val stage = updateStage) {
        is UpdateStage.Checking -> AVBoxAlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = { Text(stringResource(R.string.update_checking)) },
            confirmButton = {},
            dismissible = false,
        )

        is UpdateStage.Latest -> AVBoxAlertDialog(
            onDismissRequest = { updateStage = UpdateStage.Idle },
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = { Text(stringResource(R.string.update_latest)) },
            confirmButton = {
                TextButton(
                    onClick = { updateStage = UpdateStage.Idle },
                    modifier = Modifier.tvControlFocus(cornerRadius = 20.dp),
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )

        is UpdateStage.Available -> AVBoxAlertDialog(
            onDismissRequest = { updateStage = UpdateStage.Idle },
            title = { Text(stringResource(R.string.update_available_title, stage.info.versionName)) },
            text = {
                Text(
                    if (stage.info.notes.isNotEmpty()) {
                        stage.info.notes
                    } else {
                        stringResource(R.string.update_available_notes)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { startUpdateDownload(stage.info) },
                    modifier = Modifier.tvControlFocus(cornerRadius = 20.dp),
                ) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { updateStage = UpdateStage.Idle },
                    modifier = Modifier.tvControlFocus(cornerRadius = 20.dp),
                ) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )

        is UpdateStage.Downloading -> AVBoxAlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading, stage.info.versionName)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { updateProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$updateProgress%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissible = false,
        )

        is UpdateStage.CheckFailed -> AVBoxAlertDialog(
            onDismissRequest = { updateStage = UpdateStage.Idle },
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = { Text(stringResource(R.string.update_check_failed)) },
            confirmButton = {
                TextButton(
                    onClick = { updateStage = UpdateStage.Idle },
                    modifier = Modifier.tvControlFocus(cornerRadius = 20.dp),
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )

        is UpdateStage.DownloadFailed -> AVBoxAlertDialog(
            onDismissRequest = { updateStage = UpdateStage.Idle },
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = { Text(stringResource(R.string.update_download_failed)) },
            confirmButton = {
                TextButton(
                    onClick = { updateStage = UpdateStage.Idle },
                    modifier = Modifier.tvControlFocus(cornerRadius = 20.dp),
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )

        else -> Unit
    }

}

@Composable
private fun AppInfoHeaderCard(versionName: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(scheme.primaryContainer, scheme.tertiaryContainer),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "归零box",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.settings_app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp),
                )
                Surface(
                    shape = CircleShape,
                    color = scheme.onPrimaryContainer,
                    contentColor = scheme.primaryContainer,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(
                        text = if (versionName.isEmpty()) "v-.-.-" else "v$versionName",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            Image(
                painter = painterResource(R.drawable.icon_lingqi),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(88.dp),
            )
        }
    }
}

@Composable
private fun AboutSheet(versionName: String, onDismiss: () -> Unit) {
    AVBoxBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.settings_about)) {
        val dismissAnimated = LocalSheetDismiss.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            if (versionName.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_about_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.settings_about_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            // 关于弹层原本只有纯文字、没有任何可聚焦控件:TV 上弹层打开后焦点无处可去,
            // 方向键仍在底层页面乱跑。补一个关闭按钮,既给遥控器一个落点,也方便触摸关闭。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { dismissAnimated() },
                    modifier = Modifier.tvControlFocus(cornerRadius = 20.dp),
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
        }
    }
}

/** 检测更新的界面状态机 */
private sealed interface UpdateStage {
    /** 空闲 */
    object Idle : UpdateStage

    /** 正在请求远端版本清单 */
    object Checking : UpdateStage

    /** 已是最新版本 */
    object Latest : UpdateStage

    /** 有新版本可下载 */
    data class Available(val info: UpdateInfo) : UpdateStage

    /** 下载中(进度见 updateProgress) */
    data class Downloading(val info: UpdateInfo) : UpdateStage

    /** 检测失败(网络/清单异常) */
    object CheckFailed : UpdateStage

    /** 下载失败 */
    object DownloadFailed : UpdateStage
}

@Composable
fun TextEditDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    AVBoxAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.dialog_api_url_hint)) },
            )
        },
        confirmButton = {
            val dismissThen = LocalSheetDismissThen.current
            TextButton(
                onClick = { dismissThen { onConfirm(text.trim()) } },
                modifier = Modifier.tvControlFocus(cornerRadius = 20.dp),
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
    )
}
