package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.JsLoader;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.adapter.SearchPosterAdapter;
import com.github.tvbox.osc.ui.dialog.RemoteDialog;
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchActivity extends BaseActivity {
    private static final String HOT_SEARCH_URL = "https://movie.douban.com/j/search_subjects?type=tv&tag=%E7%83%AD%E9%97%A8&sort=recommend&page_limit=20&page_start=0";
    private static final int SEARCH_THREAD_COUNT = 6;
    private static final int SEARCH_MAX_THREAD_COUNT = Build.VERSION.SDK_INT >= 35 ? 24 : Build.VERSION.SDK_INT >= 30 ? 18 : 12;
    private static final int SEARCH_NEXT_BATCH_SECONDS = 3;
    private static final int SEARCH_SITE_TIMEOUT_SECONDS = 15;
    private static final String[] DEFAULT_HOT_WORDS = {
            "\u5bb6\u4e1a",
            "\u4e3b\u89d2",
            "\u4f4e\u667a\u5546\u72af\u7f6a",
            "\u82cf\u8d85",
            "\u4e66\u5377\u4e00\u68a6",
            "\u7f8e\u4eba\u4f59",
            "\u85cf\u6d77\u4f20",
            "\u957f\u5b89\u7684\u8354\u679d",
            "\u5e86\u4f59\u5e74",
            "\u51e1\u4eba\u4fee\u4ed9\u4f20"
    };
    private LinearLayout llLayout;
    private LinearLayout llRecommend;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewWord;
    private TvRecyclerView featuredGrid;
    private TvRecyclerView hotPlayGrid;
    SourceViewModel sourceViewModel;
    private RemoteDialog remoteDialog;
    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvClear;
    private TextView tvRemoteSearch;
    private SearchKeyboard keyboard;
    private SearchAdapter searchAdapter;
    private SearchPosterAdapter featuredAdapter;
    private SearchPosterAdapter hotPlayAdapter;
    private PinyinAdapter wordAdapter;
    private PinyinAdapter hotWordAdapter;
    private String searchTitle = "";
    private final List<Movie.Video> highMatchVods = new ArrayList<>();
    private boolean showHighMatchResults = false;
    private TextView tvSearchCheckboxBtn;
    private TextView searchRecommendSwitch;
    private TextView keyboardModeFull;
    private TextView keyboardModeT9;

    private static HashMap<String, String> mCheckSources = null;
    private SearchCheckboxDialog mSearchCheckboxDialog = null;

    private TextView wordsSwitch;
    private boolean aggregateSearchMode;
    private boolean aggregateSearchModeInited = false;
    private static ArrayList<Movie.Video> cachedHotPlayVideos;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }


    private static Boolean hasKeyBoard;
    private static Boolean isSearchBack;
    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
        hasKeyBoard = true;
        isSearchBack = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (searchPaused) {
            resumePausedSearches();
        }
        requestSearchFocusWhenReady();
        applySearchWordMode();
        if (aggregateSearchMode) {
            loadRecommend();
        }
    }

    private void requestSearchFocusWhenReady() {
        final View focusView = hasKeyBoard || isSearchBack ? tvSearch : etSearch;
        if (focusView == null) return;
        focusView.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                focusView.requestFocus();
                focusView.requestFocusFromTouch();
            }
        });
    }

    private void initView() {
        EventBus.getDefault().register(this);
        llLayout = findViewById(R.id.llLayout);
        llRecommend = findViewById(R.id.llRecommend);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvRemoteSearch = findViewById(R.id.tvRemoteSearch);
        tvSearchCheckboxBtn = findViewById(R.id.tvSearchCheckboxBtn);
        tvClear = findViewById(R.id.tvClear);
        mGridView = findViewById(R.id.mGridView);
        keyboard = findViewById(R.id.keyBoardRoot);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        featuredGrid = findViewById(R.id.featuredGrid);
        hotPlayGrid = findViewById(R.id.hotPlayGrid);
        mGridViewWord.setHasFixedSize(true);
        wordAdapter = new PinyinAdapter();
        hotWordAdapter = new PinyinAdapter();
        wordsSwitch = findViewById(R.id.wordSwitch);
        searchRecommendSwitch = findViewById(R.id.searchRecommendSwitch);
        keyboardModeFull = findViewById(R.id.keyboardModeFull);
        keyboardModeT9 = findViewById(R.id.keyboardModeT9);
        initRecommendViews();
        applySearchWordMode();
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                startSearch(wordAdapter.getItem(position));
            }
        });
        hotWordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                startSearch(hotWordAdapter.getItem(position));
            }
        });
        mGridView.setHasFixedSize(true);
        // lite
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0)
            mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
            // with preview
        else
            mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 3));
        searchAdapter = new SearchAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    openSearchVideo(video);
                }
            }
        });
        searchRecommendSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                shuffleHotWords();
            }
        });
        keyboardModeFull.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                keyboard.setMode(SearchKeyboard.MODE_FULL);
                syncKeyboardModeButtons();
            }
        });
        keyboardModeT9.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                keyboard.setMode(SearchKeyboard.MODE_T9);
                syncKeyboardModeButtons();
            }
        });
        tvRemoteSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                remoteDialog = new RemoteDialog(mContext);
                remoteDialog.show();
            }
        });
        keyboard.setOnModeChangeListener(new SearchKeyboard.OnModeChangeListener() {
            @Override
            public void onModeChanged(int mode) {
                syncKeyboardModeButtons();
            }
        });
        syncKeyboardModeButtons();
        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                hasKeyBoard = true;
                String wd = etSearch.getText().toString().trim();
                if (!TextUtils.isEmpty(wd)) {
                    if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)){
                        Bundle bundle = new Bundle();
                        bundle.putString("title", wd);
                        jumpActivity(FastSearchActivity.class, bundle);
                    }else {
                        search(wd);
                    }
                } else {
                    Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                clearSearchInput();
            }
        });

        //软键盘

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String wd = etSearch.getText().toString().trim();
                    if (!TextUtils.isEmpty(wd)) {
                        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                            Bundle bundle = new Bundle();
                            bundle.putString("title", wd);
                            jumpActivity(FastSearchActivity.class, bundle);
                        } else {
                            hiddenImm();
                            search(wd);
                        }
                    } else {
                        Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });

        // 监听遥控器
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String wd = etSearch.getText().toString().trim();
                    if (!TextUtils.isEmpty(wd)) {
                        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                            Bundle bundle = new Bundle();
                            bundle.putString("title", wd);
                            jumpActivity(FastSearchActivity.class, bundle);
                        } else {
                            hiddenImm();
                            search(wd);
                        }
                    } else {
                        Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });
        keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
            @Override
            public void onSearchKey(String key) {
                if (SearchKeyboard.KEY_CLEAR.equals(key)) {
                    clearSearchInput();
                    return;
                }
                if (SearchKeyboard.KEY_BACKSPACE.equals(key)) {
                    String text = etSearch.getText().toString();
                    if (text.length() > 0) {
                        text = text.substring(0, text.length() - 1);
                        etSearch.setText(text);
                        if (!TextUtils.isEmpty(text)) {
                            loadRec(text);
                        } else {
                            showRecommend();
                        }
                    }
                    return;
                }
                String text = etSearch.getText().toString() + key;
                etSearch.setText(text);
                loadRec(text);
            }
        });
        setLoadSir(llLayout);
        tvSearchCheckboxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<SourceBean> searchAbleSource = ApiConfig.get().getSearchSourceBeanList();
                if (mSearchCheckboxDialog == null) {
                    mSearchCheckboxDialog = new SearchCheckboxDialog(SearchActivity.this, searchAbleSource, mCheckSources);
                }else {
                    if(searchAbleSource.size()!=mSearchCheckboxDialog.mSourceList.size()){
                        mSearchCheckboxDialog.setMSourceList(searchAbleSource);
                    }
                }
                mSearchCheckboxDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });
                mSearchCheckboxDialog.show();
            }
        });
    }

    private void startSearch(String wd) {
        if (TextUtils.isEmpty(wd)) {
            return;
        }
        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
            Bundle bundle = new Bundle();
            bundle.putString("title", wd);
            jumpActivity(FastSearchActivity.class, bundle);
        } else {
            search(wd);
        }
    }

    private boolean isAggregateSearchMode() {
        return Hawk.get(HawkConfig.FAST_SEARCH_MODE, true);
    }

    private void setAggregateHotTitle() {
        wordsSwitch.setText("搜索推荐");
        wordsSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_24));
        wordsSwitch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wordsSwitch.setLetterSpacing(0.06f);
        }
    }

    private void setNormalWordTitle() {
        wordsSwitch.setText("搜索推荐");
        wordsSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_24));
        wordsSwitch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wordsSwitch.setLetterSpacing(0.06f);
        }
    }

    private void applySearchWordMode() {
        boolean aggregateMode = isAggregateSearchMode();
        if (aggregateSearchModeInited && aggregateSearchMode == aggregateMode) {
            return;
        }
        aggregateSearchModeInited = true;
        aggregateSearchMode = aggregateMode;
        if (aggregateSearchMode) {
            llRecommend.setVisibility(View.VISIBLE);
            llLayout.setVisibility(View.GONE);
            mGridView.setVisibility(View.GONE);
            setAggregateHotTitle();
            wordsSwitch.setFocusable(false);
            wordsSwitch.setBackground(null);
            mGridViewWord.setLayoutManager(new V7GridLayoutManager(this.mContext, 4));
            mGridViewWord.setAdapter(hotWordAdapter);
            showRecommend();
        } else {
            llRecommend.setVisibility(View.GONE);
            llLayout.setVisibility(View.VISIBLE);
            if (mGridView.getVisibility() == View.GONE) {
                mGridView.setVisibility(View.INVISIBLE);
            }
            setNormalWordTitle();
            wordsSwitch.setFocusable(false);
            wordsSwitch.setBackground(null);
            mGridViewWord.setLayoutManager(new V7GridLayoutManager(this.mContext, 4));
            mGridViewWord.setAdapter(wordAdapter);
        }
    }

    private void setHotWordsData(ArrayList<String> data) {
        if (aggregateSearchMode) {
            hotWordAdapter.setNewData(data);
        } else {
            wordAdapter.setNewData(data);
        }
    }

    private void initRecommendViews() {
        featuredGrid.setHasFixedSize(true);
        featuredGrid.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        featuredAdapter = new SearchPosterAdapter();
        featuredGrid.setAdapter(featuredAdapter);
        featuredAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                openRecommendVideo(featuredAdapter.getItem(position));
            }
        });
        hotPlayGrid.setHasFixedSize(true);
        hotPlayGrid.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        hotPlayAdapter = new SearchPosterAdapter();
        hotPlayGrid.setAdapter(hotPlayAdapter);
        hotPlayAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                openRecommendVideo(hotPlayAdapter.getItem(position));
            }
        });
    }

    private void syncKeyboardModeButtons() {
        if (keyboardModeFull == null || keyboardModeT9 == null || keyboard == null) return;
        int mode = keyboard.getMode();
        keyboardModeFull.setSelected(mode == SearchKeyboard.MODE_FULL);
        keyboardModeT9.setSelected(mode == SearchKeyboard.MODE_T9);
    }

    private void clearSearchInput() {
        etSearch.setText("");
        showRecommend();
    }

    private void showRecommend() {
        if (!aggregateSearchMode) return;
        llRecommend.setVisibility(View.VISIBLE);
        llLayout.setVisibility(View.GONE);
        mGridView.setVisibility(View.GONE);
        loadRecommend();
    }

    private void loadRecommend() {
        setAggregateHotTitle();
        ArrayList<String> words = buildChipWords();
        if (!words.isEmpty()) {
            setHotWordsData(words);
        }
        loadFeaturedVideos();
        loadHotPlayVideos();
    }

    private ArrayList<String> buildChipWords() {
        ArrayList<String> words = new ArrayList<>();
        ArrayList<String> history = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
        if (history != null) {
            for (String w : history) {
                if (!TextUtils.isEmpty(w) && !words.contains(w)) {
                    words.add(w);
                }
            }
        }
        if (hots != null) {
            for (String w : hots) {
                if (!TextUtils.isEmpty(w) && !words.contains(w)) {
                    words.add(w);
                }
            }
        }
        return words;
    }

    private void shuffleHotWords() {
        if (hots == null || hots.isEmpty()) {
            useDefaultHotWords();
            return;
        }
        ArrayList<String> shuffled = new ArrayList<>(hots);
        Collections.shuffle(shuffled);
        if (shuffled.size() > 12) {
            shuffled = new ArrayList<>(shuffled.subList(0, 12));
        }
        ArrayList<String> words = new ArrayList<>();
        ArrayList<String> history = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
        if (history != null) {
            for (String w : history) {
                if (!TextUtils.isEmpty(w) && !words.contains(w)) {
                    words.add(w);
                }
            }
        }
        for (String w : shuffled) {
            if (!words.contains(w)) {
                words.add(w);
            }
        }
        setHotWordsData(words);
    }

    private void loadFeaturedVideos() {
        if (featuredAdapter == null) return;
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        String key = home == null ? null : home.getKey();
        List<Movie.Video> videos = SourceViewModel.peekHomeRecVideos(key);
        if (videos == null || videos.isEmpty()) {
            featuredAdapter.setNewData(new ArrayList<Movie.Video>());
            return;
        }
        if (videos.size() > 12) {
            videos = new ArrayList<>(videos.subList(0, 12));
        }
        featuredAdapter.setNewData(new ArrayList<>(videos));
    }

    private void loadHotPlayVideos() {
        if (hotPlayAdapter == null) return;
        if (cachedHotPlayVideos != null && !cachedHotPlayVideos.isEmpty()) {
            hotPlayAdapter.setNewData(new ArrayList<>(cachedHotPlayVideos));
        }
    }

    private void openRecommendVideo(Movie.Video video) {
        if (video == null) return;
        if (!TextUtils.isEmpty(video.sourceKey) && !TextUtils.isEmpty(video.id)) {
            openSearchVideo(video);
        } else {
            startSearch(video.name);
        }
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, new androidx.lifecycle.Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml data) {
                if (!folderLoading) return;
                folderLoading = false;
                if (data == null || data.movie == null || data.movie.videoList == null) {
                    showEmpty();
                    return;
                }
                showSuccess();
                mGridView.setVisibility(View.VISIBLE);
                searchAdapter.setNewData(data.movie.videoList);
            }
        });
    }

    private void openSearchVideo(Movie.Video video) {
        pauseSearchTasks();
        hasKeyBoard = false;
        if (TextUtils.equals("folder", video.tag)) {
            folderHistory.add(new ArrayList<>(searchAdapter.getData()));
            folderLoading = true;
            showLoading();
            sourceViewModel.getList(video.sourceKey, video.id);
            return;
        }
        isSearchBack = true;
        Bundle bundle = new Bundle();
        bundle.putString("id", video.id);
        bundle.putString("sourceKey", video.sourceKey);
        bundle.putString("title", video.name);
        bundle.putString("picture", video.pic);
        putDetailFallbackCandidates(bundle, video);
        jumpActivity(DetailActivity.class, bundle);
    }

    @Override
    public void onBackPressed() {
        if (!folderHistory.isEmpty()) {
            folderLoading = false;
            List<Movie.Video> previous = folderHistory.remove(folderHistory.size() - 1);
            showSuccess();
            mGridView.setVisibility(View.VISIBLE);
            searchAdapter.setNewData(previous);
            return;
        }
        super.onBackPressed();
    }

    /**
     * 拼音联想
     */
    private void loadRec(String key) {
        OkGo.get("https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box")
                .params("format", "json")
                .params("page_num", 0)
                .params("page_size", 20)
                .params("key", key)
                .execute(new AbsCallback() {
                    @Override
                    public void onSuccess(Response response) {
                        try {
                            ArrayList hots = new ArrayList<>();
                            String result = (String) response.body();
                            Gson gson = new Gson();
                            JsonElement json = gson.fromJson(result, JsonElement.class);
                            JsonArray groupDataArr = json.getAsJsonObject()
                                    .get("data").getAsJsonObject()
                                    .get("search_data").getAsJsonObject()
                                    .get("vecGroupData").getAsJsonArray()
                                    .get(0).getAsJsonObject()
                                    .get("group_data").getAsJsonArray();
                            for (JsonElement groupDataElement : groupDataArr) {
                                JsonObject groupData = groupDataElement.getAsJsonObject();
                                String keywordTxt = groupData.getAsJsonObject("dtReportInfo")
                                        .getAsJsonObject("reportData")
                                        .get("keyword_txt").getAsString();
                                hots.add(keywordTxt.trim());
                            }
                            wordsSwitch.setText("猜你 想搜");
                            setHotWordsData(hots);
                            mGridViewWord.smoothScrollToPosition(0);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private static ArrayList<String> hots;
    private static boolean hotWordsRequested;

    private void useDefaultHotWords() {
        ArrayList<String> data = new ArrayList<>();
        for (String word : DEFAULT_HOT_WORDS) {
            data.add(word);
        }
        cacheHotWords(data);
    }

    private void cacheHotWords(ArrayList<String> data) {
        hots = data;
        setHotWordsData(hots);
    }

    private String cleanHotWord(String title) {
        if (TextUtils.isEmpty(title)) return "";
        return title.trim().replaceAll("<|>|《|》|-", "").split(" ")[0];
    }

    private void addHotWord(ArrayList<String> data, String title) {
        String word = cleanHotWord(title);
        if (!TextUtils.isEmpty(word) && !data.contains(word)) {
            data.add(word);
        }
    }

    private void initData() {
        initCheckedSourcesForSearch();
        applySearchWordMode();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            showLoading();
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)){
                Bundle bundle = new Bundle();
                bundle.putString("title", title);
                jumpActivity(FastSearchActivity.class, bundle);
            }else {
                search(title);
            }
        }
        if (aggregateSearchMode) {
            setAggregateHotTitle();
            loadRecommend();
        } else {
            setNormalWordTitle();
        }
        if(hots!=null && !hots.isEmpty()){
            setHotWordsData(buildChipWords());
            loadHotPlayVideos();
            loadFeaturedVideos();
            return;
        }
        if (hotWordsRequested) {
            return;
        }
        hotWordsRequested = true;
        // 加载热词
        OkGo.<String>get(HOT_SEARCH_URL)
//        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
//                .params("cat", "1")
                .headers("User-Agent", "Mozilla/5.0")
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> data = new ArrayList<String>();
                            ArrayList<Movie.Video> hotPlay = new ArrayList<Movie.Video>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("subjects").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                if (obj.has("title")) {
                                    addHotWord(data, obj.get("title").getAsString());
                                }
                                Movie.Video vod = new Movie.Video();
                                if (obj.has("title")) vod.name = obj.get("title").getAsString();
                                if (obj.has("rate") && !obj.get("rate").isJsonNull()) {
                                    vod.note = obj.get("rate").getAsString();
                                    if (!TextUtils.isEmpty(vod.note)) vod.note += " 分";
                                }
                                if (obj.has("cover") && !obj.get("cover").isJsonNull()) {
                                    vod.pic = obj.get("cover").getAsString()
                                            + "@User-Agent=Mozilla/5.0"
                                            + "@Referer=https://www.douban.com/";
                                }
                                if (!TextUtils.isEmpty(vod.name)) hotPlay.add(vod);
                            }
                            cachedHotPlayVideos = hotPlay;
                            hotPlayAdapter.setNewData(new ArrayList<>(hotPlay));
                            loadFeaturedVideos();
                            if (data.isEmpty()) {
                                useDefaultHotWords();
                                return;
                            }
                            cacheHotWords(data);
                        } catch (Throwable th) {
                            th.printStackTrace();
                            useDefaultHotWords();
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        useDefaultHotWords();
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)){
                Bundle bundle = new Bundle();
                bundle.putString("title", title);
                jumpActivity(FastSearchActivity.class, bundle);
            }else{
                search(title);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    public static void setCheckedSourcesForSearch(HashMap<String,String> checkedSources) {
        mCheckSources = checkedSources;
    }

    private void search(String title) {
        cancel();
        if (remoteDialog != null) {
            remoteDialog.dismiss();
            remoteDialog = null;
        }
        showLoading();
        etSearch.setText(title);

        //写入历史记录
        HistoryHelper.setSearchHistory(title);


        this.searchTitle = title;
        mGridView.setVisibility(View.INVISIBLE);
        searchAdapter.setNewData(new ArrayList<>());
        searchResult();
    }

    private ExecutorService searchExecutorService = null;
    private ScheduledExecutorService searchTimeoutExecutor = null;
    private AtomicInteger allRunCount = new AtomicInteger(0);
    private final Set<String> pendingSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final List<SearchTask> waitingSearchTasks = Collections.synchronizedList(new ArrayList<SearchTask>());
    private final Set<String> startedSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> releasedSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final AtomicInteger searchTokenSeq = new AtomicInteger(0);
    private final AtomicInteger totalSearchCount = new AtomicInteger(0);
    private String currentSearchToken = "";
    private boolean searchPaused = false;
    private final List<Movie.Video> detailFallbackSearchResults = new ArrayList<>();
    private final List<List<Movie.Video>> folderHistory = new ArrayList<>();
    private boolean folderLoading;

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            allRunCount.set(0);
            pendingSearchKeys.clear();
            waitingSearchTasks.clear();
            startedSearchKeys.clear();
            releasedSearchKeys.clear();
            highMatchVods.clear();
            detailFallbackSearchResults.clear();
            folderHistory.clear();
            showHighMatchResults = false;
            totalSearchCount.set(0);
            currentSearchToken = String.valueOf(searchTokenSeq.incrementAndGet());
            searchPaused = false;
        }
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<SearchTask> searchTasks = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            searchTasks.add(new SearchTask(bean.getKey(), searchTitle, currentSearchToken, isBlockingSearchSource(bean)));
        }
        if (searchTasks.size() <= 0) {
            Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }
        for (SearchTask task : searchTasks) {
            pendingSearchKeys.add(task.sourceKey);
        }
        allRunCount.set(searchTasks.size());
        totalSearchCount.set(searchTasks.size());
        searchExecutorService = createSearchExecutor();
        searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        startFastSearchTasks(searchTasks);
        waitingSearchTasks.addAll(searchTasks);
        startNextSearchBatch(currentSearchToken);
    }

    private boolean matchSearchResult(String name, String searchTitle) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false;
        return TextUtils.equals(name.trim(), searchTitle.trim());
    }

    private boolean isHighMatchSearchResult(Movie.Video video) {
        return video != null && !TextUtils.isEmpty(video.name) && !TextUtils.isEmpty(searchTitle)
                && video.name.replaceAll("\\s+", "").startsWith(searchTitle.replaceAll("\\s+", ""));
    }

    private boolean shouldShowHighMatchResults() {
        if (showHighMatchResults || searchAdapter.getData().size() > 0) return false;
        int total = totalSearchCount.get();
        int threshold = Math.min(SEARCH_THREAD_COUNT, total);
        return threshold > 0 && total - allRunCount.get() >= threshold;
    }

    private void addSearchResults(List<Movie.Video> data) {
        if (data == null || data.isEmpty()) return;
        if (searchAdapter.getData().size() > 0) {
            searchAdapter.addData(data);
        } else {
            showSuccess();
            mGridView.setVisibility(View.VISIBLE);
            searchAdapter.setNewData(data);
        }
    }

    private void searchData(AbsXml absXml) {
        if (!isCurrentSearchResult(absXml)) {
            return;
        }
        String sourceKey = absXml == null ? "" : absXml.sourceKey;
        if (!markSearchFinished(sourceKey, absXml.searchToken)) {
            return;
        }
        releaseSearchSlotAndStartNext(sourceKey, absXml.searchToken);
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> exactData = new ArrayList<>();
            List<Movie.Video> highData = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                if (isHighMatchSearchResult(video)) {
                    highMatchVods.add(video);
                    highData.add(video);
                    detailFallbackSearchResults.add(video);
                }
                if (matchSearchResult(video.name, searchTitle)) {
                    exactData.add(video);
                }
            }

            if (showHighMatchResults) {
                addSearchResults(highData);
            } else if (!exactData.isEmpty()) {
                addSearchResults(exactData);
            }
        }

        if (shouldShowHighMatchResults()) {
            showHighMatchResults = true;
            addSearchResults(new ArrayList<>(highMatchVods));
        }

        finishSearchIfDone();
    }

    private void putDetailFallbackCandidates(Bundle bundle, Movie.Video selectedVideo) {
        if (bundle == null || selectedVideo == null || TextUtils.isEmpty(selectedVideo.name)) {
            return;
        }
        String title = selectedVideo.name.trim();
        ArrayList<Movie.Video> candidates = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Movie.Video video : detailFallbackSearchResults) {
            if (video == null || TextUtils.isEmpty(video.id)
                    || !TextUtils.equals(title, video.name == null ? "" : video.name.trim())) {
                continue;
            }
            String key = (video.sourceKey == null ? "" : video.sourceKey) + "|" + video.id;
            if (keys.add(key)) {
                candidates.add(video);
                if (candidates.size() >= 20) {
                    break;
                }
            }
        }
        if (!candidates.isEmpty()) {
            bundle.putSerializable(DetailActivity.EXTRA_DETAIL_FALLBACK_CANDIDATES, candidates);
        }
    }

    private void scheduleSearchAdvance(final String sourceKey, final String searchToken) {
        if (searchTimeoutExecutor == null) return;
        searchTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentSearchToken(searchToken)) return;
                if (isSearchPending(sourceKey, searchToken) && releaseSearchSlot(sourceKey, searchToken)) {
                    startNextSearchTask(searchToken);
                }
            }
        }, SEARCH_NEXT_BATCH_SECONDS, TimeUnit.SECONDS);
    }

    private void scheduleSearchTimeout(final String sourceKey, final String searchToken) {
        if (searchTimeoutExecutor == null) return;
        searchTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentSearchToken(searchToken)) return;
                if (markSearchFinished(sourceKey, searchToken)) {
                    releaseSearchSlotAndStartNext(sourceKey, searchToken);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finishSearchIfDone();
                        }
                    });
                }
            }
        }, SEARCH_SITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private boolean submitSearchTask(SearchTask task) {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return false;
        if (searchExecutorService == null || searchExecutorService.isShutdown()) return false;
        try {
            searchExecutorService.execute(task);
        } catch (RejectedExecutionException e) {
            return false;
        }
        scheduleSearchAdvance(task.sourceKey, task.searchToken);
        scheduleSearchTimeout(task.sourceKey, task.searchToken);
        return true;
    }

    private ExecutorService createSearchExecutor() {
        return new ThreadPoolExecutor(0, SEARCH_MAX_THREAD_COUNT, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    }

    private void startNextSearchBatch(String searchToken) {
        for (int i = 0; i < SEARCH_THREAD_COUNT; i++) {
            if (!startNextSearchTask(searchToken)) {
                return;
            }
        }
    }

    private boolean startNextSearchTask(String searchToken) {
        if (!isCurrentSearchToken(searchToken)) return false;
        SearchTask task = takeNextSearchTask(searchToken);
        if (task == null) {
            return false;
        }
        if (!submitSearchTask(task)) {
            startedSearchKeys.remove(task.sourceKey);
            synchronized (waitingSearchTasks) {
                waitingSearchTasks.add(0, task);
            }
            return false;
        }
        return true;
    }

    private SearchTask takeNextSearchTask(String searchToken) {
        synchronized (waitingSearchTasks) {
            while (!waitingSearchTasks.isEmpty()) {
                SearchTask task = waitingSearchTasks.remove(0);
                if (!isSearchPending(task.sourceKey, searchToken) || !startedSearchKeys.add(task.sourceKey)) {
                    continue;
                }
                return task;
            }
        }
        return null;
    }

    private void resumePausedSearches() {
        if (!searchPaused) {
            return;
        }
        searchPaused = false;
        List<String> sourceKeys = getPendingSearchKeys();
        if (sourceKeys.isEmpty()) {
            finishSearchIfDone();
            return;
        }
        currentSearchToken = String.valueOf(searchTokenSeq.incrementAndGet());
        waitingSearchTasks.clear();
        startedSearchKeys.clear();
        releasedSearchKeys.clear();
        for (String sourceKey : sourceKeys) {
            SourceBean bean = ApiConfig.get().getSource(sourceKey);
            waitingSearchTasks.add(new SearchTask(sourceKey, searchTitle, currentSearchToken, isBlockingSearchSource(bean)));
        }
        if (searchExecutorService == null || searchExecutorService.isShutdown()) {
            searchExecutorService = createSearchExecutor();
        }
        if (searchTimeoutExecutor == null || searchTimeoutExecutor.isShutdown()) {
            searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        startNextSearchBatch(currentSearchToken);
    }

    private void pauseSearchTasks() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
            searchPaused = allRunCount.get() > 0;
            if (searchPaused) {
                cancel();
                currentSearchToken = "";
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private boolean isCurrentSearchResult(AbsXml absXml) {
        return absXml != null && isCurrentSearchToken(absXml.searchToken);
    }

    private boolean isCurrentSearchToken(String searchToken) {
        return !TextUtils.isEmpty(searchToken) && searchToken.equals(currentSearchToken);
    }

    private boolean markSearchFinished(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken)) return false;
        synchronized (pendingSearchKeys) {
            if (TextUtils.isEmpty(sourceKey)) {
                return false;
            }
            if (!pendingSearchKeys.remove(sourceKey)) {
                return false;
            }
            allRunCount.set(pendingSearchKeys.size());
            return true;
        }
    }

    private boolean releaseSearchSlot(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false;
        return releasedSearchKeys.add(sourceKey);
    }

    private void releaseSearchSlotAndStartNext(String sourceKey, String searchToken) {
        if (releaseSearchSlot(sourceKey, searchToken)) {
            startNextSearchTask(searchToken);
        }
    }

    private boolean isSearchPending(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false;
        synchronized (pendingSearchKeys) {
            return pendingSearchKeys.contains(sourceKey);
        }
    }

    private boolean isBlockingSearchSource(SourceBean bean) {
        return bean == null || bean.getType() == 3;
    }

    private void startFastSearchTasks(List<SearchTask> tasks) {
        for (SearchTask task : tasks) {
            if (task.blocking) {
                continue;
            }
            if (startedSearchKeys.add(task.sourceKey)) {
                submitDirectSearchTask(task);
            }
        }
    }

    private void submitDirectSearchTask(SearchTask task) {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return;
        scheduleSearchTimeout(task.sourceKey, task.searchToken);
        try {
            sourceViewModel.getSearch(task.sourceKey, task.title, task.searchToken);
        } catch (Throwable th) {
            th.printStackTrace();
            if (markSearchFinished(task.sourceKey, task.searchToken)) {
                finishSearchIfDone();
            }
        }
    }

    private List<String> getPendingSearchKeys() {
        synchronized (pendingSearchKeys) {
            return new ArrayList<>(pendingSearchKeys);
        }
    }

    private void finishSearchIfDone() {
        if (allRunCount.get() > 0) return;
        searchPaused = false;
        if (searchAdapter.getData().size() <= 0) {
            showEmpty();
        }
        cancel();
        if (searchTimeoutExecutor != null) {
            searchTimeoutExecutor.shutdownNow();
            searchTimeoutExecutor = null;
        }
    }

    private class SearchTask implements Runnable {
        private final String sourceKey;
        private final String title;
        private final String searchToken;
        private final boolean blocking;

        private SearchTask(String sourceKey, String title, String searchToken, boolean blocking) {
            this.sourceKey = sourceKey;
            this.title = title;
            this.searchToken = searchToken;
            this.blocking = blocking;
        }

        @Override
        public void run() {
            if (!isSearchPending(sourceKey, searchToken)) return;
            try {
                sourceViewModel.getSearch(sourceKey, title, searchToken);
            } catch (Throwable th) {
                th.printStackTrace();
                if (markSearchFinished(sourceKey, searchToken)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finishSearchIfDone();
                        }
                    });
                }
            }
        }
    }


    private void cancel() {
        OkGo.getInstance().cancelTag("search");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        EventBus.getDefault().unregister(this);
    }

    private void hiddenImm()
    {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }
}
