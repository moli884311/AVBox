package com.github.tvbox.osc.util.kv;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 32 位设备后端:SharedPreferences 承载(MMKV 无 armeabi-v7a 原生库)。
 *
 * <p>与 MMKV 后端保持同一契约:值统一存 String、异步落盘({@code apply()})、明文落在应用
 * 私有目录,安全面与 MMKV 的明文 mmap 一致。键不存在或类型不符返回 null,由 {@link KVCodec}
 * 按"读不到"处理。
 */
public final class PrefsStore implements KvStore {

    private final SharedPreferences prefs;

    public PrefsStore(@NonNull SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public boolean encode(@NonNull String key, @NonNull String value) {
        // 与 MMKV 同为异步写;SharedPreferences.apply() 无失败返回值,按成功处理
        prefs.edit().putString(key, value).apply();
        return true;
    }

    @Override
    @Nullable
    public String decodeString(@NonNull String key) {
        return prefs.getString(key, null);
    }

    @Override
    public boolean containsKey(@NonNull String key) {
        return prefs.contains(key);
    }

    @Override
    public void removeValueForKey(@NonNull String key) {
        prefs.edit().remove(key).apply();
    }
}
