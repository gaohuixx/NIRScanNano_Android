package com.kstechnologies.NanoScan;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import com.baoyz.swipemenulistview.SwipeMenu;
import com.baoyz.swipemenulistview.SwipeMenuCreator;
import com.baoyz.swipemenulistview.SwipeMenuItem;
import com.baoyz.swipemenulistview.SwipeMenuListView;
import com.gaohui.utils.ThemeManageUtil;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;

import static java.security.AccessController.getContext;

/**
 * 这个activity是应用程序的主视图
 * 它负责生成启动屏幕和主文件列表视图
 * 通过这个activity，用户能够开启扫描程序{@link NewScanActivity}，
 * 去info视图{@link InfoActivity}，或者查看曾经的扫描数据{@link GraphActivity}
 */
public class ScanListActivity extends BaseActivity {

    private ArrayList<String> csvFiles = new ArrayList<>();
    private ArrayAdapter<String> mAdapter;
    private static Context mContext;
    private DrawerLayout drawerLayout;
    private SwipeMenuListView lv_csv_files;
    private SwipeMenuCreator unknownCreator = createMenu();
    private BluetoothAdapter bluetoothAdapter;//本地蓝牙适配器

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        setContentView(R.layout.activity_scan_list);//设置布局
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();//获取本地蓝牙适配器

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        toolbar.setNavigationIcon(R.drawable.ic_menu_white_24dp);
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle("NIRScan Nano"); //4. 设置标题

        drawerLayout = (DrawerLayout) findViewById(R.id.main_drawer_layout);
        final NavigationView navigationView = (NavigationView) findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_scan:
                        Toast.makeText(mContext, "开始扫描", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.action_bluetooth:
                        openBluetooth(); //开启蓝牙
                        break;
                    case R.id.action_settings:
                        Intent settingsIntent = new Intent(mContext, SettingsActivity.class);
                        startActivity(settingsIntent); //跳转到设置页面
                        break;
                    case R.id.action_theme:
                        ThemeDialog dialog = new ThemeDialog(); //弹出主题选择对话框
                        dialog.show(getSupportFragmentManager(), "theme");
                        break;
                    case R.id.action_more:
                        Intent infoIntent = new Intent(mContext, InfoActivity.class);
                        startActivity(infoIntent); //跳转到信息界面
                        break;
                    case R.id.action_about:
                        Intent aboutIntent = new Intent(mContext, AboutActivity.class);
                        startActivity(aboutIntent); //跳转到关于界面
                        break;

                }

                drawerLayout.closeDrawer(Gravity.LEFT); // 关闭左边抽屉栏
                return true;
            }
        });

    }


    /**
     * 当activity恢复的时候，检查更新，设置文件列表，菜单和事件监听
     */
    @Override
    public void onResume() {
        super.onResume();

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
                startActivity(graphIntent);  //跳转到绘图页面
            }
        });

        //通过扫描名字，获得UI引用来编辑
        EditText searchText = (EditText) findViewById(R.id.et_search);

        //为搜索框添加监听来保证listview 能够随着用户键入的值而改变
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 把扫描按钮添加到menu中
        getMenuInflater().inflate(R.menu.menu_scan_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == 16908332) {
            drawerLayout.openDrawer(Gravity.LEFT);
            return true;
        } else if (id == R.id.action_scan) {
            Intent graphIntent = new Intent(mContext, NewScanActivity.class);
            graphIntent.putExtra("file_name", getString(R.string.newScan)); //传递参数
            startActivity(graphIntent); //跳转到扫描页面
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 用raw 目录下的文件和已经保存的CSV 文件生成listview
     */
    public void populateListView() {
        Field[] files = R.raw.class.getFields();

        //循环raw文件夹中的每个文件
        for (Field file : files) {
            String filename = file.getName();

            csvFiles.add(filename);
        }

        String nanoExtPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        File yourDir = new File(nanoExtPath, "/");//从根目录中查找
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
     *
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

    /**
     * SwipeMenu：滑动菜单
     */
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
//                settingsItem.setIcon(android.R.drawable.ic_menu_more);
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

    /**
     * 这个方法用来开启蓝牙
     */
    private void openBluetooth() {

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "本地蓝牙不可用", Toast.LENGTH_SHORT).show();
        }

        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();  //打开蓝牙，需要BLUETOOTH_ADMIN权限
            Toast.makeText(mContext, "开启蓝牙成功", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(mContext, "蓝牙已开启", Toast.LENGTH_SHORT).show();
        }


    }
}
