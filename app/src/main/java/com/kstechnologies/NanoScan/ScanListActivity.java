package com.kstechnologies.NanoScan;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RelativeLayout;

import com.baoyz.swipemenulistview.SwipeMenu;
import com.baoyz.swipemenulistview.SwipeMenuCreator;
import com.baoyz.swipemenulistview.SwipeMenuItem;
import com.baoyz.swipemenulistview.SwipeMenuListView;


import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * 这个activity是应用程序的主视图
 * 它负责生成启动屏幕和主文件列表视图
 * 通过这个activity，用户能够开启扫描程序{@link NewScanActivity}，
 * 去info视图{@link InfoActivity}，或者查看曾经的扫描数据{@link GraphActivity}
 *
 */
public class ScanListActivity extends Activity {

    private ArrayList<String> csvFiles = new ArrayList<>();
    private ArrayAdapter<String> mAdapter;
    private static Context mContext;
    private SwipeMenuListView lv_csv_files;
    private SwipeMenuCreator unknownCreator = createMenu();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;



        //通过隐藏ActionBar来制造出Splash动画的效果
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.hide();
        }

        setContentView(R.layout.activity_scan_list);//设置布局

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { //系统版本大于19
//            setTranslucentStatus(true);
//        }
//        SystemBarTintManager tintManager = new SystemBarTintManager(this);
//        tintManager.setStatusBarTintEnabled(true);
//        tintManager.setStatusBarTintResource(R.color.title_green);//设置标题栏颜色，此颜色在color中声明


        //获取UI元素的引用
        final RelativeLayout mSplashLayout = (RelativeLayout) findViewById(R.id.rl_splash);
        final RelativeLayout mMainLayout = (RelativeLayout) findViewById(R.id.rl_mainLayout);

        //设置splash屏幕
        mSplashLayout.setVisibility(View.VISIBLE);
        mMainLayout.setVisibility(View.GONE);

        //设置splash动画
        Animation animSplash = AnimationUtils.loadAnimation(this, R.anim.alpha_splash);
        animSplash.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                //隐藏splash布局，并显示主布局
                mSplashLayout.setVisibility(View.GONE);
                mMainLayout.setVisibility(View.VISIBLE);

                //splash动画结束，显示action bar
                getActionBar().show();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        // 开启动画
        mSplashLayout.setAnimation(animSplash);
        animSplash.start();



    }

    /*
     * 当activity被销毁的时候调用父类的方法
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /*
     * 当activity恢复的时候，检查更新，设置文件列表，菜单和事件监听
     */
    @Override
    public void onResume() {
        super.onResume();
        //checkForCrashes();
        //checkForUpdates();

        csvFiles.clear();

        lv_csv_files = (SwipeMenuListView) findViewById(R.id.lv_csv_files);
        populateListView();

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, csvFiles);
        lv_csv_files.setAdapter(mAdapter);
        lv_csv_files.setMenuCreator(unknownCreator);

        /*
         * 设置SwipeMenuListView菜单item 的点击事件
         * 在这种情况下， 删除被选中的文件
         */
        lv_csv_files.setOnMenuItemClickListener(new SwipeMenuListView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final int position, SwipeMenu menu, int index) {

                switch (index) {
                    case 0:
                        removeFile(mAdapter.getItem(position));
                        mAdapter.remove(csvFiles.get(position));
                        lv_csv_files.setAdapter(mAdapter);
                        break;
                }
                return false;
            }
        });

        /*
         * 添加文件列表的item点击监听，如果swipe menu打开了，这个SwipeMenuListView 的item 将关闭
         */
        lv_csv_files.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                lv_csv_files.smoothOpenMenu(position);
            }
        });

        mAdapter.notifyDataSetChanged();

        /*
         * 添加文件列表的点击监听，当点击一个item时将开始这个文件的绘图activity
         */
        lv_csv_files.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent graphIntent = new Intent(mContext, GraphActivity.class);
                graphIntent.putExtra("file_name", mAdapter.getItem(i));
                startActivity(graphIntent);
            }
        });

        //通过扫描名字，获得UI引用来编辑
        EditText searchText = (EditText) findViewById(R.id.et_search);

        //Add listener to editText so that the listview is updated as the user starts typing
        //为那个搜索框添加监听来保证listview 能够随着用户键入的值而改变
        searchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mAdapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /*
     * Inflate options菜单，来使info，settings，connect图标是可视的
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 通过xml，来把3个item添加到menu中
        getMenuInflater().inflate(R.menu.menu_scan_list, menu);
        return true;
    }

    /*
     * 处理菜单选项，一共有三个选项。
     * 用户可以通过他们去info activity，settings activity，或者连接到一个Nano
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }

        else if (id == R.id.action_info) {
            Intent infoIntent = new Intent(this, InfoActivity.class);
            startActivity(infoIntent);
            return true;
        }

        else if (id == R.id.action_scan) {
            Intent graphIntent = new Intent(mContext, NewScanActivity.class);
            graphIntent.putExtra("file_name", getString(R.string.newScan));
            startActivity(graphIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Populate the stored scan listview with included files in the raw directory as well as
     * stored CSV files
     */
    public void populateListView() {
        Field[] files = R.raw.class.getFields();

        //循环raw文件夹中的每个文件
        for (Field file : files) {
            String filename = file.getName();

            csvFiles.add(filename);
        }

        String nanoExtPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        File yourDir = new File(nanoExtPath, "/");
        for (File f : yourDir.listFiles()) {
            if (f.isFile()) {
                String fileName = f.getName();
                if (fileName.contains(".csv")) {
                    //Log.d(TAG, "found:" + fileName);
                    csvFiles.add(fileName);
                }
            }
        }
    }

    /**
     * 从外部存储中删除一个文件
     * @param name 要删除文件的名字
     */
    public void removeFile(String name) {
        String nanoExtPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        File yourDir = new File(nanoExtPath, "/");
        for (File f : yourDir.listFiles()) {
            if (f.isFile()) {
                String fileName = f.getName();
                if (fileName.equals(name)) {
                    f.delete();
                }
            }
        }
    }

    //SwipeMenu：滑动菜单
    private SwipeMenuCreator createMenu() {
        return new SwipeMenuCreator() {
            @Override
            public void create(SwipeMenu menu) {

                SwipeMenuItem settingsItem = new SwipeMenuItem(
                        getApplicationContext());
                // 设置 item 背景
                settingsItem.setBackground(R.color.kst_red);
                // 设置 item 宽度
                settingsItem.setWidth(dp2px(90));
                // 设置一个图标
                //settingsItem.setIcon(android.R.drawable.ic_menu_delete);
                settingsItem.setTitleColor(ContextCompat.getColor(mContext, R.color.white));
                settingsItem.setTitleSize(18);
                settingsItem.setTitle(getResources().getString(R.string.delete));

                // add to menu
                menu.addMenuItem(settingsItem);
            }
        };
    }

    /**
     * 转换dp到px的函数
     *
     * @param dp the number of dip to convert
     * @return the dip units converted to pixels
     */
    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }


}
