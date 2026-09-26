package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 搜索页"精选推荐 / 热门播放"横排海报。
 */
public class SearchPosterAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    public SearchPosterAdapter() {
        super(R.layout.item_search_poster, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (item == null) return;
        helper.setText(R.id.tvName, item.name);
        TextView note = helper.getView(R.id.tvNote);
        if (TextUtils.isEmpty(item.note)) {
            note.setVisibility(View.GONE);
        } else {
            note.setVisibility(View.VISIBLE);
            note.setText(item.note);
        }
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        String pic = item.pic == null ? "" : item.pic.trim();
        if (!TextUtils.isEmpty(pic)) {
            ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 8),
                    AutoSizeUtils.mm2px(mContext, 120), AutoSizeUtils.mm2px(mContext, 160), item.name);
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
    }
}
