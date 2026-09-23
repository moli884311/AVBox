package com.github.tvbox.osc.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.tvbox.osc.R
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.activity.DanmuApiActivity
import com.github.tvbox.osc.ui.components.AppTopBarScaffold
import com.github.tvbox.osc.ui.components.SettingsCard
import com.github.tvbox.osc.ui.components.SettingsCardPosition
import com.github.tvbox.osc.ui.components.SettingsGroup
import com.github.tvbox.osc.ui.components.SettingsOptionMenuRow
import com.github.tvbox.osc.ui.components.SettingsRow
import com.github.tvbox.osc.ui.components.SettingsSwitchRow
import com.github.tvbox.osc.ui.components.TopBarActionBox
import com.github.tvbox.osc.util.DanmuHelper
import com.github.tvbox.osc.util.DanmuSourceStore
import org.greenrobot.eventbus.EventBus

// 与播放器内弹幕面板共用同一批取值:顺序不能翻
private val DANMU_SPEEDS = listOf(2.4f, 1.8f, 1.5f, 1.0f)
private val DANMU_SIZES = (6..20).toList()
private val DANMU_LINES = (1..15).toList()
private val DANMU_ALPHAS = (1..10).map { it * 10 }

@Composable
fun DanmuSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberScrollState()
    // 与播放器内面板一致:仅颜色行需要重建弹幕轨,其余只应用样式
    val postSettings: (Boolean) -> Unit = { forColor ->
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, forColor))
    }
    // 来源开关变更后通知在播的播放器重新选源
    val reselect: () -> Unit = {
        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_DANMU_RESELECT))
    }

    var danmuOpen by remember { mutableStateOf(DanmuHelper.isOpen()) }
    var online by remember { mutableStateOf(DanmuHelper.isOnlineEnabled()) }
    var platform by remember { mutableStateOf(DanmuHelper.isPlatformEnabled()) }
    var apiItems by remember { mutableStateOf<List<DanmuSourceStore.Item>>(DanmuSourceStore.load()) }
    // 从「弹幕 API」页返回时重读源列表,否则数值停留在进入前的快照
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { apiItems = DanmuSourceStore.load() }
    var colorIdx by remember { mutableIntStateOf(if (DanmuHelper.useRandomColor()) 1 else 0) }
    var speedIdx by remember {
        mutableIntStateOf(DANMU_SPEEDS.indexOf(DanmuHelper.getSpeed()).coerceAtLeast(0))
    }
    var sizeIdx by remember {
        mutableIntStateOf((Math.round(DanmuHelper.getSizeScale() * 10) - DANMU_SIZES.first()).coerceIn(0, DANMU_SIZES.lastIndex))
    }
    var lineIdx by remember {
        mutableIntStateOf((DanmuHelper.getMaxLine() - DANMU_LINES.first()).coerceIn(0, DANMU_LINES.lastIndex))
    }
    var alphaIdx by remember {
        mutableIntStateOf((Math.round(DanmuHelper.getAlpha() * 100) / 10 - 1).coerceIn(0, DANMU_ALPHAS.lastIndex))
    }

    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.danmu_settings),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(R.drawable.ic_arrow_left, stringResource(R.string.common_back), onClick = onNavigateBack)
        },
    ) { topPad, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            Spacer(Modifier.height(topPad + 8.dp))

            SettingsGroup(title = null) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_danmu_switch),
                        checked = danmuOpen,
                        onCheckedChange = {
                            danmuOpen = it
                            DanmuHelper.setOpen(it)
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.settings_danmu_api),
                        valueText = apiItems.firstOrNull { it.enabled }?.name
                            ?: stringResource(R.string.common_not_set),
                        onClick = { DanmuApiActivity.start(context) },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.danmu_online),
                        subtitle = stringResource(R.string.danmu_online_subtitle),
                        checked = online,
                        onCheckedChange = {
                            online = it
                            DanmuHelper.setOnlineEnabled(it)
                            reselect()
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.danmu_platform),
                        subtitle = stringResource(R.string.danmu_platform_subtitle),
                        checked = platform,
                        onCheckedChange = {
                            platform = it
                            DanmuHelper.setPlatformEnabled(it)
                            reselect()
                        },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            SettingsGroup(title = null) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.danmu_color),
                        options = listOf(
                            stringResource(R.string.common_default),
                            stringResource(R.string.danmu_color_random),
                        ),
                        selectedIndex = colorIdx,
                        valueText = listOf(
                            stringResource(R.string.common_default),
                            stringResource(R.string.danmu_color_random),
                        )[colorIdx],
                        onSelect = { idx ->
                            colorIdx = idx
                            DanmuHelper.setRandomColor(idx == 1)
                            postSettings(true)
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.danmu_speed),
                        options = listOf(
                            stringResource(R.string.danmu_speed_very_slow),
                            stringResource(R.string.danmu_speed_slow),
                            stringResource(R.string.danmu_speed_normal),
                            stringResource(R.string.danmu_speed_fast),
                        ),
                        selectedIndex = speedIdx,
                        onSelect = { idx ->
                            speedIdx = idx
                            DanmuHelper.setSpeed(DANMU_SPEEDS[idx])
                            postSettings(false)
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.danmu_size),
                        options = DANMU_SIZES.map { stringResource(R.string.danmu_size_value, it) },
                        selectedIndex = sizeIdx,
                        valueText = stringResource(R.string.danmu_size_value, DANMU_SIZES[sizeIdx]),
                        onSelect = { idx ->
                            sizeIdx = idx
                            DanmuHelper.setSizeScale(DANMU_SIZES[idx] / 10f)
                            postSettings(false)
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.danmu_lines),
                        options = DANMU_LINES.map { stringResource(R.string.danmu_lines_value, it) },
                        selectedIndex = lineIdx,
                        valueText = stringResource(R.string.danmu_lines_value, DANMU_LINES[lineIdx]),
                        onSelect = { idx ->
                            lineIdx = idx
                            DanmuHelper.setMaxLine(DANMU_LINES[idx])
                            postSettings(false)
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsOptionMenuRow(
                        title = stringResource(R.string.danmu_alpha),
                        options = DANMU_ALPHAS.map { "$it%" },
                        selectedIndex = alphaIdx,
                        valueText = "${DANMU_ALPHAS[alphaIdx]}%",
                        onSelect = { idx ->
                            alphaIdx = idx
                            DanmuHelper.setAlpha(DANMU_ALPHAS[idx] / 100f)
                            postSettings(false)
                        },
                    )
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}
