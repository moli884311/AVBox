package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmuSourceManager;
import com.github.tvbox.osc.bean.DanmuSource;
import com.github.tvbox.osc.ui.adapter.DanmuSourceAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹幕接口源选择：自动 / 内置多源（含测速结果）/ 自定义地址。
 */
public class DanmuSourceDialog extends BaseDialog {
    private TvRecyclerView gridView;
    private DanmuSourceAdapter adapter;
    private TextView hintView;
    private ProgressBar loadingBar;
    private OnListener listener;
    private boolean probing = false;

    public DanmuSourceDialog(@NonNull @NotNull Context context) {
        super(context);
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setContentView(R.layout.dialog_danmu_source);
        setCanceledOnTouchOutside(false);
        initView();
    }

    private void initView() {
        gridView = findViewById(R.id.mGridView);
        hintView = findViewById(R.id.danmuSourceHint);
        loadingBar = findViewById(R.id.loadingBar);
        adapter = new DanmuSourceAdapter();
        gridView.setHasFixedSize(false);
        gridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        gridView.setAdapter(adapter);
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter a, View view, int position) {
                FastClickCheckUtil.check(view);
                onItemSelected(adapter.getData().get(position));
            }
        });
        findViewById(R.id.danmuClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                dismiss();
            }
        });
        findViewById(R.id.danmuProbe).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                startProbe();
            }
        });
        refreshList();
        DanmuSourceManager.refreshRemote(sources -> refreshList());
    }

    private void refreshList() {
        List<DanmuSource> data = new ArrayList<>();
        data.add(new DanmuSource(DanmuSource.KIND_AUTO, "自动（推荐）", ""));
        data.addAll(DanmuSourceManager.getSources());
        data.add(new DanmuSource(DanmuSource.KIND_CUSTOM, "自定义地址", DanmuSourceManager.getSelectedUrl()));
        adapter.setNewData(data);
        updateHint();
    }

    private void updateHint() {
        if (hintView == null) return;
        int mode = DanmuSourceManager.getMode();
        if (mode == DanmuSourceManager.MODE_CUSTOM) {
            hintView.setText("当前：自定义地址");
            return;
        }
        if (mode == DanmuSourceManager.MODE_SOURCE) {
            String name = DanmuSourceManager.getSelectedName();
            hintView.setText(TextUtils.isEmpty(name) ? "当前：指定源" : "当前：" + name);
            return;
        }
        DanmuSource fastest = DanmuSourceManager.getFastestSource();
        if (fastest == null) {
            hintView.setText("当前：自动（播放时按测速结果择优）");
        } else {
            hintView.setText("当前：自动，优先 " + fastest.getName() + " " + DanmuSourceManager.getLatency(fastest.getUrl()) + "ms");
        }
    }

    private void onItemSelected(DanmuSource item) {
        if (item == null) return;
        if (item.kind == DanmuSource.KIND_AUTO) {
            DanmuSourceManager.useAuto();
        } else if (item.kind == DanmuSource.KIND_CUSTOM) {
            openCustomDialog();
            return;
        } else {
            DanmuSourceManager.selectSource(item);
        }
        refreshList();
        notifyChanged();
    }

    private void openCustomDialog() {
        DanmuApiDialog dialog = new DanmuApiDialog(getContext());
        dialog.setOnListener(new DanmuApiDialog.OnListener() {
            @Override
            public void onChange(String api) {
                refreshList();
                notifyChanged();
            }
        });
        dialog.show();
    }

    private void startProbe() {
        if (probing) return;
        probing = true;
        loadingBar.setVisibility(View.VISIBLE);
        DanmuSourceManager.probeAll(new DanmuSourceManager.ProbeListener() {
            @Override
            public void onResult(String url, int latencyMs) {
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onFinish() {
                probing = false;
                loadingBar.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
                updateHint();
            }
        });
    }

    private void notifyChanged() {
        if (listener != null) listener.onChange();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    public interface OnListener {
        void onChange();
    }
}
