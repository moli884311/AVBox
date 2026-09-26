package com.github.tvbox.osc.api;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.bean.DanmuSource;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 弹幕接口源管理：内置源清单（远端热更新 + 本地兜底）、连通性测速、候选源排序。
 * 与手机/网页端 data/danmu.json 同源，供 TV 端「弹幕地址」选择使用。
 */
public class DanmuSourceManager {

    public static final int MODE_AUTO = 0;
    public static final int MODE_SOURCE = 1;
    public static final int MODE_CUSTOM = 2;

    public interface RemoteCallback {
        void onResult(List<DanmuSource> sources);
    }

    public interface ProbeListener {
        void onResult(String url, int latencyMs);

        default void onFinish() {
        }
    }

    private static final String TAG = "danmu-source";
    private static final String REMOTE_URL = "https://tvbox.moliys.icu/data/danmu.json";
    private static final long PROBE_TIMEOUT = 6000L;
    private static final int PROBE_POOL_SIZE = 4;
    private static final int LATENCY_UNKNOWN = Integer.MIN_VALUE;
    private static final int MAX_AUTO_CANDIDATES = 6;

    /** 内置兜底源：远端清单拉取失败时使用 */
    private static final String[] FALLBACK = new String[]{
            "ecs源|http://ecs.dysobo.cn:9321/87654321",
            "快源|http://43.143.108.212/87654321",
            "阿里云源|http://47.107.188.112:6008/87654321",
            "量大|http://38.207.186.41/87654321",
            "luosen|https://dm.ljiaovm.com/luosen",
            "ip源|https://172.252.125.145/87654321",
            "sym源|https://danmu1.sym9233.dpdns.org/87654321",
            "777775源|https://danmu.7777735.xyz/87654321",
            "pizazz源|https://pizazz.us.ci/1314",
            "公益1|https://danmu.iyo.us.ci/theft-dastardly-prognosis-hula-agenda2-dropkick",
            "公益2|https://danmu-api-one-vert.vercel.app/87654321",
            "W佬公益源|https://dm.660505.xyz:8443/a123456",
    };

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Map<String, Integer> latency = new HashMap<>();

    private static List<DanmuSource> sources;
    private static boolean latencyLoaded = false;
    private static boolean remoteLoaded = false;
    private static boolean remoteLoading = false;
    private static boolean remoteTried = false;
    private static ExecutorService probePool;

    /* ---------------- 模式与选择 ---------------- */

    public static int getMode() {
        int mode = Hawk.get(HawkConfig.DANMU_SOURCE_MODE, -1);
        if (mode >= 0) return mode;
        String legacy = Hawk.get(HawkConfig.DANMU_API, "");
        boolean legacyDefault = Hawk.get(DanmakuApi.USE_DEFAULT_KEY, false);
        mode = (!TextUtils.isEmpty(legacy) && !legacyDefault) ? MODE_CUSTOM : MODE_AUTO;
        Hawk.put(HawkConfig.DANMU_SOURCE_MODE, mode);
        return mode;
    }

    public static boolean isAuto() {
        return getMode() == MODE_AUTO;
    }

    public static String getSelectedUrl() {
        if (getMode() == MODE_AUTO) return "";
        return Hawk.get(HawkConfig.DANMU_API, "").trim();
    }

    public static String getSelectedName() {
        String name = Hawk.get(HawkConfig.DANMU_SOURCE_NAME, "");
        return name == null ? "" : name.trim();
    }

    public static void selectSource(DanmuSource source) {
        if (source == null) return;
        Hawk.put(HawkConfig.DANMU_SOURCE_MODE, MODE_SOURCE);
        Hawk.put(HawkConfig.DANMU_SOURCE_NAME, source.getName());
        Hawk.put(HawkConfig.DANMU_API, source.getUrl());
    }

    public static void selectCustom(String url) {
        Hawk.put(HawkConfig.DANMU_SOURCE_MODE, MODE_CUSTOM);
        Hawk.put(HawkConfig.DANMU_SOURCE_NAME, "");
        Hawk.put(HawkConfig.DANMU_API, url == null ? "" : url.trim());
    }

    public static void useAuto() {
        Hawk.put(HawkConfig.DANMU_SOURCE_MODE, MODE_AUTO);
        Hawk.put(HawkConfig.DANMU_SOURCE_NAME, "");
        Hawk.put(HawkConfig.DANMU_API, "");
    }

