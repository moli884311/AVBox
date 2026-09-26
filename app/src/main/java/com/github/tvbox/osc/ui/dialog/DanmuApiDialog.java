package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmuSourceManager;

import org.jetbrains.annotations.NotNull;

public class DanmuApiDialog extends BaseDialog {
    private EditText input;
    private OnListener listener;

    public DanmuApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_danmu_api);
        setCanceledOnTouchOutside(false);
        input = findViewById(R.id.input);
        input.setText(DanmuSourceManager.getSelectedUrl());
        input.setHint("请输入弹幕搜索地址");
        findViewById(R.id.inputDefault).setOnClickListener(v -> saveDefault());
        findViewById(R.id.inputSubmit).setOnClickListener(v -> save(input.getText().toString().trim()));
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    save(input.getText().toString().trim());
                    return true;
                }
                return false;
            }
        });
    }

    private void save(String api) {
        if (TextUtils.isEmpty(api)) {
            saveDefault();
            return;
        }
        DanmuSourceManager.selectCustom(api);
        if (listener != null) listener.onChange(api);
        dismiss();
    }

    private void saveDefault() {
        DanmuSourceManager.useAuto();
        if (listener != null) listener.onChange("");
        dismiss();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public interface OnListener {
        void onChange(String api);
    }
}
