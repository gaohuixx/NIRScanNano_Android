package com.kstechnologies.NanoScan;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;//蓝牙低功耗相关
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.opencsv.CSVWriter;
import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;
import com.kstechnologies.nirscannanolibrary.SettingsManager;

/**
 * Activity controlling the Nano once it is connected
 * This activity allows a user to initiate a scan, as well as access other "connection-only"
 * settings. When first launched, the app will scan for a preferred device
 * for {@link NanoBLEService#SCAN_PERIOD}, if it is not found, then it will start another "open"
 * scan for any Nano.
 * 一旦发生了连接，这个Activity将控制这个Nano，这个Activity允许用户去初始化一个扫描，也允许用户访问其它的“只连
 * 接”设置。当这个Activity被启动的时候，app将扫描一个preferred设备，{@link NanoBLEService#SCAN_PERIOD}，
 * 如果没有发现，它将开始另一个"open"，去扫描任何Nano
 * If a preferred Nano has not been set, it will start a single scan. If at the end of scanning, a
 * Nano has not been found, a message will be presented to the user indicating and error, and the
 * activity will finish
 * 如果一个偏好Nano还没有被设置，它将开启一个单独的扫描。如果在扫描结束的时候，一个Nano还没有被发现，一个信息将
 * 被显示给用户，来告诉用户出错信息，并且这个Activity将完毕
 *
 * WARNING: This activity uses JNI function calls for communicating with the Spectrum C library, It
 * is important that the name and file structure of this activity remain unchanged, or the functions
 * will NOT work
 * 警告：这个Activity使用了JNI函数调用来完成和Spectrum（光谱）C语言库的通信，这个Activity的名字和结构保持不变
 * 是非常重要的，否则它的功能将会不好使
 *
 * @author collinmast
 */
public class NewScanActivity extends Activity {

    private static Context mContext;

    private ProgressDialog barProgressDialog;

    private ViewPager mViewPager;
    private String fileName;
    private ArrayList<String> mXValues;

    private ArrayList<Entry> mIntensityFloat;
    private ArrayList<Entry> mAbsorbanceFloat;
    private ArrayList<Entry> mReflectanceFloat;
    private ArrayList<Float> mWavelengthFloat;

    private final BroadcastReceiver scanDataReadyReceiver = new scanDataReadyReceiver();
    private final BroadcastReceiver refReadyReceiver = new refReadyReceiver();
    private final BroadcastReceiver notifyCompleteReceiver = new notifyCompleteReceiver();
    private final BroadcastReceiver scanStartedReceiver = new ScanStartedReceiver();
    private final BroadcastReceiver requestCalCoeffReceiver = new requestCalCoeffReceiver();
    private final BroadcastReceiver requestCalMatrixReceiver = new requestCalMatrixReceiver();
    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();

    private final IntentFilter scanDataReadyFilter = new IntentFilter(KSTNanoSDK.SCAN_DATA);
    private final IntentFilter refReadyFilter = new IntentFilter(KSTNanoSDK.REF_CONF_DATA);
    private final IntentFilter notifyCompleteFilter = new IntentFilter(KSTNanoSDK.ACTION_NOTIFY_DONE);
    private final IntentFilter requestCalCoeffFilter = new IntentFilter(KSTNanoSDK.ACTION_REQ_CAL_COEFF);
    private final IntentFilter requestCalMatrixFilter = new IntentFilter(KSTNanoSDK.ACTION_REQ_CAL_MATRIX);
    private final IntentFilter disconnFilter = new IntentFilter(KSTNanoSDK.ACTION_GATT_DISCONNECTED);
    private final IntentFilter scanStartedFilter = new IntentFilter(NanoBLEService.ACTION_SCAN_STARTED);

    private final BroadcastReceiver scanConfReceiver = new ScanConfReceiver();
    private final IntentFilter scanConfFilter = new IntentFilter(KSTNanoSDK.SCAN_CONF_DATA);

    private ProgressBar calProgress;
    private KSTNanoSDK.ScanResults results;
    private EditText filePrefix;
    private ToggleButton btn_os;
    private ToggleButton btn_sd;
    private ToggleButton btn_continuous;
    private Button btn_scan;

