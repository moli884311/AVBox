package com.github.tvbox.osc.util

import com.github.tvbox.osc.base.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object HomeSettings {

    enum class HomeLayout { Horizontal, Vertical }

    // 未登记 KVKeySpec:读取必须带默认值(靠默认值携带 String 类型),KV.get(key) 不带默认值会解不出
    private const val KEY_LAYOUT = "home_layout"

    private const val VALUE_LAYOUT_HORIZONTAL = "horizontal"

    private const val VALUE_LAYOUT_VERTICAL = "vertical"

    /** 未设置过的哨兵:用它区分"没选过"(跟随设备默认)与"显式选了横版" */
    private const val VALUE_LAYOUT_UNSET = ""

    private val mutableLayout = MutableStateFlow(current())

    val layoutFlow: StateFlow<HomeLayout> = mutableLayout

    fun current(): HomeLayout = when (KV.get(KEY_LAYOUT, VALUE_LAYOUT_UNSET)) {
        VALUE_LAYOUT_HORIZONTAL -> HomeLayout.Horizontal
        VALUE_LAYOUT_VERTICAL -> HomeLayout.Vertical
        // 没设置过:TV 默认用栅格(竖版)。横版首屏是一张占满屏的 Hero 卡片,
        // 电视上表现为"整个界面就 1 张卡片、看不到别的",用户明确要求默认铺满卡片的栅格布局。
        else -> if (defaultIsGrid()) HomeLayout.Vertical else HomeLayout.Horizontal
    }

    fun setLayout(layout: HomeLayout) {
        KV.put(
            KEY_LAYOUT,
            if (layout == HomeLayout.Vertical) VALUE_LAYOUT_VERTICAL else VALUE_LAYOUT_HORIZONTAL,
        )
        mutableLayout.value = layout
    }

    /** 设备默认是否用栅格:用 Application 上下文判 TV,判定异常按手机处理 */
    private fun defaultIsGrid(): Boolean = try {
        val app = App.getInstance()
        app != null && ScreenUtils.isTv(app)
    } catch (t: Throwable) {
        false
    }
}
