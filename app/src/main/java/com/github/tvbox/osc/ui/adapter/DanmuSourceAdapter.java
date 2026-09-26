package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmuSourceManager;
import com.github.tvbox.osc.bean.DanmuSource;

import java.util.ArrayList;

public class DanmuSourceAdapter extends BaseQuickAdapter<DanmuSource, BaseViewHolder> {

    public DanmuSourceAdapter() {
        super(R.layout.item_danmu_source, new ArrayList<DanmuSource>());
    }

    @Override
    protected void convert(BaseViewHolder helper, DanmuSource item) {
        helper.setText(R.id.danmuSourceName, item.getName());
        View itemView = helper.getView(R.id.danmuSourceItem);
        TextView urlView = helper.getView(R.id.danmuSourceUrl);
        TextView statusView = helper.getView(R.id.danmuSourceStatus);
        switch (item.kind) {
            case DanmuSource.KIND_AUTO:
                urlView.setText("自动选择可用接口，失败时自动回退");
                break;
            case DanmuSource.KIND_CUSTOM:
                urlView.setText(TextUtils.isEmpty(item.getUrl()) ? "手动输入弹幕搜索地址" : item.getUrl());
                break;
            default:
                urlView.setText(item.getUrl());
                break;
        }
        updateStatus(item, statusView);
        itemView.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && (keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                view.performClick();
                return true;
            }
            return false;
        });
    }

    private void updateStatus(DanmuSource item, TextView statusView) {
        if (isSelected(item)) {
            statusView.setText("使用中");
            statusView.setTextColor(ContextCompat.getColor(mContext, R.color.danmu_status_selected));
            return;
        }
        if (item.kind == DanmuSource.KIND_AUTO) {
            statusView.setText("推荐");
            statusView.setTextColor(ContextCompat.getColor(mContext, R.color.danmu_status_idle));
            return;
        }
        if (item.kind == DanmuSource.KIND_CUSTOM) {
            statusView.setText("");
            return;
        }
        int latency = DanmuSourceManager.getLatency(item.getUrl());
        if (latency == Integer.MIN_VALUE) {
            statusView.setText("未测速");
            statusView.setTextColor(ContextCompat.getColor(mContext, R.color.danmu_status_idle));
        } else if (latency < 0) {
            statusView.setText("不可用");
            statusView.setTextColor(ContextCompat.getColor(mContext, R.color.danmu_status_bad));
        } else {
            statusView.setText(latency + "ms");
            statusView.setTextColor(ContextCompat.getColor(mContext, R.color.danmu_status_ok));
        }
    }

    public boolean isSelected(DanmuSource item) {
        int mode = DanmuSourceManager.getMode();
        if (item.kind == DanmuSource.KIND_AUTO) return mode == DanmuSourceManager.MODE_AUTO;
        if (item.kind == DanmuSource.KIND_CUSTOM) return mode == DanmuSourceManager.MODE_CUSTOM;
        if (mode != DanmuSourceManager.MODE_SOURCE) return false;
        return item.getUrl().equals(DanmuSourceManager.getSelectedUrl());
    }
}