    private NanoBLEService mNanoBLEService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler mHandler;
    private static final String DEVICE_NAME = "NIRScanNano";
    private boolean connected;
    private AlertDialog alertDialog;
    private TextView tv_scan_conf;
    private String preferredDevice;
    private LinearLayout ll_conf;
    private KSTNanoSDK.ScanConfiguration activeConf;

    private Menu mMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_scan);

        mContext = this;

        calProgress = (ProgressBar) findViewById(R.id.calProgress);//进度条
        calProgress.setVisibility(View.VISIBLE);
        connected = false;

        ll_conf = (LinearLayout)findViewById(R.id.ll_conf);
        ll_conf.setClickable(false);
        ll_conf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(activeConf != null) {
                    Intent activeConfIntent = new Intent(mContext, ActiveScanActivity.class);
                    activeConfIntent.putExtra("conf",activeConf);
                    startActivity(activeConfIntent);
                }
            }
        });

        //Set the filename from the intent
        Intent intent = getIntent();
        fileName = intent.getStringExtra("file_name");

        //Set up action bar enable tab navigation
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);//设置返回箭头
            ab.setTitle(getString(R.string.new_scan));
            ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

            mViewPager = (ViewPager) findViewById(R.id.viewpager);
            mViewPager.setOffscreenPageLimit(2);

            //创建一个tab监听器，当tab被改变时被调用
            ActionBar.TabListener tl = new ActionBar.TabListener() {
                @Override
                public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
                    mViewPager.setCurrentItem(tab.getPosition());
                }

                @Override
                public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

                }

                @Override
                public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

                }
            };

            //添加3个tab，制定tab的文本和TabListener
            for (int i = 0; i < 3; i++) {
                ab.addTab(
                        ab.newTab()
                                .setText(getResources().getStringArray(R.array.graph_tab_index)[i])
                                .setTabListener(tl));
            }
        }

        //设置UI元素和事件处理器
        filePrefix = (EditText) findViewById(R.id.et_prefix);//文件名前缀
        btn_os = (ToggleButton) findViewById(R.id.btn_saveOS);//保存到安卓设备
        btn_sd = (ToggleButton) findViewById(R.id.btn_saveSD);//保存到SD卡
        btn_continuous = (ToggleButton) findViewById(R.id.btn_continuous);//继续扫描么
        btn_scan = (Button) findViewById(R.id.btn_scan);//扫描
        tv_scan_conf = (TextView) findViewById(R.id.tv_scan_conf);//扫描配置

        btn_os.setChecked(SettingsManager.getBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.saveOS, false));
        btn_sd.setChecked(SettingsManager.getBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.saveSD, false));
        btn_continuous.setChecked(SettingsManager.getBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.continuousScan, false));

        btn_sd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SettingsManager.storeBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.saveSD, b);
            }
        });

        btn_scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.prefix, filePrefix.getText().toString());
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.START_SCAN));
                calProgress.setVisibility(View.VISIBLE);
                btn_scan.setText(getString(R.string.scanning));
            }
        });

        btn_scan.setClickable(false);
        btn_scan.setBackgroundColor(ContextCompat.getColor(mContext, R.color.btn_unavailable));

        //Bind to the service. This will start it, and call the start command function
        Intent gattServiceIntent = new Intent(this, NanoBLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //注册所有需要的广播接收器
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanDataReadyReceiver, scanDataReadyFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(refReadyReceiver, refReadyFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(notifyCompleteReceiver, notifyCompleteFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(requestCalCoeffReceiver, requestCalCoeffFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(requestCalMatrixReceiver, requestCalMatrixFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfReceiver, scanConfFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanStartedReceiver, scanStartedFilter);
    }

    @Override
    public void onResume() {
        super.onResume();

        //Initialize view pager
        CustomPagerAdapter pagerAdapter = new CustomPagerAdapter(this);
        mViewPager.setAdapter(pagerAdapter);
        mViewPager.invalidate();

        tv_scan_conf.setText(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, "Column 1"));

        mViewPager.setOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        // When swiping between pages, select the
                        // corresponding tab.
                        ActionBar ab = getActionBar();
                        if (ab != null) {
                            getActionBar().setSelectedNavigationItem(position);
                        }
                    }
                });

        mXValues = new ArrayList<>();
        mIntensityFloat = new ArrayList<>();
        mAbsorbanceFloat = new ArrayList<>();
        mReflectanceFloat = new ArrayList<>();
        mWavelengthFloat = new ArrayList<>();
    }

    /*
     * 当activity 被销毁时，取消所有的broadcast receivers 注册，移除回掉并且保存所有用户偏好
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanDataReadyReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(refReadyReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(notifyCompleteReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(requestCalCoeffReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(requestCalMatrixReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfReceiver);

        mHandler.removeCallbacksAndMessages(null);

        SettingsManager.storeBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.saveOS, btn_os.isChecked());
        SettingsManager.storeBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.saveSD, btn_sd.isChecked());
        SettingsManager.storeBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.continuousScan, btn_continuous.isChecked());
    }

    /*
     * Inflate the options menu so that user actions are present
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_new_scan, menu);
        mMenu = menu;
        mMenu.findItem(R.id.action_settings).setEnabled(false);

        return true;
    }

    /*
     * Handle the selection of a menu item.
     * In this case, the user has the ability to access settings while the Nano is connected
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent configureIntent = new Intent(mContext, ConfigureActivity.class);
            startActivity(configureIntent);
        }

        if (id == android.R.id.home) {
            this.finish();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Pager enum to control tab tile and layout resource
     */
    public enum CustomPagerEnum {

        REFLECTANCE(R.string.reflectance, R.layout.page_graph_reflectance),
        ABSORBANCE(R.string.absorbance, R.layout.page_graph_absorbance),
        INTENSITY(R.string.intensity, R.layout.page_graph_intensity);

        private final int mTitleResId;
        private final int mLayoutResId;

        CustomPagerEnum(int titleResId, int layoutResId) {
            mTitleResId = titleResId;
            mLayoutResId = layoutResId;
        }

        public int getLayoutResId() {
            return mLayoutResId;
        }

    }

    /**
     * Custom pager adapter to handle changing chart data when pager tabs are changed
     */
    public class CustomPagerAdapter extends PagerAdapter {

        private final Context mContext;

        public CustomPagerAdapter(Context context) {
            mContext = context;
        }

        /*
         * 这个方法，return一个对象，这个对象表明了PagerAdapter适配器选择哪个对象放在当前的ViewPager中
         */
        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            CustomPagerEnum customPagerEnum = CustomPagerEnum.values()[position];
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(customPagerEnum.getLayoutResId(), collection, false);
            collection.addView(layout);

            if (customPagerEnum.getLayoutResId() == R.layout.page_graph_intensity) {//如果是强度视图
                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartInt);
                mChart.setDrawGridBackground(false);

                mChart.setDescription("");//设置表格的描述

                mChart.setTouchEnabled(true);//// 设置是否可以触摸

                mChart.setDragEnabled(true);// 是否可以拖拽
                mChart.setScaleEnabled(true);// 是否可以缩放

                // 如果不能，缩放缩放可能会被单独作用与X 轴，Y 轴
                mChart.setPinchZoom(true);

                // X 轴限制线
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);//虚线
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();//获取X 轴
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

                YAxis leftAxis = mChart.getAxisLeft();//获取Y 轴
                leftAxis.removeAllLimitLines(); //重置所有限制线，以避免重叠线

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(true);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);

                // 设置数据
                setData(mChart, mXValues, mIntensityFloat, ChartType.INTENSITY);

                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // 获取图例 (只有在设置了数据才可以)
                Legend l = mChart.getLegend();

                // 修改图例 ...
                l.setForm(Legend.LegendForm.LINE);
                mChart.getLegend().setEnabled(false);
                return layout;
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_absorbance) {//如果是吸收率视图

                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartAbs);
                mChart.setDrawGridBackground(false);

                // 无描述文本
                mChart.setDescription("");

                // 开启触摸手势
                mChart.setTouchEnabled(true);

                // 启用缩放和拖动
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                //如果是false，那么缩放将会是分别地在x 轴，和y 轴上
                mChart.setPinchZoom(true);

                // x 轴限制线
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); //重置所有限制线来避免重叠线

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(false);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                // 限制线在数据后面被画出，而不是在上面
                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);


                // 添加数据
                setData(mChart, mXValues, mAbsorbanceFloat, ChartType.ABSORBANCE);

                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // 获取图例，只能在设置了数据后才能这样做
                Legend l = mChart.getLegend();

                // 修改图例 ...
                l.setForm(Legend.LegendForm.LINE);
                mChart.getLegend().setEnabled(false);

                return layout;
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_reflectance) {//如果是反射率视图

                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartRef);
                mChart.setDrawGridBackground(false);

                // no description text
                mChart.setDescription("");

                // enable touch gestures
                mChart.setTouchEnabled(true);

                // enable scaling and dragging
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                // if disabled, scaling can be done on x- and y-axis separately
                mChart.setPinchZoom(true);

                // x-axis limit line
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(false);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                // limit lines are drawn behind data (and not on top)
                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);


                // add data
                setData(mChart, mXValues, mReflectanceFloat, ChartType.REFLECTANCE);

                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // get the legend (only possible after setting data)
                Legend l = mChart.getLegend();

                // modify the legend ...
                l.setForm(Legend.LegendForm.LINE);
                mChart.getLegend().setEnabled(false);
                return layout;
            } else {
                return layout;
            }
        }

        /*
         * 这个方法，是从ViewGroup中移出当前View
         */
        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);//删除页卡
        }

        /*
         * 这个方法，是获取当前窗体界面数，这里是3个
         */
        @Override
        public int getCount() {
            return CustomPagerEnum.values().length;
        }

        /**
         * 用于判断是否由对象生成界面
         */
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.reflectance);
                case 1:
                    return getString(R.string.absorbance);
                case 2:
                    return getString(R.string.intensity);
            }
            return null;
        }

    }

    private void setData(LineChart mChart, ArrayList<String> xValues, ArrayList<Entry> yValues, ChartType type) {

        //针对三种图分别都要写一段代码
        if (type == ChartType.REFLECTANCE) {
            // 创建一个数据集并给它一个类型
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // 画出像 "- - - - - -" 这样的线
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.RED);
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(Color.RED);
            set1.setDrawFilled(true);

            ArrayList<LineDataSet> dataSets = new ArrayList<LineDataSet>();
            dataSets.add(set1); // 添加数据集

            // 用这个数据集创建一个数据对象
            LineData data = new LineData(xValues, dataSets);

            // 发送数据
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(20);
        } else if (type == ChartType.ABSORBANCE) {
            // 创建一个数据集并且给它一个类型
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // 画出像 "- - - - - -" 这样的线
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.GREEN);
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(Color.GREEN);
            set1.setDrawFilled(true);

            ArrayList<LineDataSet> dataSets = new ArrayList<LineDataSet>();
            dataSets.add(set1); // add the datasets

            // create a data object with the datasets
            LineData data = new LineData(xValues, dataSets);

            // set data
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(20);
        } else if (type == ChartType.INTENSITY) {
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.BLUE);
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(Color.BLUE);
            set1.setDrawFilled(true);

            ArrayList<LineDataSet> dataSets = new ArrayList<LineDataSet>();
            dataSets.add(set1); // add the datasets

            // create a data object with the datasets
            LineData data = new LineData(xValues, dataSets);

            // set data
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(20);
        } else {
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.BLACK);
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(Color.BLACK);
            set1.setDrawFilled(true);

            ArrayList<LineDataSet> dataSets = new ArrayList<LineDataSet>();
            dataSets.add(set1); // add the datasets

            // create a data object with the datasets
            LineData data = new LineData(xValues, dataSets);

            // set data
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(10);
        }
    }

    /**
     * 自定义图表类型 枚举
     */
    public enum ChartType {
        REFLECTANCE,
        ABSORBANCE,
        INTENSITY
    }

    /**
     * 由一个Activity 启动这个BroadcastReceiver，并传过来一些数据
     * 自定义接收器用来处理扫描数据并且设置图表属性
     */
    public class scanDataReadyReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            calProgress.setVisibility(View.GONE);
            btn_scan.setText(getString(R.string.scan));
            byte[] scanData = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_DATA);//从Intent中获取数据

            String scanType = intent.getStringExtra(KSTNanoSDK.EXTRA_SCAN_TYPE);
            /*
            * 7 bytes representing the current data
            * byte0: uint8_t     year; //< years since 2000
            * byte1: uint8_t     month; /**< months since January [0-11]
            * byte2: uint8_t     day; /**< day of the month [1-31]
            * byte3: uint8_t     day_of_week; /**< days since Sunday [0-6]
            * byte3: uint8_t     hour; /**< hours since midnight [0-23]
            * byte5: uint8_t     minute; //< minutes after the hour [0-59]
            * byte6: uint8_t     second; //< seconds after the minute [0-60]
            */
            String scanDate = intent.getStringExtra(KSTNanoSDK.EXTRA_SCAN_DATE);

            KSTNanoSDK.ReferenceCalibration ref = KSTNanoSDK.ReferenceCalibration.currentCalibration.get(0);
            results = KSTNanoSDK.KSTNanoSDK_dlpSpecScanInterpReference(scanData, ref.getRefCalCoefficients(), ref.getRefCalMatrix());

            mXValues.clear();
            mIntensityFloat.clear();
            mAbsorbanceFloat.clear();
            mReflectanceFloat.clear();
            mWavelengthFloat.clear();

            int index;
            for (index = 0; index < results.getLength(); index++) {
                mXValues.add(String.format("%.02f", KSTNanoSDK.ScanResults.getSpatialFreq(mContext, results.getWavelength()[index])));
                mIntensityFloat.add(new Entry((float) results.getUncalibratedIntensity()[index], index));
                mAbsorbanceFloat.add(new Entry((-1) * (float) Math.log10((double) results.getUncalibratedIntensity()[index] / (double) results.getIntensity()[index]), index));
                mReflectanceFloat.add(new Entry((float) results.getUncalibratedIntensity()[index] / results.getIntensity()[index], index));
                mWavelengthFloat.add((float) results.getWavelength()[index]);
            }

            //还是获取最大值与最小值
            float minWavelength = mWavelengthFloat.get(0);
            float maxWavelength = mWavelengthFloat.get(0);

            for (Float f : mWavelengthFloat) {
                if (f < minWavelength) minWavelength = f;
                if (f > maxWavelength) maxWavelength = f;
            }

            float minAbsorbance = mAbsorbanceFloat.get(0).getVal();
            float maxAbsorbance = mAbsorbanceFloat.get(0).getVal();

            for (Entry e : mAbsorbanceFloat) {
                if (e.getVal() < minAbsorbance) minAbsorbance = e.getVal();
                if (e.getVal() > maxAbsorbance) maxAbsorbance = e.getVal();
            }

            float minReflectance = mReflectanceFloat.get(0).getVal();
            float maxReflectance = mReflectanceFloat.get(0).getVal();

            for (Entry e : mReflectanceFloat) {
                if (e.getVal() < minReflectance) minReflectance = e.getVal();
                if (e.getVal() > maxReflectance) maxReflectance = e.getVal();
            }

            float minIntensity = mIntensityFloat.get(0).getVal();
            float maxIntensity = mIntensityFloat.get(0).getVal();

            for (Entry e : mIntensityFloat) {
                if (e.getVal() < minIntensity) minIntensity = e.getVal();
                if (e.getVal() > maxIntensity) maxIntensity = e.getVal();
            }

            mViewPager.setAdapter(mViewPager.getAdapter());
            mViewPager.invalidate();

            if (scanType.equals("00")) {
                scanType = "Column 1";
            } else {
                scanType = "Hadamard";
            }

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("ddMMyyhhmmss", java.util.Locale.getDefault());
            String ts = simpleDateFormat.format(new Date());

            ActionBar ab = getActionBar();
            if (ab != null) {

                if (filePrefix.getText().toString().equals("")) {
                    ab.setTitle("Nano" + ts);
                } else {
                    ab.setTitle(filePrefix.getText().toString() + ts);
                }
                ab.setSelectedNavigationItem(0);
            }

            boolean saveOS = btn_os.isChecked();
            boolean continuous = btn_continuous.isChecked();

            writeCSV(ts, results, saveOS);
            writeCSVDict(ts, scanType, scanDate, String.valueOf(minWavelength), String.valueOf(maxWavelength), String.valueOf(results.getLength()), String.valueOf(results.getLength()), "1", "2.00", saveOS);

            SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.prefix, filePrefix.getText().toString());

            if (continuous) {
                calProgress.setVisibility(View.VISIBLE);
                btn_scan.setText(getString(R.string.scanning));
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.SEND_DATA));
            }
        }
    }

    /**
     * Custom receiver for returning the event that reference calibrations have been read
     *
     */
    public class refReadyReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            byte[] refCoeff = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_REF_COEF_DATA);
            byte[] refMatrix = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_REF_MATRIX_DATA);
            ArrayList<KSTNanoSDK.ReferenceCalibration> refCal = new ArrayList<>();
            refCal.add(new KSTNanoSDK.ReferenceCalibration(refCoeff, refMatrix));
            KSTNanoSDK.ReferenceCalibration.writeRefCalFile(mContext, refCal);
            calProgress.setVisibility(View.GONE);
        }
    }

    /**
     * Custom receiver for returning the event that a scan has been initiated from the button
     */
    public class ScanStartedReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            calProgress.setVisibility(View.VISIBLE);
            btn_scan.setText(getString(R.string.scanning));
        }
    }

    /**
     * Custom receiver that will request the time once all of the GATT notifications have been
     * subscribed to
     */
    public class notifyCompleteReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.SET_TIME));
        }
    }

    /**
     * Write scan data to CSV file
     * @param currentTime the current time to save
     * @param scanResults the {@link KSTNanoSDK.ScanResults} structure to save
     * @param saveOS boolean indicating if the CSV file should be saved to the OS
     */
    private void writeCSV(String currentTime, KSTNanoSDK.ScanResults scanResults, boolean saveOS) {

        String prefix = filePrefix.getText().toString();
        if (prefix.equals("")) {
            prefix = "Nano";
        }

        if (saveOS) {
            String csvOS = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + prefix + currentTime + ".csv";

            CSVWriter writer;
            try {
                writer = new CSVWriter(new FileWriter(csvOS), ',', CSVWriter.NO_QUOTE_CHARACTER);
                List<String[]> data = new ArrayList<String[]>();
                data.add(new String[]{"Wavelength,Intensity,Absorbance,Reflectance"});

                int csvIndex;
                for (csvIndex = 0; csvIndex < scanResults.getLength(); csvIndex++) {
                    double waves = scanResults.getWavelength()[csvIndex];
                    int intens = scanResults.getUncalibratedIntensity()[csvIndex];
                    float absorb = (-1) * (float) Math.log10((double) scanResults.getUncalibratedIntensity()[csvIndex] / (double) scanResults.getIntensity()[csvIndex]);
                    float reflect = (float) results.getUncalibratedIntensity()[csvIndex] / results.getIntensity()[csvIndex];
                    data.add(new String[]{String.valueOf(waves), String.valueOf(intens), String.valueOf(absorb), String.valueOf(reflect)});
                }
                writer.writeAll(data);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Write the dictionary for a CSV files
     * @param currentTime the current time to be saved
     * @param scanType the scan type to be saved
     * @param timeStamp the timestamp to be saved
     * @param spectStart the spectral range start
     * @param spectEnd the spectral range end
     * @param numPoints the number of data points
     * @param resolution the scan resolution
     * @param numAverages the number of scans to average
     * @param measTime the total measurement time
     * @param saveOS boolean indicating if this file should be saved to the OS
     */
    private void writeCSVDict(String currentTime, String scanType, String timeStamp, String spectStart, String spectEnd, String numPoints, String resolution, String numAverages, String measTime, boolean saveOS) {

        String prefix = filePrefix.getText().toString();
        if (prefix.equals("")) {
            prefix = "Nano";
        }

        if (saveOS) {
            String csv = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + prefix + currentTime + ".dict";

            CSVWriter writer;
            try {
                writer = new CSVWriter(new FileWriter(csv));
                List<String[]> data = new ArrayList<String[]>();
                data.add(new String[]{"方法", scanType});
                data.add(new String[]{"时间戳", timeStamp});
                data.add(new String[]{"光谱范围起点 (nm)", spectStart});
                data.add(new String[]{"光谱范围终点 (nm)", spectEnd});
                data.add(new String[]{"波长点数", numPoints});
                data.add(new String[]{"数字分辨率", resolution});
                data.add(new String[]{"平均扫描数", numAverages});
                data.add(new String[]{"总共测量时间 (s)", measTime});

                writer.writeAll(data);

                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 代码去管理Service 的生命周期
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            //Get a reference to the service from the service connection
            mNanoBLEService = ((NanoBLEService.LocalBinder) service).getService();

            //initialize bluetooth, if BLE is not available, then finish
            if (!mNanoBLEService.initialize()) {
                finish();
            }

            //Start scanning for devices that match DEVICE_NAME
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if(mBluetoothLeScanner == null){
                finish();
                Toast.makeText(NewScanActivity.this, "请先开启蓝牙并再次尝试", Toast.LENGTH_SHORT).show();
            }
            mHandler = new Handler();
            if (SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null) != null) {
                preferredDevice = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null);
                scanPreferredLeDevice(true);
            } else {
                scanLeDevice(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mNanoBLEService = null;
        }
    };

    /**
     * Callback function for Bluetooth scanning. This function provides the instance of the
     * Bluetooth device {@link BluetoothDevice} that was found, it's rssi, and advertisement
     * data (scanRecord).
     * <p>
     * When a Bluetooth device with the advertised name matching the
     * string DEVICE_NAME {@link NewScanActivity#DEVICE_NAME} is found, a call is made to connect
     * to the device. Also, the Bluetooth should stop scanning, even if
     * the {@link NanoBLEService#SCAN_PERIOD} has not expired
     */
    private final ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null) {
                if (device.getName().equals(DEVICE_NAME)) {
                    mNanoBLEService.connect(device.getAddress());
                    connected = true;
                    scanLeDevice(false);
                }
            }
        }
    };

    /**
     * Callback function for preferred Nano scanning. This function provides the instance of the
     * Bluetooth device {@link BluetoothDevice} that was found, it's rssi, and advertisement
     * data (scanRecord).
     * <p>
     * When a Bluetooth device with the advertised name matching the
     * string DEVICE_NAME {@link NewScanActivity#DEVICE_NAME} is found, a call is made to connect
     * to the device. Also, the Bluetooth should stop scanning, even if
     * the {@link NanoBLEService#SCAN_PERIOD} has not expired
     */
    private final ScanCallback mPreferredLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null) {
                if (device.getName().equals(DEVICE_NAME)) {
                    if (device.getAddress().equals(preferredDevice)) {
                        mNanoBLEService.connect(device.getAddress());
                        connected = true;
                        scanPreferredLeDevice(false);
                    }
                }
            }
        }
    };

    /**
     * Scans for Bluetooth devices on the specified interval {@link NanoBLEService#SCAN_PERIOD}.
     * This function uses the handler {@link NewScanActivity#mHandler} to delay call to stop
     * scanning until after the interval has expired. The start and stop functions take an
     * LeScanCallback parameter that specifies the callback function when a Bluetooth device
     * has been found {@link NewScanActivity#mLeScanCallback}
     *
     * @param enable Tells the Bluetooth adapter {@link KSTNanoSDK#mBluetoothAdapter} if
     *               it should start or stop scanning
     */
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mBluetoothLeScanner != null) {
                        mBluetoothLeScanner.stopScan(mLeScanCallback);
                        if (!connected) {
                            notConnectedDialog();
                        }
                    }
                }
            }, NanoBLEService.SCAN_PERIOD);
            if(mBluetoothLeScanner != null) {
                mBluetoothLeScanner.startScan(mLeScanCallback);
            }else{
                finish();
                Toast.makeText(NewScanActivity.this, "请先开启蓝牙并再次尝试", Toast.LENGTH_SHORT).show();
            }
        } else {
            mBluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }

    /**
     * Scans for preferred Nano devices on the specified interval {@link NanoBLEService#SCAN_PERIOD}.
     * This function uses the handler {@link NewScanActivity#mHandler} to delay call to stop
     * scanning until after the interval has expired. The start and stop functions take an
     * LeScanCallback parameter that specifies the callback function when a Bluetooth device
     * has been found {@link NewScanActivity#mPreferredLeScanCallback}
     *
     * @param enable Tells the Bluetooth adapter {@link KSTNanoSDK#mBluetoothAdapter} if
     *               it should start or stop scanning
     */
    private void scanPreferredLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanner.stopScan(mPreferredLeScanCallback);
                    if (!connected) {

                        scanLeDevice(true);
                    }
                }
            }, NanoBLEService.SCAN_PERIOD);
            mBluetoothLeScanner.startScan(mPreferredLeScanCallback);
        } else {
            mBluetoothLeScanner.stopScan(mPreferredLeScanCallback);
        }
    }

    /**
     * Dialog that tells the user that a Nano is not connected. The activity will finish when the
     * user selects ok
     */
    private void notConnectedDialog() {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle(mContext.getResources().getString(R.string.not_connected_title));
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage(mContext.getResources().getString(R.string.not_connected_message));

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                alertDialog.dismiss();
                finish();
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }

    /**
     * Custom receiver for receiving calibration coefficient data.
     */
    public class requestCalCoeffReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE, 0);
            Boolean size = intent.getBooleanExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE_PACKET, false);
            if (size) {
                calProgress.setVisibility(View.INVISIBLE);
                barProgressDialog = new ProgressDialog(NewScanActivity.this);

                barProgressDialog.setTitle(getString(R.string.dl_ref_cal));
                barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                barProgressDialog.setProgress(0);
                barProgressDialog.setMax(intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE, 0));
                barProgressDialog.setCancelable(false);
                barProgressDialog.show();
            } else {
                barProgressDialog.setProgress(barProgressDialog.getProgress() + intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE, 0));
            }
        }
    }

    /**
     * Custom receiver for receiving calibration matrix data. When this receiver action complete, it
     * will request the active configuration so that it can be displayed in the listview
     */
    public class requestCalMatrixReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0);
            Boolean size = intent.getBooleanExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE_PACKET, false);
            if (size) {
                barProgressDialog.dismiss();
                barProgressDialog = new ProgressDialog(NewScanActivity.this);

                barProgressDialog.setTitle(getString(R.string.dl_cal_matrix));
                barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                barProgressDialog.setProgress(0);
                barProgressDialog.setMax(intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0));
                barProgressDialog.setCancelable(false);
                barProgressDialog.show();
            } else {
                barProgressDialog.setProgress(barProgressDialog.getProgress() + intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0));
            }
            if (barProgressDialog.getProgress() == barProgressDialog.getMax()) {

                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.REQUEST_ACTIVE_CONF));
            }
        }
    }

    /**
     * Custom receiver for handling scan configurations
     */
    private class ScanConfReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            byte[] smallArray = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_DATA);
            byte[] addArray = new byte[smallArray.length * 3];
            byte[] largeArray = new byte[smallArray.length + addArray.length];

            System.arraycopy(smallArray, 0, largeArray, 0, smallArray.length);
            System.arraycopy(addArray, 0, largeArray, smallArray.length, addArray.length);

            Log.w("_JNI","largeArray Size: "+ largeArray.length);
            KSTNanoSDK.ScanConfiguration scanConf = KSTNanoSDK.KSTNanoSDK_dlpSpecScanReadConfiguration(intent.getByteArrayExtra(KSTNanoSDK.EXTRA_DATA));
            //KSTNanoSDK.ScanConfiguration scanConf = KSTNanoSDK.KSTNanoSDK_dlpSpecScanReadConfiguration(largeArray);

            activeConf = scanConf;

            barProgressDialog.dismiss();
            btn_scan.setClickable(true);
            btn_scan.setBackgroundColor(ContextCompat.getColor(mContext, R.color.kst_red));
            mMenu.findItem(R.id.action_settings).setEnabled(true);

            SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, scanConf.getConfigName());
            tv_scan_conf.setText(scanConf.getConfigName());


        }
    }
    /**
     * Broadcast Receiver handling the disconnect event. If the Nano disconnects,
     * this activity should finish so that the user is taken back to the {@link ScanListActivity}
     * and display a toast message
     */
    public class DisconnReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(mContext, R.string.nano_disconnected, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
