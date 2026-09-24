package com.github.tvbox.osc.ui.tv

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.util.ScreenUtils

/**
 * 当前设备是否处于 Android TV 模式。
 *
 * 判据与 [com.github.tvbox.osc.util.ScreenUtils.isTv] 完全一致(直接委托,不再各写一份):
 * 先认 `UiModeManager.getCurrentModeType() == TELEVISION`,非认证盒子会把 UI_MODE 报成 NORMAL,
 * 再兜底认 `android.software.leanback` 特性。在 [com.github.tvbox.osc.ui.theme.AVBoxTheme] 里统一下发。
 *
 * ⚠️ 曾经这里只认 UI_MODE,导致"横屏(BASE 层判 TV)"与"Compose 层判非 TV"不一致:
 * 焦点描边/按键路由/overscan 整套 TV 适配在非认证盒子上全部静默失效。
 */
val LocalIsTelevision = staticCompositionLocalOf { false }

fun isTelevision(context: Context): Boolean = ScreenUtils.isTv(context)

@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { isTelevision(context) }
}

/** 遥控聚焦时卡片的放大系数(仅 TV 生效)。 */
const val TV_FOCUSED_SCALE = 1.06f

/** 触摸按压时的缩小系数(手机原有手感,保持不变)。 */
const val TV_PRESSED_SCALE = 0.97f

/** TV 屏边 overscan 安全边距(dp):电视普遍裁掉边缘约 5%,关键内容需留白。 */
const val TV_OVERSCAN_DP = 24f

/**
 * 统一的"聚焦/按压"缩放动画。
 * TV 上聚焦放大、按压回缩;手机上只保留原有的按压回缩。
 */
@Composable
fun rememberTvFocusScale(
    focused: Boolean,
    pressed: Boolean,
    isTelevision: Boolean,
    focusedScale: Float = TV_FOCUSED_SCALE,
): Float = animateFloatAsState(
    targetValue = when {
        isTelevision && focused -> focusedScale
        pressed -> TV_PRESSED_SCALE
        else -> 1f
    },
    animationSpec = spring(stiffness = Spring.StiffnessMedium),
    label = "tvFocusScale",
).value

/**
 * 在控件自身边界内画一圈聚焦描边(圆角矩形)。
 *
 * 描边整体画在布局边界内侧,因此配合外层 `clip(shape)` 时不会被裁掉半条边,
 * 同时圆角与卡片一致,观感是"玻璃壳外沿亮起来"。
 */
fun Modifier.tvFocusBorder(
    focused: Boolean,
    cornerRadius: Dp,
    color: Color,
    width: Dp = 3.dp,
): Modifier {
    if (!focused) return this
    return drawWithContent {
        drawContent()
        val stroke = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
            style = Stroke(width = stroke),
        )
    }
}

/**
 * 给现有可点击控件(clickable/combinedClickable)叠加 TV 聚焦视觉:
 * 聚焦放大 + primary 色描边。手机上直接原样返回,零影响。
 *
 * 调用方必须把 `clickable` 用的同一个 [InteractionSource] 传进来,否则读不到聚焦态。
 */
@Composable
fun Modifier.tvFocusableCard(
    interactionSource: InteractionSource,
    cornerRadius: Dp = 16.dp,
    isTelevision: Boolean = LocalIsTelevision.current,
    focusedScale: Float = TV_FOCUSED_SCALE,
    borderColor: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    if (!isTelevision) return this
    val focused by interactionSource.collectIsFocusedAsState()
    val scale = rememberTvFocusScale(focused = focused, pressed = false, isTelevision = true, focusedScale = focusedScale)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .tvFocusBorder(focused = focused, cornerRadius = cornerRadius, color = borderColor)
}

/**
 * 等价于 [clickable],但额外叠加 [tvFocusableCard] 的 TV 聚焦视觉。
 *
 * 保留与默认 `clickable` 完全一致的触摸反馈([LocalIndication]),手机上零行为差异;
 * TV 上则由 [interactionSource] 驱动"放大 + 描边"。用于把散落的裸 `clickable`
 * 统一接入遥控焦点体系,免去每处手写 indication/interactionSource 样板。
 */
@Composable
fun Modifier.tvClickable(
    interactionSource: MutableInteractionSource,
    cornerRadius: Dp,
    enabled: Boolean = true,
    focusedScale: Float = 1f,
    onClick: () -> Unit,
): Modifier = this
    .clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        onClick = onClick,
    )
    .tvFocusableCard(interactionSource, cornerRadius, focusedScale = focusedScale)

/** [clickable] 的长按版本,语义同 [tvClickable]。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvCombinedClickable(
    interactionSource: MutableInteractionSource,
    cornerRadius: Dp,
    enabled: Boolean = true,
    focusedScale: Float = 1f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = this
    .combinedClickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick,
    )
    .tvFocusableCard(interactionSource, cornerRadius, focusedScale = focusedScale)

/**
 * 进入页面后把焦点落到 [focusRequester] 上(等第一帧测量完成再请求,避免 NoFocusTarget)。
 * TV 上用于建立"初始焦点"——否则遥控方向键会先触发一次不可预期的焦点搜索。
 */
@Composable
fun Modifier.tvInitialFocus(
    focusRequester: FocusRequester,
    enabled: Boolean = LocalIsTelevision.current,
): Modifier {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }
    return this
}
