package com.github.tvbox.osc.ui.activity

import android.content.Context
import android.content.Intent
import com.github.tvbox.osc.ui.theme.enableTransparentEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.ui.components.SheetHostScaffold
import com.github.tvbox.osc.ui.page.DanmuApiSettingsScreen
import com.github.tvbox.osc.ui.theme.AVBoxTheme

class DanmuApiActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DanmuApiActivity::class.java))
        }
    }

    override fun getLayoutResID(): Int = R.layout.activity_main

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                // 「添加弹幕源」输入框走窗口根槽位
                SheetHostScaffold {
                    DanmuApiSettingsScreen(onNavigateBack = { finish() })
                }
            }
        }
    }
}
