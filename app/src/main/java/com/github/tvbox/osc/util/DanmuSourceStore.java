package com.github.tvbox.osc.util;

import android.text.TextUtils;

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

    /** 内置源(与设置页「弹幕 API」列表一致) */
    public static List<Item> defaults() {
        List<Item> list = new ArrayList<>();
        list.add(new Item("阿里云", "http://47.107.188.112:6008/87654321", true));
        list.add(new Item("快源", "http://43.143.108.212/87654321", true));
        list.add(new Item("ecs", "http://ecs.dysobo.cn:9321/87654321", true));
        list.add(new Item("默认", "https://logvardanmu.konfan.cn/87654321", true));
        return list;
    }

    /** 读取源列表;首次读取时把旧的单条「弹幕 API」迁入,并落盘内置默认 */
    public static List<Item> load() {
        String raw = KV.get(KEY, "");
        if (!TextUtils.isEmpty(raw)) {
            List<Item> parsed = parse(raw);
            if (parsed != null) return parsed;
        }
        List<Item> list = defaults();
        String legacy = KV.get(HawkConfig.DANMU_API, "");
        if (!TextUtils.isEmpty(legacy)) {
            list.add(0, new Item("自定义", legacy.trim(), true));
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
                object.put("url", item.url);
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
            if (item.enabled && !TextUtils.isEmpty(item.url)) urls.add(item.url.trim());
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
                String url = object.optString("url", "").trim();
                if (TextUtils.isEmpty(url)) continue;
                String name = object.optString("name", "").trim();
                list.add(new Item(TextUtils.isEmpty(name) ? url : name, url,
                        object.optBoolean("enabled", true), object.optLong("latency", 0L)));
            }
            return list;
        } catch (Throwable th) {
            return null;
        }
    }
}
