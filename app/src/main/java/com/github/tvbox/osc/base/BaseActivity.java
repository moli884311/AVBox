package com.github.tvbox.osc.base;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.PermissionChecker;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.WindowSize;
import com.github.tvbox.osc.ui.tv.TvInputMode;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.LanguageManager;
import com.github.tvbox.osc.util.ScreenUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.AutoSizeCompat;
import me.jessyan.autosize.internal.CustomAdapt;
import xyz.doikki.videoplayer.util.CutoutUtil;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public abstract class BaseActivity extends AppCompatActivity implements CustomAdapt {
    protected Context mContext;

    private static float screenRatio = -100.0f;
    private int orientationPolicy = Integer.MIN_VALUE;
    private final Runnable refreshAutoSizeRunnable = new Runnable() {
        @Override
        public void run() {
            if (shouldRefreshAutoSize()) {
                refreshAutoSize();
            }
        }
    };
    private final Runnable hideSysBarRunnable = new Runnable() {
        @Override
        public void run() {
            hideSysBar();
        }
    };

    /** 语言资源包裹;必须早于 AppCompat 的 delegate 建基(它依赖包裹后的 base) */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.INSTANCE.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            if (screenRatio < 0) {
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                updateScreenRatio(dm);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        setContentView(getLayoutResID());
        mContext = this;
        initSystemUiListener();
        CutoutUtil.adaptCutoutAboveAndroidP(mContext, true);//设置刘海
        AppManager.getInstance().addActivity(this);
        applyOrientationPolicy();
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyOrientationPolicy();
        hideSysBar();
        if (shouldRefreshAutoSize()) {
            refreshAutoSize();
            scheduleRefreshAutoSize();
        }
    }

    /**
     * 进程级"用户确实在用遥控器"闩锁:方向键/确认键一旦出现就说明是遥控场景。
     * 部分盒子 {@link ScreenUtils#isTv} 的静态判据全部不成立(不报 TELEVISION/leanback、
     * 谎报触摸屏),此时靠这里动态补正,否则整套 TV 适配静默失效。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                TvInputMode.INSTANCE.onDpadKey();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }

    private void initSystemUiListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    int hiddenBars = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
                    if ((visibility & hiddenBars) != hiddenBars) {
                        decorView.removeCallbacks(hideSysBarRunnable);
                        decorView.postDelayed(hideSysBarRunnable, 300);
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().getDecorView().removeCallbacks(refreshAutoSizeRunnable);
        getWindow().getDecorView().removeCallbacks(hideSysBarRunnable);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSysBar();
            if (shouldRefreshAutoSize()) {
                scheduleRefreshAutoSize();
            }
        }
    }

    protected boolean shouldRefreshAutoSize() {
        return false;
    }

    /**
     * 方向策略:sw<600dp 锁竖屏,>=600dp 放开 —— 与平台在 API 36+ 的忽略范围一致,
     * 故手机档行为不变,大屏交由用户旋转/折叠。
     */
    public void applyOrientationPolicy() {
        try {
            int desired = orientationPolicyValue();
            // 只在策略值本身变化时下发,否则会覆盖播放器「旋转」按钮刚设过的方向
            if (orientationPolicy == desired) {
                return;
            }
            orientationPolicy = desired;
            setRequestedOrientation(desired);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /** 当前窗口档下的策略值;播放器退出全屏时恢复到此值,而不是硬写竖屏 */
    public int orientationPolicyValue() {
        try {
            // TV 恒横屏:电视盒子常见 densityDpi=320,1080p 折算仅 540dp,
            // 会被 sw<600 误判成手机而锁竖屏(真机实测:画面竖板居中)
            if (ScreenUtils.isTv(this)) {
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }
            Configuration configuration = super.getResources().getConfiguration();
            return WindowSize.shouldLockPortrait(configuration.smallestScreenWidthDp)
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        } catch (Throwable th) {
            th.printStackTrace();
            return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationPolicy();
    }

    private void scheduleRefreshAutoSize() {
        View decorView = getWindow().getDecorView();
        decorView.removeCallbacks(refreshAutoSizeRunnable);
        decorView.postDelayed(refreshAutoSizeRunnable, 300);
    }

    private void refreshAutoSize() {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            if (dm.widthPixels <= 0 || dm.heightPixels <= 0) {
                return;
            }
            updateScreenRatio(dm);
            AutoSizeConfig.getInstance()
                    .setScreenWidth(dm.widthPixels)
                    .setScreenHeight(dm.heightPixels);
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(super.getResources(), this);
            getWindow().getDecorView().requestLayout();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void updateScreenRatio(DisplayMetrics dm) {
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        int min = Math.min(screenWidth, screenHeight);
        if (min > 0) {
            screenRatio = (float) Math.max(screenWidth, screenHeight) / (float) min;
        }
    }

    @Override
    public Resources getResources() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(super.getResources(), this);
        }
        return super.getResources();
    }

    public boolean hasPermission(String permission) {
        boolean has = true;
        try {
            has = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return has;
    }

    protected abstract int getLayoutResID();

    protected abstract void init();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppManager.getInstance().finishActivity(this);
    }

    protected String getAssetText(String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            AssetManager assets = getAssets();
            BufferedReader bf = new BufferedReader(new InputStreamReader(assets.open(fileName)));
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public float getSizeInDp() {
        return isBaseOnWidth() ? 1280 : 720;
    }

    @Override
    public boolean isBaseOnWidth() {
        return !(screenRatio >= 4.0f);
    }

}