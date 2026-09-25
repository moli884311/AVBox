package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private boolean mShowList;
    private int defaultWidth;
    private final boolean mCompactRow;
    public ImgUtil.Style style;

    public GridAdapter(boolean showList, ImgUtil.Style style) {
        this(showList, style, false);
    }

    public GridAdapter(boolean showList, ImgUtil.Style style, boolean compactRow) {
        super(showList ? R.layout.item_list : (compactRow ? R.layout.item_grid_row : R.layout.item_grid), new ArrayList<>());
        this.mShowList = showList;
        this.mCompactRow = compactRow;
        if (style != null) {
            if (style.type.equals("list")) this.mShowList = true;
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (this.mShowList) {
            helper.setText(R.id.tvNote, item.note);
            helper.setText(R.id.tvName, item.name);
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            String pic = item.pic == null ? "" : item.pic.trim();
            if (!TextUtils.isEmpty(pic)) {
                if (ImgUtil.isBase64Image(pic)) {
                    ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
                } else {
                    ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 336), item.name);
                }
            } else {
                ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            }
            return;
        }

        TextView tvYear = helper.getView(R.id.tvYear);
        if (item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);
        if (TextUtils.isEmpty(item.note)) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if (mCompactRow) {
            newWidth = 150;
            newHeight = 200;
        } else if (style != null) {
            newWidth = defaultWidth;
            newHeight = (int) (newWidth / style.ratio);
        }
        String pic = item.pic == null ? "" : item.pic.trim();
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
            } else {
                ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight), item.name);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
        applyStyleToImage(helper, ivThumb);
    }

    private void applyStyleToImage(BaseViewHolder helper, final ImageView ivThumb) {
        float ratio;
        int baseWidth;
        if (mCompactRow) {
            ratio = 150f / 200f;
            baseWidth = 140;
        } else if (style != null) {
            ratio = style.ratio <= 0f ? ((float) ImgUtil.defaultWidth / ImgUtil.defaultHeight) : style.ratio;
            baseWidth = defaultWidth;
        } else {
            ratio = (float) ImgUtil.defaultWidth / ImgUtil.defaultHeight;
            baseWidth = 214;
        }
        int width = fitToGrid(ivThumb, baseWidth);
        int height = Math.max(1, (int) (width / ratio));
        ViewGroup container = (ViewGroup) ivThumb.getParent();
        ViewGroup.LayoutParams containerParams = container.getLayoutParams();
        containerParams.width = width;
        containerParams.height = height;
        container.setLayoutParams(containerParams);
        if (mCompactRow) {
            View name = helper.getView(R.id.tvName);
            if (name != null && name.getLayoutParams() != null) {
                name.getLayoutParams().width = width;
                name.requestLayout();
            }
        }
    }

    private int fitToGrid(View child, int fallbackWidth) {
        int width = AutoSizeUtils.mm2px(mContext, fallbackWidth);
        View current = child;
        RecyclerView recyclerView = null;
        while (current != null) {
            if (current instanceof RecyclerView) {
                recyclerView = (RecyclerView) current;
                break;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        if (recyclerView != null && recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            int spanCount = ((GridLayoutManager) recyclerView.getLayoutManager()).getSpanCount();
            int available = recyclerView.getWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight();
            if (spanCount > 0 && available > 0) {
                width = Math.min(width, available / spanCount);
            }
        }
        return Math.max(1, width);
    }
}