    public static String getModeText() {
        int mode = getMode();
        if (mode == MODE_CUSTOM) return "自定义";
        if (mode == MODE_SOURCE) {
            String name = getSelectedName();
            return TextUtils.isEmpty(name) ? "指定源" : name;
        }
        return "自动";
    }

    /* ---------------- 源清单 ---------------- */

    public static List<DanmuSource> getSources() {
        if (sources == null) {
            sources = loadCachedSources();
            if (sources.isEmpty()) sources = parseFallback();
        }
        if (!remoteLoaded && !remoteLoading && !remoteTried) refreshRemote(null);
        return sources;
    }

    public static void refreshRemote(final RemoteCallback callback) {
        if (remoteLoading) return;
        remoteLoading = true;
        remoteTried = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<DanmuSource> result = new ArrayList<>();
                try {
                    OkHttpClient client = OkHttp.client(PROBE_TIMEOUT);
                    Response response = client.newCall(new Request.Builder().url(REMOTE_URL).build()).execute();
                    String body = response.body() == null ? "" : response.body().string();
                    response.close();
                    parseSources(result, body);
                } catch (Throwable th) {
                    LOG.e("danmu-source remote error: " + th.getMessage());
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        remoteLoading = false;
                        if (!result.isEmpty()) {
                            sources = result;
                            remoteLoaded = true;
                            Hawk.put(HawkConfig.DANMU_SOURCE_LIST, toSourcesJson(result));
                        }
                        if (callback != null) callback.onResult(getSources());
                    }
                });
            }
        }).start();
    }

    private static List<DanmuSource> loadCachedSources() {
        List<DanmuSource> list = new ArrayList<>();
        String json = Hawk.get(HawkConfig.DANMU_SOURCE_LIST, "");
        if (!TextUtils.isEmpty(json)) parseSources(list, json);
        return list;
    }

    private static List<DanmuSource> parseFallback() {
        List<DanmuSource> list = new ArrayList<>();
        for (String raw : FALLBACK) {
            int idx = raw.indexOf('|');
            if (idx <= 0) continue;
            list.add(new DanmuSource(DanmuSource.KIND_SOURCE, raw.substring(0, idx), raw.substring(idx + 1)));
        }
        return list;
    }

    private static void parseSources(List<DanmuSource> out, String body) {
        if (TextUtils.isEmpty(body)) return;
        try {
            JSONArray array = new JSONArray(body.trim());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "").trim();
                if (TextUtils.isEmpty(url)) continue;
                String name = item.optString("name", "").trim();
                out.add(new DanmuSource(DanmuSource.KIND_SOURCE, TextUtils.isEmpty(name) ? url : name, url));
            }
        } catch (Throwable th) {
            LOG.e("danmu-source parse error: " + th.getMessage());
        }
    }

    private static String toSourcesJson(List<DanmuSource> list) {
        JSONArray array = new JSONArray();
        for (DanmuSource source : list) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", source.getName());
                item.put("url", source.getUrl());
            } catch (Throwable ignored) {
            }
            array.put(item);
        }
        return array.toString();
    }

    /* ---------------- 测速 ---------------- */

    /** @return 延迟毫秒；-1 表示不可用；Integer.MIN_VALUE 表示未测速 */
    public static int getLatency(String url) {
        if (TextUtils.isEmpty(url)) return LATENCY_UNKNOWN;
        ensureLatencyLoaded();
        Integer value = latency.get(url);
        return value == null ? LATENCY_UNKNOWN : value;
    }

    public static void probeAll(final ProbeListener listener) {
        final List<DanmuSource> list = getSources();
        if (probePool == null || probePool.isShutdown()) {
            probePool = Executors.newFixedThreadPool(PROBE_POOL_SIZE);
        }
        final AtomicInteger pending = new AtomicInteger(0);
        for (final DanmuSource source : list) {
            if (TextUtils.isEmpty(source.getUrl())) continue;
            pending.incrementAndGet();
            probePool.execute(new Runnable() {
                @Override
                public void run() {
                    final int ms = probeOnce(source.getUrl());
                    final String url = source.getUrl();
                    synchronized (latency) {
                        latency.put(url, ms);
                    }
                    saveLatency();
                    if (listener != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onResult(url, ms);
                            }
                        });
                    }
                    if (pending.decrementAndGet() == 0 && listener != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onFinish();
                            }
                        });
                    }
                }
            });
        }
        if (pending.get() == 0 && listener != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onFinish();
                }
            });
        }
    }

    /** @return 当前测速结果中最快的可用源；无可用结果时返回 null */
    public static DanmuSource getFastestSource() {
        ensureLatencyLoaded();
        DanmuSource best = null;
        int bestLatency = Integer.MAX_VALUE;
        for (DanmuSource source : getSources()) {
            Integer value = latency.get(source.getUrl());
            if (value == null || value < 0) continue;
            if (value < bestLatency) {
                bestLatency = value;
                best = source;
            }
        }
        return best;
    }

    private static int probeOnce(String url) {
        if (TextUtils.isEmpty(url)) return -1;
        long start = SystemClock.elapsedRealtime();
        try {
            OkHttpClient client = OkHttp.client(PROBE_TIMEOUT);
            Response response = client.newCall(new Request.Builder().url(url).head().build()).execute();
            response.close();
            return (int) (SystemClock.elapsedRealtime() - start);
        } catch (Throwable ignored) {
        }
        start = SystemClock.elapsedRealtime();
        try {
            OkHttpClient client = OkHttp.client(PROBE_TIMEOUT);
            Response response = client.newCall(new Request.Builder().url(url).build()).execute();
            response.close();
            return (int) (SystemClock.elapsedRealtime() - start);
        } catch (Throwable th) {
            return -1;
        }
    }

    private static void ensureLatencyLoaded() {
        if (latencyLoaded) return;
        latencyLoaded = true;
        String json = Hawk.get(HawkConfig.DANMU_SOURCE_LATENCY, "");
        if (TextUtils.isEmpty(json)) return;
        try {
            JSONObject object = new JSONObject(json);
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                latency.put(key, object.optInt(key, 0));
            }
        } catch (Throwable th) {
            LOG.e("danmu-source latency parse error: " + th.getMessage());
        }
    }

    private static void saveLatency() {
        JSONObject object = new JSONObject();
        synchronized (latency) {
            for (Map.Entry<String, Integer> entry : latency.entrySet()) {
                try {
                    object.put(entry.getKey(), entry.getValue());
                } catch (Throwable ignored) {
                }
            }
        }
        Hawk.put(HawkConfig.DANMU_SOURCE_LATENCY, object.toString());
    }

    /* ---------------- 候选源 ---------------- */

    /**
     * 返回按优先级排序的候选接口地址。
     * 自动模式：有测速结果时按可用源的延迟升序排列（默认源兜底在最后）；无测速结果时默认源优先。
     * 指定/自定义模式：只返回所选地址。
     */
    public static List<String> getCandidateUrls(String builtinApi) {
        ensureLatencyLoaded();
        List<String> result = new ArrayList<>();
        if (getMode() != MODE_AUTO) {
            String url = getSelectedUrl();
            result.add(TextUtils.isEmpty(url) ? builtinApi : url);
            return dedup(result);
        }
        if (!latency.isEmpty()) {
            List<String> reachable = new ArrayList<>();
            for (DanmuSource source : getSources()) {
                Integer value = latency.get(source.getUrl());
                if (value != null && value >= 0) reachable.add(source.getUrl());
            }
            Collections.sort(reachable, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return rank(a) - rank(b);
                }
            });
            result.addAll(reachable);
            result.add(builtinApi);
        } else {
            result.add(builtinApi);
            for (DanmuSource source : getSources()) result.add(source.getUrl());
        }
        return cap(dedup(result), MAX_AUTO_CANDIDATES);
    }

    private static List<String> cap(List<String> urls, int max) {
        if (urls.size() <= max) return urls;
        return new ArrayList<>(urls.subList(0, max));
    }

    private static int rank(String url) {
        Integer value = latency.get(url);
        if (value == null) return 100000;
        if (value < 0) return 1000000;
        return value;
    }

    private static List<String> dedup(List<String> urls) {
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (String url : urls) {
            if (TextUtils.isEmpty(url)) continue;
            seen.put(url, Boolean.TRUE);
        }
        return new ArrayList<>(seen.keySet());
    }
}
