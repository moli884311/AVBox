package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int selectedIndex = 0;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    public void setSelectedIndex(int index) {
        if (selectedIndex == index) {
            return;
        }
        int old = selectedIndex;
        selectedIndex = index;
        if (old >= 0 && old < getData().size()) {
            notifyItemChanged(old);
        }
        if (index >= 0 && index < getData().size()) {
            notifyItemChanged(index);
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        helper.itemView.setSelected(helper.getLayoutPosition() == selectedIndex);
    }
}