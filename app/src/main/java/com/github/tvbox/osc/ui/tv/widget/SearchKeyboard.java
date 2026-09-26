package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;
import java.util.List;


/**
 * 电视端搜索键盘: 全键盘(A-Z) 与 T9 数字键盘两种布局, 数字为真实可输入。
 */
public class SearchKeyboard extends FrameLayout {
    public static final int MODE_FULL = 0;
    public static final int MODE_T9 = 1;

    public static final String KEY_BACKSPACE = "删除";
    public static final String KEY_CLEAR = "清空";
    public static final String KEY_TO_T9 = "123";
    public static final String KEY_TO_FULL = "ABC";

    private RecyclerView mRecyclerView;
    private KeyboardAdapter adapter;
    private GridLayoutManager manager;
    private final List<Keyboard> keyboardList = new ArrayList<>();
    private int mode = MODE_FULL;
    private OnSearchKeyListener searchKeyListener;
    private OnModeChangeListener modeChangeListener;

    private final OnFocusChangeListener focusChangeListener = new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View itemView, boolean hasFocus) {
            if (null != itemView && itemView != mRecyclerView) {
                itemView.setSelected(hasFocus);
            }
        }
    };

    public SearchKeyboard(@NonNull Context context) {
        this(context, null);
    }

    public SearchKeyboard(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchKeyboard(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_keyborad, this);
        mRecyclerView = view.findViewById(R.id.mRecyclerView);
        manager = new GridLayoutManager(getContext(), 5);
        mRecyclerView.setLayoutManager(manager);
        mRecyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View child) {
                if (child.isFocusable() && null == child.getOnFocusChangeListener()) {
                    child.setOnFocusChangeListener(focusChangeListener);
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
        adapter = new KeyboardAdapter(keyboardList);
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter a, View view, int position) {
                if (position < 0 || position >= keyboardList.size()) return;
                onKey(keyboardList.get(position).label);
            }
        });
        adapter.setSpanSizeLookup(new BaseQuickAdapter.SpanSizeLookup() {
            @Override
            public int getSpanSize(GridLayoutManager gridLayoutManager, int position) {
                if (mode == MODE_FULL) {
                    if (position == keyboardList.size() - 1) return 2;
                    return 1;
                }
                return 1;
            }
        });
        mRecyclerView.setAdapter(adapter);
        applyMode();
    }

    private void onKey(String key) {
        if (TextUtils.isEmpty(key)) return;
        if (KEY_TO_T9.equals(key)) {
            setMode(MODE_T9);
            return;
        }
        if (KEY_TO_FULL.equals(key)) {
            setMode(MODE_FULL);
            return;
        }
        if (searchKeyListener != null) {
            searchKeyListener.onSearchKey(key);
        }
    }

    public void setMode(int newMode) {
        if (newMode != MODE_FULL && newMode != MODE_T9) return;
        if (mode == newMode && !keyboardList.isEmpty()) return;
        mode = newMode;
        applyMode();
        if (modeChangeListener != null) {
            modeChangeListener.onModeChanged(mode);
        }
    }

    public int getMode() {
        return mode;
    }

    private void applyMode() {
        keyboardList.clear();
        if (mode == MODE_FULL) {
            manager.setSpanCount(5);
            for (int i = 0; i < 26; i++) {
                keyboardList.add(new Keyboard(String.valueOf((char) ('A' + i)), ""));
            }
            keyboardList.add(new Keyboard(KEY_TO_T9, ""));
            keyboardList.add(new Keyboard(KEY_BACKSPACE, ""));
            keyboardList.add(new Keyboard(KEY_CLEAR, ""));
        } else {
            manager.setSpanCount(3);
            keyboardList.add(new Keyboard("1", ""));
            keyboardList.add(new Keyboard("2", "ABC"));
            keyboardList.add(new Keyboard("3", "DEF"));
            keyboardList.add(new Keyboard("4", "GHI"));
            keyboardList.add(new Keyboard("5", "JKL"));
            keyboardList.add(new Keyboard("6", "MNO"));
            keyboardList.add(new Keyboard("7", "PQRS"));
            keyboardList.add(new Keyboard("8", "TUV"));
            keyboardList.add(new Keyboard("9", "WXYZ"));
            keyboardList.add(new Keyboard(KEY_BACKSPACE, ""));
            keyboardList.add(new Keyboard("0", ""));
            keyboardList.add(new Keyboard(KEY_CLEAR, ""));
        }
        if (adapter != null) {
            adapter.setNewData(new ArrayList<>(keyboardList));
            adapter.notifyDataSetChanged();
        }
        mRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                manager.setSpanCount(mode == MODE_FULL ? 5 : 3);
            }
        });
    }

    static class Keyboard {
        private final String label;
        private final String sub;

        private Keyboard(String label, String sub) {
            this.label = label;
            this.sub = sub;
        }
    }

    private static class KeyboardAdapter extends BaseQuickAdapter<Keyboard, BaseViewHolder> {

        private KeyboardAdapter(List<Keyboard> data) {
            super(R.layout.item_keyboard, data);
        }

        @Override
        protected void convert(BaseViewHolder helper, Keyboard item) {
            helper.setText(R.id.keyName, item.label);
            TextView sub = helper.getView(R.id.keySub);
            if (TextUtils.isEmpty(item.sub)) {
                sub.setVisibility(View.GONE);
            } else {
                sub.setVisibility(View.VISIBLE);
                sub.setText(item.sub);
            }
        }
    }

    public void setOnSearchKeyListener(OnSearchKeyListener listener) {
        searchKeyListener = listener;
    }

    public void setOnModeChangeListener(OnModeChangeListener listener) {
        modeChangeListener = listener;
    }

    public interface OnSearchKeyListener {
        void onSearchKey(String key);
    }

    public interface OnModeChangeListener {
        void onModeChanged(int mode);
    }
}
