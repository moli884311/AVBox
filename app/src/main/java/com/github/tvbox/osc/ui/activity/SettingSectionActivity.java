package com.github.tvbox.osc.ui.activity;

import android.text.TextUtils;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.fragment.ModelSettingFragment;

/**
 * 设置分类二级页
 */
public class SettingSectionActivity extends BaseActivity {

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_setting_section;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    @Override
    protected void init() {
        String category = getIntent().getStringExtra("category");
        String title = getIntent().getStringExtra("title");
        if (TextUtils.isEmpty(category)) {
            finish();
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(TextUtils.isEmpty(title) ? "设置" : title);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.contentContainer, ModelSettingFragment.newInstance(category))
                .commitAllowingStateLoss();
    }
}
