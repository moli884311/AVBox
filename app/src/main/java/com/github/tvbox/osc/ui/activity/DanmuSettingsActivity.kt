package com.github.tvbox.osc.ui.activity

import android.content.Context
import android.content.Intent
import com.github.tvbox.osc.ui.theme.enableTransparentEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.ui.components.SheetHostScaffold
import com.github.tvbox.osc.ui.page.DanmuSettingsScreen
import com.github.tvbox.osc.ui.theme.AVBoxTheme

class DanmuSettingsActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DanmuSettingsActivity::class.java))
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
                // 弹幕 API 输入框走窗口根槽位,同偏好设置页
                SheetHostScaffold {
                    DanmuSettingsScreen(onNavigateBack = { finish() })
                }
            }
        }
    }
}
