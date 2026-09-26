package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;

import com.github.tvbox.osc.base.BaseLazyFragment;

import java.util.List;

/**
 * @user acer
 * @date 2018/12/4
 */

public class HomePageAdapter extends FragmentPagerAdapter {
    public FragmentManager fragmentManager;
    public List<BaseLazyFragment> list;
    private final long adapterId = System.nanoTime();

    public HomePageAdapter(FragmentManager fm) {
        super(fm);
    }

    public HomePageAdapter(FragmentManager fm, List<BaseLazyFragment> list) {
        super(fm);
        this.fragmentManager = fm;
        this.list = list;
    }

    public void clear() {
        list.clear();
        notifyDataSetChanged();
    }

    public void removeAll() {
        if (list == null || fragmentManager == null) return;
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        for (BaseLazyFragment fragment : list) {
            if (fragment != null && fragment.isAdded()) {
                transaction.remove(fragment);
            }
        }
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();
        list.clear();
        notifyDataSetChanged();
    }

    @Override
    public Fragment getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return adapterId + position;
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        super.setPrimaryItem(container, position, object);
        final Fragment fragment = (Fragment) object;
        if (fragment == null) return;
        final View primaryView = fragment.getView();
        if (primaryView != null) {
            syncPageVisibility(container, primaryView);
        } else {
            container.post(new Runnable() {
                @Override
                public void run() {
                    View view = fragment.getView();
                    if (view != null) {
                        syncPageVisibility(container, view);
                    }
                }
            });
        }
    }

    private void syncPageVisibility(ViewGroup container, View primaryView) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            child.setVisibility(child == primaryView ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
