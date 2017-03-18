package com.kstechnologies.NanoScan;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.menu.MenuView;
import android.support.v7.widget.Toolbar;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
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

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;


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
    private NavigationView navigationView;
    private SwipeMenuCreator unknownCreator = createMenu();
    private BluetoothAdapter bluetoothAdapter;//本地蓝牙适配器
    private boolean bluetoothState = false;//蓝牙状态

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
        navigationView = (NavigationView) findViewById(R.id.navigation_view);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(bluetoothStateChangeReceiver, filter);

    }


    /**
     * 当activity恢复的时候，检查更新，设置文件列表，菜单和事件监听
     */
    @Override
    public void onResume() {
        super.onResume();
        bluetoothState = bluetoothAdapter.isEnabled();//刷新蓝牙状态
        refreshNavigationListen();//刷新导航栏的监听
        refreshBluetoothItemStyle();//刷新蓝牙选项样式

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
                        confirmDialog(position);
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

    /**
     * 点击删除的时候弹出这个确认对话框
     * @param position 删除第几个
     */
    private void confirmDialog(final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage("确认删除这条记录吗？");
        builder.setTitle("警告");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                removeFile(mAdapter.getItem(position)); //删除文件
                mAdapter.remove(csvFiles.get(position)); //将相应的条目从列表项中移除
                lv_csv_files.setAdapter(mAdapter); //刷新列表
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
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

    @Override
    protected void onDestroy() {
        try {
            mContext.unregisterReceiver(bluetoothStateChangeReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
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

        String nanoExtPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/csv";
        File yourDir = new File(nanoExtPath, "/");//从根目录中查找
        if(!yourDir.exists())
            yourDir.mkdirs();
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
        //此时获取到的nanoExtPath==“/storage/emulated/0”
        String nanoExtPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/csv";
        File yourDir = new File(nanoExtPath, "/");
        if(!yourDir.exists())
            yourDir.mkdirs();
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
            boolean state = bluetoothAdapter.enable();  //打开蓝牙，需要BLUETOOTH_ADMIN权限
            if(state){
                Toast.makeText(mContext, "开启蓝牙成功", Toast.LENGTH_SHORT).show();
                bluetoothState = true;
            }
            else
                Toast.makeText(mContext, "开启蓝牙失败", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(mContext, "蓝牙已开启", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 这个方法用来关闭蓝牙
     */
    private void closeBluetooth() {

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "本地蓝牙不可用", Toast.LENGTH_SHORT).show();
        }

        if (bluetoothAdapter.isEnabled()) {
            boolean state = bluetoothAdapter.disable();  //打开蓝牙，需要BLUETOOTH_ADMIN权限
            if(state){
                Toast.makeText(mContext, "关闭蓝牙成功", Toast.LENGTH_SHORT).show();
                bluetoothState = false;
            }

            else
                Toast.makeText(mContext, "关闭蓝牙失败", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(mContext, "蓝牙已关闭", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 这个方法用来刷新蓝牙按钮的监听
     */
    private void refreshNavigationListen(){
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_scan:
                        Intent graphIntent = new Intent(mContext, NewScanActivity.class);
                        startActivity(graphIntent); //跳转到扫描页面
                        break;
                    case R.id.action_bluetooth:
                        if (bluetoothState)
                            closeBluetooth(); //关闭蓝牙
                        else
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
     * 这个方法用来刷新蓝牙按钮的样式
     */
    private void refreshBluetoothItemStyle(){
        Menu menu = navigationView.getMenu();
        MenuItem bluetoothItem = menu.findItem(R.id.action_bluetooth);
        if (bluetoothState){
            bluetoothItem.setTitle("关闭蓝牙");
            bluetoothItem.setIcon(R.drawable.ic_bluetooth_opened);
        }
        else{
            bluetoothItem.setTitle("开启蓝牙");
            bluetoothItem.setIcon(R.drawable.ic_bluetooth_closed);
        }
    }


    private BroadcastReceiver bluetoothStateChangeReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (blueState == BluetoothAdapter.STATE_ON || blueState == BluetoothAdapter.STATE_OFF){
                bluetoothState = bluetoothAdapter.isEnabled();//刷新蓝牙状态
                refreshNavigationListen();//刷新导航栏的监听
                refreshBluetoothItemStyle();//刷新蓝牙选项样式
            }
        }
    };


}
