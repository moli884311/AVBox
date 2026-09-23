package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹幕 API 源列表(dandanplay 兼容源)。顺序即优先级:自动搜弹幕时按顺序逐个尝试,
 * 停用项不参与匹配。持久化为 JSON 字符串,避免给 KV 增加集合类型注册。
 */
public class DanmuSourceStore {

    /** 单条弹幕源:latency 单位毫秒;0=未测速,-1=测速失败 */
    public static class Item {
        public String name;
        public String url;
        public boolean enabled;
        public long latency;

        public Item(String name, String url, boolean enabled) {
            this(name, url, enabled, 0L);
        }

        public Item(String name, String url, boolean enabled, long latency) {
            this.name = name;
            this.url = url;
            this.enabled = enabled;
            this.latency = latency;
        }

        public Item copy() {
            return new Item(name, url, enabled, latency);
        }
    }

    private static final String KEY = HawkConfig.DANMU_API_LIST;

    /** 站点弹幕源清单,「恢复默认」优先拉它,失败才用内置 */
    public static final String REMOTE_DEFAULTS = "https://tvbox.moliys.icu/data/danmu.json";

    /** 内置源(顺序即优先级,与 https://tvbox.moliys.icu 「弹幕」标签页的 data/danmu.json 一致) */
    public static List<Item> defaults() {
        List<Item> list = new ArrayList<>();
        list.add(new Item("ecs源", "http://ecs.dysobo.cn:9321/87654321", true));
        list.add(new Item("快源", "http://43.143.108.212/87654321", true));
        list.add(new Item("阿里云源", "http://47.107.188.112:6008/87654321", true));
        list.add(new Item("量大", "http://38.207.186.41/87654321", true));
        list.add(new Item("luosen", "https://dm.ljiaovm.com/luosen", true));
        list.add(new Item("ip源", "https://172.252.125.145/87654321", true));
        list.add(new Item("sym源", "https://danmu1.sym9233.dpdns.org/87654321", true));
        list.add(new Item("777775源", "https://danmu.7777735.xyz/87654321", true));
        list.add(new Item("pizazz源", "https://pizazz.us.ci/1314", true));
        list.add(new Item("公益1", "https://danmu.iyo.us.ci/theft-dastardly-prognosis-hula-agenda2-dropkick", true));
        list.add(new Item("公益2", "https://danmu-api-one-vert.vercel.app/87654321", true));
        list.add(new Item("W佬公益源", "https://dm.660505.xyz:8443/a123456", true));
        return list;
    }

    /** 拉取站点最新源清单;失败或无有效条目返回 null(调用方回退 defaults) */
    public static List<Item> fetchRemoteDefaults() {
        String body = OkHttp.string(REMOTE_DEFAULTS, 8000L);
        if (TextUtils.isEmpty(body)) return null;
        try {
            JSONArray array = new JSONArray(body.trim());
            List<Item> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String raw = object.optString("url", "");
                String url = cleanUrl(raw);
                if (url == null) continue;
                String name = object.optString("name", "").trim();
                if (TextUtils.isEmpty(name)) name = aliasOf(raw);
                list.add(new Item(TextUtils.isEmpty(name) ? url : name, url, true));
            }
            return list.isEmpty() ? null : list;
        } catch (Throwable th) {
            return null;
        }
    }

    /** 去掉 "url|备注" 的备注段;返回 null 表示非法 */
    public static String cleanUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        int pipe = url.indexOf('|');
        if (pipe >= 0) url = url.substring(0, pipe).trim();
        return url.startsWith("http") ? url : null;
    }

    /** "url|备注" 里的备注名(没有则空串) */
    public static String aliasOf(String raw) {
        String url = raw == null ? "" : raw.trim();
        int pipe = url.indexOf('|');
        return pipe >= 0 ? url.substring(pipe + 1).trim() : "";
    }

    /** 读取源列表;首次读取时把旧的单条「弹幕 API」迁入,并落盘内置默认 */
    public static List<Item> load() {
        String raw = KV.get(KEY, "");
        if (!TextUtils.isEmpty(raw)) {
            List<Item> parsed = parse(raw);
            if (parsed != null) return parsed;
        }
        List<Item> list = defaults();
        String legacy = cleanUrl(KV.get(HawkConfig.DANMU_API, ""));
        if (legacy != null) {
            list.add(0, new Item("自定义", legacy, true));
        }
        save(list);
        return list;
    }

    public static void save(List<Item> list) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : list) {
                JSONObject object = new JSONObject();
                object.put("name", item.name);
                object.put("url", TextUtils.isEmpty(cleanUrl(item.url)) ? item.url : cleanUrl(item.url));
                object.put("enabled", item.enabled);
                object.put("latency", item.latency);
                array.put(object);
            }
        } catch (Throwable ignored) {
        }
        KV.put(KEY, array.toString());
    }

    public static void resetDefaults() {
        save(defaults());
    }

    /** 参与自动匹配的接口地址(按优先级) */
    public static List<String> enabledUrls() {
        List<String> urls = new ArrayList<>();
        for (Item item : load()) {
            String url = cleanUrl(item.url);
            if (item.enabled && url != null) urls.add(url);
        }
        return urls;
    }

    /** 解析 JSON;格式非法返回 null,合法的空数组返回空列表 */
    private static List<Item> parse(String raw) {
        try {
            JSONArray array = new JSONArray(raw);
            List<Item> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String raw = object.optString("url", "");
                String url = cleanUrl(raw);
                if (url == null) continue;
                String name = object.optString("name", "").trim();
                if (TextUtils.isEmpty(name)) name = aliasOf(raw);
                list.add(new Item(TextUtils.isEmpty(name) ? url : name, url,
                        object.optBoolean("enabled", true), object.optLong("latency", 0L)));
            }
            return list;
        } catch (Throwable th) {
            return null;
        }
    }
}
