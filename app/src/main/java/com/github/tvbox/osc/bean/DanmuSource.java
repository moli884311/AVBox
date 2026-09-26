package com.github.tvbox.osc.bean;

import android.text.TextUtils;

/**
 * 弹幕接口源。kind 用于在弹幕源列表里区分「自动」「内置源」「自定义」三种行。
 */
public class DanmuSource {
    public static final int KIND_AUTO = 0;
    public static final int KIND_SOURCE = 1;
    public static final int KIND_CUSTOM = 2;

    public int kind = KIND_SOURCE;
    public String name;
    public String url;

    public DanmuSource() {
    }

    public DanmuSource(int kind, String name, String url) {
        this.kind = kind;
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }
}
