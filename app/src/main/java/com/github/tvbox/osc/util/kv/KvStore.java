package com.github.tvbox.osc.util.kv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * KV 门面的最小存储后端抽象(2026-09-24 为 32 位设备引入)。
 *
 * <p>64 位设备由 {@link MmkvStore} 承载(MMKV 2.4.2,arm64 16KB 页对齐);32 位设备由
 * {@link PrefsStore} 承载 —— MMKV 自 2.0.0 起不再提供 armeabi-v7a 原生库,而 1.3.x 的
 * arm64 库只有 4KB 对齐、无法在 16KB 页新机上加载,故 32 位改走 SharedPreferences。
 *
 * <p>两个后端对门面都只暴露"存取 String":类型编解码仍统一由 {@link KVCodec} 负责,
 * 因此两条路径语义一致。
 */
public interface KvStore {

    /** 写入字符串值;成功返回 true。 */
    boolean encode(@NonNull String key, @NonNull String value);

    /** 读取字符串值;键不存在返回 null。 */
    @Nullable
    String decodeString(@NonNull String key);

    /** 判断键是否存在。 */
    boolean containsKey(@NonNull String key);

    /** 删除键。 */
    void removeValueForKey(@NonNull String key);
}
