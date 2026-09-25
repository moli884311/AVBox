package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.OkGoHelper;
import com.orhanobut.hawk.Hawk;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SettingActivity extends BaseActivity {
    private Handler mHandler = new Handler();
    private String homeSourceKey;
    private String currentApi;
    private int homeRec;
    private String currentLiveApi;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_setting;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    @Override
    protected void init() {
        currentApi = Hawk.get(HawkConfig.API_URL, "");
        homeSourceKey = ApiConfig.get().getHomeSourceBean().getKey();
        homeRec = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC);
        currentLiveApi = Hawk.get(HawkConfig.LIVE_API_URL, "");
        ((TextView) findViewById(R.id.tvVersion)).setText("v" + DefaultConfig.getAppVersionName(mContext));
        bindRow(R.id.rowConfig, R.drawable.ic_settings_api, "配置管理", "导入或删除订阅源", null, "config");
        bindRow(R.id.rowTheme, R.drawable.ic_settings_theme, "主题设置", "修改应用的配色和效果", null, "theme");
        bindRow(R.id.rowPlay, R.drawable.ic_settings_play, "播放设置", "播放内核和解码方式", null, "play");
        bindRow(R.id.rowDanmu, R.drawable.ic_settings_danmu, "弹幕设置", "弹幕开关、来源与外观", null, "danmu");
        bindRow(R.id.rowPrefer, R.drawable.ic_settings_preference, "偏好设置", "修改应用的使用偏好", null, "prefer");
        bindRow(R.id.rowPreload, R.drawable.ic_settings_preload, "预载设置", "播放视频时预加载", null, "preload");
        bindRow(R.id.rowDefaultPage, R.drawable.ic_settings_start, "默认启动页", "首次打开应用的所在位置", null, "start");
        bindRow(R.id.rowHistory, R.drawable.ic_settings_history, "历史记录上限", "最多保留多少条记录", null, "history");
        bindRow(R.id.rowCache, R.drawable.ic_delete, "清除缓存", "清理应用的使用缓存", null, "cache");
        bindRow(R.id.rowDoh, R.drawable.ic_settings_doh, "安全DNS", "DOH 加密域名解析", null, "doh");
        bindRow(R.id.rowAbout, R.drawable.ic_settings_about, "关于", "查看详细信息", null, "about");
        bindRow(R.id.rowBackup, R.drawable.ic_settings_api, "数据备份", "备份与恢复应用数据", null, "more");
        bindRow(R.id.rowDebug, R.drawable.ic_settings_preference, "调试模式", "开发者选项", null, "more");
        bindRow(R.id.rowUpdate, R.drawable.ic_settings_update, "检测更新", "检查是否有新版本", null, null);
        getClickableRow(R.id.rowUpdate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Toast.makeText(mContext, "当前版本 v" + DefaultConfig.getAppVersionName(mContext) + "，已是最新版本", Toast.LENGTH_SHORT).show();
            }
        });
        refreshRowValues();
    }

    private View getClickableRow(int rowId) {
        View row = findViewById(rowId);
        if (row instanceof android.view.ViewGroup && ((android.view.ViewGroup) row).getChildCount() > 0) {
            return ((android.view.ViewGroup) row).getChildAt(0);
        }
        return row;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRowValues();
    }

    private void bindRow(int rowId, int iconRes, String title, String sub, String value, final String category) {
        View row = findViewById(rowId);
        if (row == null) return;
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        TextView subView = row.findViewById(R.id.rowSub);
        if (sub != null && !sub.isEmpty()) {
            subView.setText(sub);
            subView.setVisibility(View.VISIBLE);
        } else {
            subView.setVisibility(View.GONE);
        }
        TextView valueView = row.findViewById(R.id.rowValue);
        valueView.setText(value == null ? "" : value);
        if (category != null) {
            final String titleStr = title;
            getClickableRow(rowId).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FastClickCheckUtil.check(v);
                    Bundle bundle = new Bundle();
                    bundle.putString("category", category);
                    bundle.putString("title", titleStr);
                    jumpActivity(SettingSectionActivity.class, bundle);
                }
            });
        }
    }

    private void setRowValue(int rowId, String value) {
        View row = findViewById(rowId);
        if (row == null) return;
        ((TextView) row.findViewById(R.id.rowValue)).setText(value == null ? "" : value);
    }

    private void refreshRowValues() {
        setRowValue(R.id.rowDefaultPage, Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false) ? "直播" : "点播");
        setRowValue(R.id.rowHistory, HistoryHelper.getHistoryNumName(Hawk.get(HawkConfig.HISTORY_NUM, 0)));
        setRowValue(R.id.rowDoh, OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        setRowValue(R.id.rowDebug, Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
    }

    private Runnable mDevModeRun = new Runnable() {
        @Override
        public void run() {
            devMode = "";
        }
    };

    public interface DevModeCallback {
        void onChange();
    }

    public static DevModeCallback callback = null;

    String devMode = "";

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mHandler.removeCallbacks(mDevModeRun);
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_0:
                    devMode += "0";
                    mHandler.postDelayed(mDevModeRun, 200);
                    if (devMode.length() >= 4) {
                        if (callback != null) {
                            callback.onChange();
                        }
                    }
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (currentApi.equals(Hawk.get(HawkConfig.API_URL, ""))) {
            if (homeRec != Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)) {
                jumpActivity(HomeActivity.class, createBundle());
            } else if (!currentLiveApi.equals(Hawk.get(HawkConfig.LIVE_API_URL, ""))) {
                jumpActivity(HomeActivity.class, createBundle());
            }
        } else {
            AppManager.getInstance().finishActivity(HomeActivity.class);
            jumpActivity(HomeActivity.class);
            finish();
            return;
        }
        super.onBackPressed();
    }

    private Bundle createBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        return bundle;
    }
}
