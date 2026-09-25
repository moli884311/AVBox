package com.github.tvbox.osc.util;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * 触摸设备上模拟电视端的两段式操作：
 * 第一次点击只让目标取得焦点并高亮，第二次点击才触发点击事件，
 * 与 TvRecyclerView 列表页的行为保持一致。
 */
public final class FocusTouchHelper {

    private FocusTouchHelper() {
    }

    public static void install(View root) {
        if (root == null) return;
        if (root.isFocusable()) {
            attach(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                install(group.getChildAt(i));
            }
        }
    }

    private static void attach(final View view) {
        view.setFocusableInTouchMode(true);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN && !v.isFocused()) {
                    v.requestFocusFromTouch();
                    return true;
                }
                return false;
            }
        });
    }
}
