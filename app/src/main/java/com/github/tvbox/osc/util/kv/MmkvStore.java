package com.github.tvbox.osc.util.kv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tencent.mmkv.MMKV;

/**
 * 64 位设备后端:转发到 MMKV 单进程实例。
 *
 * <p>本项目所有 MMKV 直接引用都集中在此类,KV 门面不再链接 {@code com.tencent.mmkv.MMKV},
 * 这样 32 位设备(无 {@code libmmkv.so})不会因类加载触发 {@code UnsatisfiedLinkError}。
 */
public final class MmkvStore implements KvStore {

    private final MMKV mmkv;

    private MmkvStore(@NonNull MMKV mmkv) {
        this.mmkv = mmkv;
    }

    /** 初始化 MMKV 并打开指定 id 的单进程实例(幂等,见 {@code App.initParams})。 */
    @NonNull
    public static MmkvStore create(@NonNull Context context, @NonNull String id) {
        MMKV.initialize(context);
        return new MmkvStore(MMKV.mmkvWithID(id, MMKV.SINGLE_PROCESS_MODE));
    }

    @Override
    public boolean encode(@NonNull String key, @NonNull String value) {
        return mmkv.encode(key, value);
    }

    @Override
    @Nullable
    public String decodeString(@NonNull String key) {
        return mmkv.decodeString(key);
    }

    @Override
    public boolean containsKey(@NonNull String key) {
        return mmkv.containsKey(key);
    }

    @Override
    public void removeValueForKey(@NonNull String key) {
        mmkv.removeValueForKey(key);
    }
}
