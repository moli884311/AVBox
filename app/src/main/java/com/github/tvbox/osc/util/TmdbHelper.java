package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * TMDB 元数据辅助类。
 * 使用用户配置的 TMDB 读访问令牌调用 v3 接口，补齐海报、简介与演职员信息。
 */
public class TmdbHelper {

    public static final String DEFAULT_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIyMTJiOGM1ZmM5ZTk0ZDgxOWQ0NDdkZDdlZmZmZjQyYyIsIm5iZiI6MTc4MjM5OTM0OS45MzEsInN1YiI6IjZhM2Q0MTc1MjVlNDBkYjczMzQ5ODgzYyIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.djxGx7TotRilNE6tT_BXY5J7AFeXLcfhWixwiK2g4sw";
    public static final String DEFAULT_API_KEY = "212b8c5fc9e94d819d447dd7effff42c";

    private static final String API_ROOT = "https://api.tmdb.org/3/";
    /** 图片经自有服务器转发, 解决国内 image.tmdb.org 不可达导致首页大图不清晰/占位的问题 */
    private static final String IMG_ROOT = "https://tvbox.moliys.icu/tmdbimg.php?path=";

    private static final Map<String, Meta> CACHE = new HashMap<>();
    /** 磁盘缓存前缀: 冷启动后首页大图/简介可直接命中, 避免每次进入首页都"慢半拍" */
    private static final String DISK_PREFIX = "tmdb_meta_v1_";
    private static final Gson GSON = new Gson();

    public static class Meta {
        public String backdrop;
        public String poster;
        public String overview;
        public String director;
        public String actors;
    }

    public interface Callback {
        void onResult(Meta meta);
    }

    public static String getToken() {
        String token = Hawk.get(HawkConfig.TMDB_TOKEN, DEFAULT_TOKEN);
        return TextUtils.isEmpty(token) ? DEFAULT_TOKEN : token;
    }

    public static String getApiKey() {
        String apiKey = Hawk.get(HawkConfig.TMDB_API_KEY, DEFAULT_API_KEY);
        return TextUtils.isEmpty(apiKey) ? DEFAULT_API_KEY : apiKey;
    }

    public static boolean isConfigured() {
        return !TextUtils.isEmpty(getToken());
    }

    public static int getMatchMode() {
        return Hawk.get(HawkConfig.TMDB_MATCH, 0);
    }

    public static int getDetailMode() {
        return Hawk.get(HawkConfig.TMDB_DETAIL, 1);
    }

    public static boolean isKeepSize() {
        return Hawk.get(HawkConfig.TMDB_KEEP_SIZE, true);
    }

    public static boolean isEnabled() {
        return isConfigured() && getMatchMode() != 2;
    }

    public static boolean isEnhanced() {
        return isEnabled() && getDetailMode() == 1;
    }

