package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.api.DanmakuApi;

/**
 * 弹幕源测速:请求 dandanplay 兼容源的搜索接口,返回耗时(毫秒);失败返回 -1。
 * 在 IO 线程调用。
 */
public class DanmakuSpeedTester {

    private static final long TIMEOUT = 6000L;

    public static long test(String apiUrl) {
        if (TextUtils.isEmpty(apiUrl)) return -1L;
        String base = DanmakuApi.normalizeBaseUrl(apiUrl);
        if (TextUtils.isEmpty(base)) return -1L;
        String testUrl = base + "/api/v2/search/anime?keyword=test";
        long start = System.currentTimeMillis();
        String body = OkHttp.string(testUrl, TIMEOUT);
        long cost = System.currentTimeMillis() - start;
        return TextUtils.isEmpty(body) ? -1L : cost;
    }
}
