package com.github.tvbox.osc.api;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.util.LOG;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 平台弹幕引擎:内置「沫离弹幕聚合」服务(danmu_api),按片名+集数取回弹幕。
 * <p>
 * 聚合服务内部已实现各视频平台(腾讯/爱奇艺/优酷/芒果/B站/搜狐/乐视/PPTV/咪咕/360 等),
 * 返回的候选名带 `from <platform>` 标签,二跳即得 B 站标准弹幕 XML
 * （{@code <i><d p="时间秒,模式,字号,颜色十进制,...">文本</d></i>}），
 * 与 {@link com.github.tvbox.osc.player.danmu.Parser} 期望的格式一致，App 侧无需逆向各平台接口。
 */
public class PlatformDanmuEngine {

    public interface PlatformCallback {
        void onFound(String xml);

        void onNotFound();
    }

    /** 内置平台弹幕聚合服务(danmu_api 独立容器,见运维文档) */
    public static final String SERVER = "http://8.130.134.173:5757";

    private static final String TAG = "PlatformDanmuEngine";
    private static final String PATH_SEARCH = "/api/v2/fongmi/danmaku?name=";
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);
    private static final int MAX_TRY = 4;
    private static final Pattern DIGIT = Pattern.compile("(\\d+)");

    /**
     * 平台优先级(取名字里的 `from <platform>`,下标越小越优先);未列出者排最后。
     * 与聚合服务的匹配能力对齐:先腾讯/爱奇艺/优酷/芒果/B站,再其它。
     */
    private static final String[] PLATFORM_ORDER = {
            "tencent", "iqiyi", "qiyi", "youku", "mgtv", "bilibili",
            "sohu", "letv", "pptv", "migu", "acfun", "360"
    };

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final AtomicInteger seqGen = new AtomicInteger();
    private static final AtomicInteger currentSeq = new AtomicInteger();

    /** 取消进行中的平台搜索(切集/退出时调用) */
    public static void cancel() {
        currentSeq.incrementAndGet();
        OkHttp.cancel(TAG);
    }

    /**
     * 按片名+集数搜平台弹幕。回调在主线程，返回的是可直接喂 Parser 的弹幕 XML。
     */
    public static void search(String name, String episode, PlatformCallback callback) {
        if (callback == null) return;
        final int seq = seqGen.incrementAndGet();
        currentSeq.set(seq);
        OkHttp.cancel(TAG);
        if (TextUtils.isEmpty(name)) {
            notifyNotFound(callback, seq);
            return;
        }
        final int ep = parseEpisode(episode);
        String url = SERVER + PATH_SEARCH + encode(name) + "&episode=" + ep;
        LOG.i("echo-danmu platform search: " + url);
        try {
            OkHttp.newCall(OkHttp.client(TIMEOUT_MS), url, TAG).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (!isCurrent(seq)) return;
                    LOG.e("echo-danmu platform list error: " + e.getMessage());
                    notifyNotFound(callback, seq);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        if (!isCurrent(seq)) return;
                        String body = response.body() == null ? "" : response.body().string();
                        List<Candidate> candidates = parseCandidates(body);
                        if (candidates.isEmpty()) {
                            notifyNotFound(callback, seq);
                            return;
                        }
                        sortCandidates(candidates, ep);
                        loadCandidate(candidates, 0, callback, seq);
                    } catch (Throwable th) {
                        LOG.e("echo-danmu platform list parse error: " + th.getMessage());
                        notifyNotFound(callback, seq);
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Throwable th) {
            LOG.e("echo-danmu platform search start error: " + th.getMessage());
            notifyNotFound(callback, seq);
        }
    }

    private static void loadCandidate(List<Candidate> candidates, int index, PlatformCallback callback, int seq) {
        if (index >= candidates.size() || index >= MAX_TRY) {
            notifyNotFound(callback, seq);
            return;
        }
        if (!isCurrent(seq)) return;
        final Candidate candidate = candidates.get(index);
        final Runnable next = () -> loadCandidate(candidates, index + 1, callback, seq);
        LOG.i("echo-danmu platform comment: " + candidate.name + " -> " + candidate.url);
        try {
            OkHttp.newCall(OkHttp.client(TIMEOUT_MS), candidate.url, TAG).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    LOG.e("echo-danmu platform comment error: " + e.getMessage());
                    next.run();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String body = response.body() == null ? "" : response.body().string();
                        if (!isCurrent(seq)) return;
                        if (isDanmuXml(body)) {
                            notifyFound(callback, seq, body);
                        } else {
                            next.run();
                        }
                    } catch (Throwable th) {
                        LOG.e("echo-danmu platform comment parse error: " + th.getMessage());
                        next.run();
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Throwable th) {
            LOG.e("echo-danmu platform comment start error: " + th.getMessage());
            next.run();
        }
    }

    private static List<Candidate> parseCandidates(String body) {
        List<Candidate> list = new ArrayList<>();
        if (TextUtils.isEmpty(body)) return list;
        String text = body.trim();
        if (!text.startsWith("[")) return list;
        try {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String url = object.optString("url", "").trim();
                if (!url.startsWith("http")) continue;
                list.add(new Candidate(object.optString("name", "").trim(), url));
            }
        } catch (Throwable th) {
            LOG.e("echo-danmu platform list json error: " + th.getMessage());
        }
        return list;
    }

    /** 命中集数的排前面,同档按平台优先级排;集数为 0 时只按平台优先级 */
    private static void sortCandidates(List<Candidate> candidates, int episode) {
        for (Candidate candidate : candidates) {
            candidate.episodeMatch = episode > 0 && matchesEpisode(candidate.name, episode);
            candidate.platformRank = platformRank(candidate.name);
        }
        Collections.sort(candidates, (a, b) -> {
            if (a.episodeMatch != b.episodeMatch) return a.episodeMatch ? -1 : 1;
            return Integer.compare(a.platformRank, b.platformRank);
        });
    }

    private static boolean matchesEpisode(String name, int episode) {
        if (TextUtils.isEmpty(name)) return false;
        String[] patterns = {
                "第" + episode + "集", "第" + episode + "话", "第" + episode + "期",
                "第" + episode + "部", "第0" + episode + "集", "_0" + episode, "_" + episode
        };
        for (String pattern : patterns) {
            if (name.contains(pattern)) return true;
        }
        return false;
    }

    private static int platformRank(String name) {
        if (TextUtils.isEmpty(name)) return PLATFORM_ORDER.length;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < PLATFORM_ORDER.length; i++) {
            if (lower.contains(PLATFORM_ORDER[i])) return i;
        }
        return PLATFORM_ORDER.length;
    }

    private static boolean isDanmuXml(String body) {
        if (TextUtils.isEmpty(body)) return false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            return ch == '<';
        }
        return false;
    }

    private static int parseEpisode(String episode) {
        if (TextUtils.isEmpty(episode)) return 0;
        Matcher matcher = DIGIT.matcher(episode);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (Throwable th) {
            return text;
        }
    }

    private static boolean isCurrent(int seq) {
        return seq == currentSeq.get();
    }

    private static void notifyFound(PlatformCallback callback, int seq, String xml) {
        if (callback == null || !isCurrent(seq)) return;
        handler.post(() -> {
            if (isCurrent(seq)) callback.onFound(xml);
        });
    }

    private static void notifyNotFound(PlatformCallback callback, int seq) {
        if (callback == null || !isCurrent(seq)) return;
        handler.post(() -> {
            if (isCurrent(seq)) callback.onNotFound();
        });
    }

    private static class Candidate {
        final String name;
        final String url;
        boolean episodeMatch;
        int platformRank = PLATFORM_ORDER.length;

        Candidate(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
