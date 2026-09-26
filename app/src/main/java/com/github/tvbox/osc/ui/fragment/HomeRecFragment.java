package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.TmdbHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页「推荐」页:横向海报横排(数据来自接口首页推荐 videoList)
 */
public class HomeRecFragment extends BaseLazyFragment {
    private List<Movie.Video> videos = new ArrayList<>();
    private TvRecyclerView mGridView;
    private TextView tvEmpty;
    private GridAdapter gridAdapter;

    public static HomeRecFragment newInstance(List<Movie.Video> videoList) {
        HomeRecFragment fragment = new HomeRecFragment();
        if (videoList != null) {
            fragment.videos = new ArrayList<>(videoList);
        }
        return fragment;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_home_rec;
    }

    @Override
    protected void init() {
        mGridView = findViewById(R.id.mGridView);
        tvEmpty = findViewById(R.id.tvEmpty);
        gridAdapter = new GridAdapter(false, null, true);
        mGridView.setLayoutManager(new V7LinearLayoutManager(mContext, V7LinearLayoutManager.HORIZONTAL, false));
        mGridView.setAdapter(gridAdapter);
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
                notifyHero(position);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        gridAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video == null) return;
                Bundle bundle = new Bundle();
                bundle.putString("id", video.id);
                bundle.putString("sourceKey", video.sourceKey);
                bundle.putString("title", video.name);
                bundle.putString("picture", video.pic);
                jumpActivity(DetailActivity.class, bundle);
            }
        });
        gridAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video == null) return true;
                Bundle bundle = new Bundle();
                bundle.putString("title", video.name);
                jumpActivity(FastSearchActivity.class, bundle);
                return true;
            }
        });
        setData(videos);
    }

    public void setVideos(List<Movie.Video> videoList) {
        this.videos = videoList == null ? new ArrayList<>() : new ArrayList<>(videoList);
        if (gridAdapter != null) {
            setData(this.videos);
        }
    }

    private void setData(List<Movie.Video> videoList) {
        if (gridAdapter == null) return;
        gridAdapter.setNewData(videoList);
        boolean empty = videoList == null || videoList.isEmpty();
        if (tvEmpty != null) {
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (mGridView != null) {
            mGridView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
        if (!empty) {
            prefetchMeta(videoList);
            notifyHero(0);
        }
    }

    private void prefetchMeta(List<Movie.Video> videoList) {
        if (videoList == null) return;
        int limit = Math.min(videoList.size(), 6);
        for (int i = 0; i < limit; i++) {
            Movie.Video video = videoList.get(i);
            if (video != null) {
                TmdbHelper.prefetch(video.name, video.year);
            }
        }
    }

    private void notifyHero(int position) {
        if (gridAdapter == null) return;
        if (position < 0 || position >= gridAdapter.getData().size()) return;
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).updateHero(gridAdapter.getData().get(position));
        }
    }
}
