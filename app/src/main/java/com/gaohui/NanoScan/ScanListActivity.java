package com.gaohui.NanoScan;

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
import android.view.WindowManager;
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


/**
 * 这个activity是应用程序的主视图
 * 它负责生成启动屏幕和主文件列表视图
 * 通过这个activity，用户能够开启扫描程序{@link NewScanActivity}，
 * 去info视图{@link InfoActivity}，或者查看曾经的扫描数据{@link GraphActivity}
 */
public class ScanListActivity extends BaseActivity {

    private static final String TAG = "gaohui";
    private ArrayList<String> csvFiles = new ArrayList<>();
    private ArrayAdapter<String> mAdapter;
    private static Context mContext;
    private DrawerLayout drawerLayout;
    private SwipeMenuListView lv_csv_files;
    private NavigationView navigationView;
    private SwipeMenuCreator unknownCreator = createMenu();
    private BluetoothAdapter bluetoothAdapter;//本地蓝牙适配器
    private boolean bluetoothState = false;//蓝牙状态
    private String csvPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/csv";
    private String dictPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/dict";

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
        populateListView(); //往csvFiles 集合里面添加文件

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, csvFiles);//生成适配器
        lv_csv_files.setAdapter(mAdapter);//设置适配器
        lv_csv_files.setMenuCreator(unknownCreator);//创建侧滑菜单

        /*
         * 设置SwipeMenuListView菜单item 的点击事件
         * 在这种情况下， 删除被选中的文件
         */
        lv_csv_files.setOnMenuItemClickListener(new SwipeMenuListView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final int position, SwipeMenu menu, int index) {

                switch (index) {
                    case 0:
                        renameDialog(position);
                        break;
                    case 1:
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
                if (i < 10)//0-9 是示例文件，不用加.csv 后缀
                    graphIntent.putExtra("file_name", mAdapter.getItem(i));
                else
                    graphIntent.putExtra("file_name", mAdapter.getItem(i) + ".csv");
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
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.DialogTheme);
        builder.setMessage("确认删除这条记录吗？");
        builder.setTitle("警告");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                boolean state = removeFile(csvFiles.get(position)); //删除文件
                if (!state){
                    Toast.makeText(mContext, "示例文件不允许删除", Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.i(TAG, "csvFiles.get(position): " + csvFiles.get(position));
                Log.i(TAG, "mAdapter.getItem(position): " + mAdapter.getItem(position));
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
        builder.setCancelable(false).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 把扫描按钮添加到menu中
        getMenuInflater().inflate(R.menu.menu_scan_list, menu);
        return true;
    }

    /**
     * 为toolbar 上的按钮添加监听
     */
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
        Field[] files = R.raw.class.getFields();//这里是获取R.raw 类的所有字段，一个字段就是一个文件名

        //循环raw文件夹中的每个文件
        for (Field file : files) {
            String filename = file.getName();
            csvFiles.add(filename);
        }

        File yourDir = new File(csvPath);//从根目录中查找
        if(!yourDir.exists())
            yourDir.mkdirs();
        for (File f : yourDir.listFiles()) {
            if (f.isFile()) {
                String fileName = f.getName();
                if (fileName.endsWith(".csv")) {
                    csvFiles.add(fileName.substring(0,fileName.length()-4));
                }
            }
        }
    }

    /**
     * 从外部存储中删除一个文件
     *
     * @param name 要删除文件的名字
     */
    public boolean removeFile(String name) {
        File csvfile = new File(csvPath, name + ".csv");
        if (csvfile.exists()) //删除csv 文件
            csvfile.delete();
        else
            return false;//如果这个csv 文件不存在，那么就说明它是示例文件

        File dictfile = new File(dictPath, name + ".dict");
        if (dictfile.exists()) //删除dict 文件
            dictfile.delete();

        return true;
    }

    /**
     * SwipeMenu：滑动菜单
     */
    private SwipeMenuCreator createMenu() {
        return new SwipeMenuCreator() {
            @Override
            public void create(SwipeMenu menu) {

                SwipeMenuItem renameItem = new SwipeMenuItem(
                        getApplicationContext());
                renameItem.setBackground(ThemeManageUtil.getCurrentThemeColorInXML());// 设置 item 背景
                renameItem.setWidth(dp2px(80));// 设置 item 宽度
                renameItem.setTitleColor(ContextCompat.getColor(mContext, R.color.white));// 设置一个图标
                renameItem.setTitleSize(18);
                renameItem.setTitle("重命名");
                menu.addMenuItem(renameItem);// 添加item 到menu


                SwipeMenuItem deleteItem = new SwipeMenuItem(getApplicationContext());
                deleteItem.setBackground(R.color.kst_red); // 设置 item 背景
                deleteItem.setWidth(dp2px(80)); // 设置 item 宽度
                deleteItem.setTitleColor(ContextCompat.getColor(mContext, R.color.white));// 设置一个图标
                deleteItem.setTitleSize(18);
                deleteItem.setTitle(getResources().getString(R.string.delete));
                menu.addMenuItem(deleteItem);// 添加item 到menu


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
                        return true;
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

    /**
     * 这个广播接收器用来接收蓝牙状态改变广播
     */
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

    /** 文件重命名
     * @param path 文件目录
     * @param oldname  原来的文件名
     * @param newname 新文件名
     *
     * @return 状态
     */
    private int renameFile(String path,String oldname,String newname){
        if(!oldname.equals(newname)){//新的文件名和以前文件名不同时,才有必要进行重命名
            File oldfile=new File(path+"/"+oldname);
            File newfile=new File(path+"/"+newname);
            if(!oldfile.exists()){
                return 1;//重命名失败，原因：重命名文件不存在
            }
            if(newfile.exists()){//若在该目录下已经有一个文件和新文件名相同，则不允许重命名
                Toast.makeText(mContext, "该名称已存在", Toast.LENGTH_SHORT).show();
                return 2;//重命名失败，原因：新名称已经存在
            }
            else{
                oldfile.renameTo(newfile);
                return 0;//重命名成功
            }
        }
        return 3;//重命名失败，原因：新的文件名和以前文件名相同
    }

    /**
     * 弹出重命名对话框
     */
    private void renameDialog(final int position){
        final EditText et = new EditText(this);
        et.setPadding(60, 30, 60, 30);
        et.setWidth(800);
        et.setText(csvFiles.get(position));
        et.setSelection(csvFiles.get(position).length());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE );

        new AlertDialog.Builder(this, R.style.DialogTheme).setTitle("重命名")
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String input = et.getText().toString();
                        if (input.equals("")) {
                            Toast.makeText(getApplicationContext(), "不能为空！" + input, Toast.LENGTH_SHORT).show();
                        }
                        else {
                            String oldName = csvFiles.get(position);
                            String newName = et.getText().toString();

                            int state = renameFile(csvPath, oldName + ".csv", newName + ".csv");

                            if (state == 1){
                                Toast.makeText(mContext, "示例文件不允许重命名", Toast.LENGTH_SHORT).show();
                                return;
                            }else if (state == 2){
                                Toast.makeText(mContext, "此名称已经存在", Toast.LENGTH_SHORT).show();
                                return;
                            }else if (state == 3){
                                Toast.makeText(mContext, "名称未作修改", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            renameFile(dictPath, oldName + ".dict", newName + ".dict");
                            csvFiles.set(position, newName);//将数组相应位置的名称该成新名字
                            lv_csv_files.setAdapter(mAdapter); //刷新列表

                            Toast.makeText(mContext, "修改成功", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null).setCancelable(false)
                .show();
    }

}
