package com.github.tvbox.osc.util;

import static android.content.Context.UI_MODE_SERVICE;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.WindowManager;

public class ScreenUtils {

    public static double getSqrt(Activity activity) {
        WindowManager wm = activity.getWindowManager();
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        double x = Math.pow(dm.widthPixels / dm.xdpi, 2);
        double y = Math.pow(dm.heightPixels / dm.ydpi, 2);
        double screenInches = Math.sqrt(x + y);// 屏幕尺寸
        return screenInches;
    }

    /**
     * 是否处于 TV 模式(锁屏钮 / 若干 gesture 据此隐藏)。
     *
     * <p>2026-09-13 收窄判据:原实现是 `UI_MODE_TYPE_TELEVISION || (屏幕很大 && !是手机)`,
     * 其中"是手机"靠 `TelephonyManager.getPhoneType()` —— 该调用**需要 READ_PHONE_STATE**
     * (API 23+ 未授权会 SecurityException),于是清单里长期挂着一个没人用的敏感权限。
     *
     * <p>但本项目是**纯手机定位**(`abiFilters` 只有 arm64-v8a、TV/遥控器适配代码已全删,
     * 见 avbox-mobile-ui-spec §1):真正需要防的反而是"大屏手机被 SCREENLAYOUT_SIZE_LARGE
     * 误判成 TV"(会莫名隐藏锁屏钮)。故只保留"系统声明为 TV"这一个权威判据,
     * 电话权限随之从清单移除。
     */
    public static boolean isTv(Context context) {
        if (context == null) return false;
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            // 部分电视盒子(尤其非认证 Android TV)UI_MODE 报 NORMAL,但会声明 leanback 特性。
            // 手机不声明该特性,故不会把大屏手机误判成 TV(见上方 2026-09-13 收窄说明)。
            if (pm.hasSystemFeature("android.software.leanback")) return true;
            if (pm.hasSystemFeature("android.hardware.type.television")) return true;
            // 无触摸屏 = 只能靠遥控/按键操作,必然是 TV/盒子场景(超大屏手机仍报触摸屏,不会命中)。
            if (!pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)) return true;
        } catch (Throwable ignored) {
            // PackageManager 异常时继续走输入设备探测
        }
        return hasRemoteInputDevice(context);
    }

    /**
     * 是否接了带方向键(DPAD)的外接输入设备 —— 遥控器/手柄/电视键盘。
     *
     * <p>2026-09-24 追加:部分非认证盒子既不报 TELEVISION 也不声明 leanback,
     * 之前整套 TV 焦点适配(描边/初始焦点/按键路由)全部静默失效,表现为"遥控器在设置页用不了"。
     * 这类设备唯一可靠的信号就是"存在带 DPAD 的输入设备"。手机的内置软键盘一般不注册 DPAD source,
     * 因此不会把普通手机误判成 TV。
     */
    private static boolean hasRemoteInputDevice(Context context) {
        try {
            InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            if (im == null) return false;
            for (int id : im.getInputDeviceIds()) {
                InputDevice device = im.getInputDevice(id);
                if (device == null) continue;
                // 跳过虚拟输入设备:手机的软键盘等会作为 "Virtual" 设备注册,可能顺带声明 DPAD,
                // 不排除会把普通手机误判成 TV。
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
                        && device.isVirtual()) {
                    continue;
                }
                int sources = device.getSources();
                if ((sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) return true;
                if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
