package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.api.DanmakuApi;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 弹幕源测速:探测源地址本身能否连通(与网页版 share/probe.php 一致)。
 * HEAD 优先,连不上再 GET;任何 HTTP 状态码都算可达;返回往返毫秒,不可达返回 -1。
 * 在 IO 线程调用。
 */
public class DanmakuSpeedTester {

    private static final long TIMEOUT = 6000L;

    public static long test(String apiUrl) {
        String url = normalize(apiUrl);
        if (TextUtils.isEmpty(url)) return -1L;
        long start = System.currentTimeMillis();
        int code = probe(url, true);
        if (code <= 0) code = probe(url, false);
        if (code <= 0) return -1L;
        return System.currentTimeMillis() - start;
    }

    /** 去掉 "url|备注" 的备注段与末尾斜杠;非 http(s) 返回空串 */
    public static String normalize(String apiUrl) {
        String url = DanmakuApi.normalizeBaseUrl(apiUrl);
        return url.startsWith("http") ? url : "";
    }

    private static int probe(String url, boolean head) {
        try (Response response = client().newCall(request(url, head)).execute()) {
            return response.code();
        } catch (Throwable th) {
            return -1;
        }
    }

    private static Request request(String url, boolean head) {
        Request.Builder builder = new Request.Builder().url(url);
        if (head) builder.head();
        return builder.build();
    }

    /** 与 probe.php 的 CURLOPT_SSL_VERIFYPEER=false 对齐:自签/域名不符的源也要算可达 */
    private static OkHttpClient client() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());
            return new OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                    .callTimeout(TIMEOUT * 2, TimeUnit.MILLISECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .sslSocketFactory(context.getSocketFactory(), TRUST_ALL)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Throwable th) {
            return OkHttp.client(TIMEOUT);
        }
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
