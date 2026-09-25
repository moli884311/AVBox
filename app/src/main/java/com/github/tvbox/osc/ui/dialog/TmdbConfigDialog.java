package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.TmdbHelper;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

public class TmdbConfigDialog extends BaseDialog {
    private EditText inputToken;
    private EditText inputApiKey;
    private OnListener listener;

    public TmdbConfigDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_tmdb_config);
        setCanceledOnTouchOutside(false);
        inputToken = findViewById(R.id.inputToken);
        inputApiKey = findViewById(R.id.inputApiKey);
        inputToken.setText(Hawk.get(HawkConfig.TMDB_TOKEN, TmdbHelper.DEFAULT_TOKEN));
        inputApiKey.setText(Hawk.get(HawkConfig.TMDB_API_KEY, TmdbHelper.DEFAULT_API_KEY));
        findViewById(R.id.inputDefault).setOnClickListener(v -> saveDefault());
        findViewById(R.id.inputSubmit).setOnClickListener(v -> save());
        inputApiKey.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    save();
                    return true;
                }
                return false;
            }
        });
    }

    private void save() {
        String token = inputToken.getText().toString().trim();
        String apiKey = inputApiKey.getText().toString().trim();
        Hawk.put(HawkConfig.TMDB_TOKEN, token.isEmpty() ? TmdbHelper.DEFAULT_TOKEN : token);
        Hawk.put(HawkConfig.TMDB_API_KEY, apiKey.isEmpty() ? TmdbHelper.DEFAULT_API_KEY : apiKey);
        if (listener != null) listener.onChange(token);
        dismiss();
    }

    private void saveDefault() {
        Hawk.put(HawkConfig.TMDB_TOKEN, TmdbHelper.DEFAULT_TOKEN);
        Hawk.put(HawkConfig.TMDB_API_KEY, TmdbHelper.DEFAULT_API_KEY);
        if (listener != null) listener.onChange(TmdbHelper.DEFAULT_TOKEN);
        dismiss();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public interface OnListener {
        void onChange(String token);
    }
}