    public static String imageUrl(String path, String size) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        if (path.startsWith("http")) {
            return path;
        }
        return IMG_ROOT + size + path;
    }

    public static void loadMeta(final String name, final int year, final Callback callback) {
        if (!isEnabled() || TextUtils.isEmpty(name) || callback == null) {
            return;
        }
        final String key = name + "#" + year;
        Meta cached = CACHE.get(key);
        if (cached == null) {
            cached = readDisk(key);
            if (cached != null) {
                CACHE.put(key, cached);
            }
        }
        if (cached != null) {
            callback.onResult(cached);
            return;
        }
        search(name, year, true, new Callback() {
            @Override
            public void onResult(Meta meta) {
                if (meta != null) {
                    CACHE.put(key, meta);
                    writeDisk(key, meta);
                }
                callback.onResult(meta);
            }
        });
    }

    /**
     * 预热元数据: 首页列表加载后提前拉取, 让焦点移动时大图/简介即时命中缓存。
     */
    public static void prefetch(final String name, final int year) {
        loadMeta(name, year, new Callback() {
            @Override
            public void onResult(Meta meta) {
            }
        });
    }

    private static Meta readDisk(String key) {
        try {
            String json = Hawk.get(DISK_PREFIX + key, "");
            if (TextUtils.isEmpty(json)) {
                return null;
            }
            return GSON.fromJson(json, Meta.class);
        } catch (Throwable th) {
            return null;
        }
    }

    private static void writeDisk(String key, Meta meta) {
        try {
            Hawk.put(DISK_PREFIX + key, GSON.toJson(meta));
        } catch (Throwable ignored) {
        }
    }

    private static void search(final String name, final int year, final boolean tryMovie, final Callback callback) {
        String type = tryMovie ? "movie" : "tv";
        StringBuilder url = new StringBuilder(API_ROOT)
                .append("search/").append(type)
                .append("?language=zh-CN&include_adult=false&query=").append(encode(name));
        if (tryMovie && getMatchMode() == 1 && year > 0) {
            url.append("&year=").append(year);
        }
        OkGo.<String>get(url.toString())
                .headers("Authorization", "Bearer " + getToken())
                .headers("Accept", "application/json")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body() == null ? "" : response.body().string();
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        JsonObject root = parse(response.body());
                        JsonArray results = root == null ? null : root.getAsJsonArray("results");
                        boolean matched = false;
                        if (results != null && results.size() > 0) {
                            JsonObject first = results.get(0).getAsJsonObject();
                            matched = isAcceptable(first, year);
                            if (matched) {
                                long id = first.has("id") ? first.get("id").getAsLong() : 0;
                                Meta meta = new Meta();
                                meta.backdrop = imageUrl(opt(first, "backdrop_path"), "original");
                                meta.poster = imageUrl(opt(first, "poster_path"), "w500");
                                meta.overview = opt(first, "overview");
                                loadCredits(tryMovie ? "movie" : "tv", id, meta, callback);
                            }
                        }
                        if (!matched) {
                            fallback(name, year, tryMovie, callback);
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        fallback(name, year, tryMovie, callback);
                    }
                });
    }

    private static void fallback(String name, int year, boolean tryMovie, Callback callback) {
        if (tryMovie) {
            search(name, year, false, callback);
        } else {
            callback.onResult(null);
        }
    }

    private static boolean isAcceptable(JsonObject item, int year) {
        String title = opt(item, "title");
        if (TextUtils.isEmpty(title)) {
            title = opt(item, "name");
        }
        if (TextUtils.isEmpty(title)) {
            return false;
        }
        if (getMatchMode() == 1 && year > 0) {
            String date = opt(item, "release_date");
            if (TextUtils.isEmpty(date)) {
                date = opt(item, "first_air_date");
            }
            if (!TextUtils.isEmpty(date) && date.length() >= 4) {
                try {
                    int itemYear = Integer.parseInt(date.substring(0, 4));
                    if (Math.abs(itemYear - year) > 1) {
                        return false;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return true;
    }

    private static void loadCredits(final String type, long id, final Meta meta, final Callback callback) {
        if (id <= 0) {
            callback.onResult(meta);
            return;
        }
        String url = API_ROOT + type + "/" + id + "?language=zh-CN&append_to_response=credits";
        OkGo.<String>get(url)
                .headers("Authorization", "Bearer " + getToken())
                .headers("Accept", "application/json")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body() == null ? "" : response.body().string();
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        JsonObject root = parse(response.body());
                        if (root != null) {
                            JsonObject credits = root.has("credits") && root.get("credits").isJsonObject()
                                    ? root.getAsJsonObject("credits") : null;
                            if (credits != null) {
                                meta.director = findDirector(credits.getAsJsonArray("crew"));
                                meta.actors = joinCast(credits.getAsJsonArray("cast"), 3);
                            }
                            if (TextUtils.isEmpty(meta.overview)) {
                                meta.overview = opt(root, "overview");
                            }
                            if (TextUtils.isEmpty(meta.backdrop)) {
                                meta.backdrop = imageUrl(opt(root, "backdrop_path"), "original");
                            }
                            if (TextUtils.isEmpty(meta.poster)) {
                                meta.poster = imageUrl(opt(root, "poster_path"), "w500");
                            }
                        }
                        callback.onResult(meta);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        callback.onResult(meta);
                    }
                });
    }

    private static String findDirector(JsonArray crew) {
        if (crew == null) {
            return "";
        }
        for (JsonElement element : crew) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject member = element.getAsJsonObject();
            if ("Directing".equals(opt(member, "department")) || "Director".equals(opt(member, "job"))) {
                return opt(member, "name");
            }
        }
        return "";
    }

    private static String joinCast(JsonArray cast, int limit) {
        if (cast == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (JsonElement element : cast) {
            if (!element.isJsonObject()) {
                continue;
            }
            String castName = opt(element.getAsJsonObject(), "name");
            if (TextUtils.isEmpty(castName)) {
                continue;
            }
            if (count > 0) {
                builder.append(" / ");
            }
            builder.append(castName);
            count++;
            if (count >= limit) {
                break;
            }
        }
        return builder.toString();
    }

    private static JsonObject parse(String json) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Throwable th) {
            return null;
        }
    }

    private static String opt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Throwable th) {
            return "";
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Throwable th) {
            return value;
        }
    }
}
