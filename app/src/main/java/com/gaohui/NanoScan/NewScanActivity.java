package com.gaohui.NanoScan;

import android.app.AlertDialog;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.design.widget.TabLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
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

import com.gaohui.utils.NanoUtil;
import com.gaohui.utils.ThemeManageUtil;
import com.gaohui.utils.TimeUtil;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
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
 * 一旦发生了连接，这个Activity将控制这个Nano，这个Activity允许用户去初始化一个扫描，也允许用户访问其它的“只连
 * 接”设置。当这个Activity被启动的时候，app将扫描一个preferred设备，{@link NanoBLEService#SCAN_PERIOD}，
 * 如果没有发现，它将开始另一个"open"，去扫描任何Nano
 * 如果一个偏好Nano还没有被设置，它将开启一个单独的扫描。如果在扫描结束的时候，一个Nano还没有被发现，一个信息将
 * 被显示给用户，来告诉用户出错信息，并且这个Activity将完毕
 *
 * 警告：这个Activity使用了JNI函数调用来完成和Spectrum（光谱）C语言库的通信，这个Activity的名字和结构保持不变
 * 是非常重要的，否则它的功能将会不好使
 *
 * 这个界面也能显示图表，但是它是又单独写了一遍，和{@link GraphActivity} 没关系
 *
 * 几个接收器的执行顺序：
 * {@link notifyCompleteReceiver}   当连接上Nano 后触发，触发器后去设置时间{@link notifyCompleteReceiver}
 * {@link requestCalCoeffReceiver}  当接收校正系数时触发，只是用来更新进度条
 * {@link requestCalMatrixReceiver} 当接收到校正矩阵时，只是用来更新进度条
 * {@link refReadyReceiver}         当校正系数和校正矩阵都接收完成后触发，用来将参考校正数据保存到本地，不过并没有用到
 *
 * {@link ScanStartedReceiver}      当点击扫描按钮时或者按Nano上的扫描键时触发，用来显示一个圆形进度条和按钮文字变为“扫描中...”
 * {@link scanDataReadyReceiver}    当扫描完成后触发，这个干的事就比较多了，详情见下面的注释
 *
 * {@link DisconnReceiver}          当连接断开时触发，显示一个Toast，并震动1s
 *
 * @author collinmast,gaohui
 */
public class NewScanActivity extends BaseActivity {

    private static Context mContext;
    private static final String TAG = "gaohui";

    private ProgressDialog barProgressDialog;

    private ViewPager mViewPager;
    private String fileName;
    private ArrayList<String> mXValues;

    private ArrayList<Entry> mIntensityFloat;
    private ArrayList<Entry> mAbsorbanceFloat;
    private ArrayList<Entry> mReflectanceFloat;
    private ArrayList<Float> mWavelengthFloat;

    private final BroadcastReceiver notifyCompleteReceiver = new notifyCompleteReceiver();
    private final BroadcastReceiver requestCalCoeffReceiver = new requestCalCoeffReceiver();
    private final BroadcastReceiver requestCalMatrixReceiver = new requestCalMatrixReceiver();
    private final BroadcastReceiver refReadyReceiver = new refReadyReceiver();
    private final BroadcastReceiver scanStartedReceiver = new ScanStartedReceiver();
    private final BroadcastReceiver scanDataReadyReceiver = new scanDataReadyReceiver();
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
    private boolean connected = false;
    private AlertDialog alertDialog;
    private TextView tv_scan_conf;
    private String preferredDevice;
    private LinearLayout ll_conf;
    private KSTNanoSDK.ScanConfiguration activeConf;//在这整个页面中代表着当前被选中的扫描配置

    private Menu mMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_scan);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar ab = this.getSupportActionBar(); // 3. 正常获取ActionBar

        mContext = this;

        calProgress = (ProgressBar) findViewById(R.id.calProgress);//进度条
        calProgress.setVisibility(View.VISIBLE);
        connected = false;


        //从intent 中获取文件名并设置
        Intent intent = getIntent();
        fileName = intent.getStringExtra("file_name");

        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);//设置返回箭头
            ab.setTitle(getString(R.string.new_scan));

            mViewPager = (ViewPager) findViewById(R.id.viewpager);
            mViewPager.setOffscreenPageLimit(2);


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

        btn_scan.setClickable(false);//此时按钮不可用

//        btn_scan.setBackgroundColor(ContextCompat.getColor(mContext, R.color.btn_unavailable));

        //绑定到service 。这将开启这个service，并且会调用start command 方法
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
        Log.i(TAG, "onResume()");
        super.onResume();

        //初始化 view pager
        CustomPagerAdapter pagerAdapter = new CustomPagerAdapter(this);
        mViewPager.setAdapter(pagerAdapter);
        mViewPager.invalidate();

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs); //获取TabLayout
        tabLayout.setupWithViewPager(mViewPager); //将TabLayout 和ViewPager 关联

        //他把当前配置的名称存在了数据库里
        tv_scan_conf.setText(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, "Column 1"));


        mXValues = new ArrayList<>();
        mIntensityFloat = new ArrayList<>();
        mAbsorbanceFloat = new ArrayList<>();
        mReflectanceFloat = new ArrayList<>();
        mWavelengthFloat = new ArrayList<>();
    }

    /**
     * 当activity 被销毁时，取消所有的broadcast receivers 注册，移除回掉并且保存所有用户偏好
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
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

    /**
     * 添加右上角的配置按钮
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu()");
        getMenuInflater().inflate(R.menu.menu_new_scan, menu);
        mMenu = menu;
        mMenu.findItem(R.id.action_config).setEnabled(false);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "onOptionsItemSelected()");

        int id = item.getItemId();

        if (id == R.id.action_config) {
            Intent configureIntent = new Intent(mContext, ConfigureActivity.class);
            startActivity(configureIntent);
        }else if (id == android.R.id.home) {
            if (connected == true){
                confirmDialog();
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vibrator.vibrate(500);//震动1s
            }else{
                finish();
            }
        }

        return true;
    }

    /**
     * 点击删除的时候弹出这个确认对话框
     */
    private void confirmDialog() {
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(mContext, R.style.DialogTheme);
        builder.setMessage("此操作将使连接断开！是否继续？");
        builder.setTitle("警告");
        builder.setPositiveButton("是", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Toast.makeText(mContext, R.string.nano_disconnected, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        builder.setNegativeButton("否", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setCancelable(false).show();
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
     * 定义一个adapter 用来处理页面内容
     */
    public class CustomPagerAdapter extends PagerAdapter {

        private final Context mContext;

        public CustomPagerAdapter(Context context) {
            mContext = context;
        }

        /*
         * 这个方法，return一个对象，这个对象表明了PagerAdapter 适配器选择哪个对象放在当前的ViewPager中
         */
        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            CustomPagerEnum customPagerEnum = CustomPagerEnum.values()[position];
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(customPagerEnum.getLayoutResId(), collection, false);
            collection.addView(layout);
            LineChart mChart = null;

            if (customPagerEnum.getLayoutResId() == R.layout.page_graph_intensity) {
                mChart = (LineChart) layout.findViewById(R.id.lineChartInt);
                setData(mChart, mXValues, mIntensityFloat);
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_absorbance) {
                mChart = (LineChart) layout.findViewById(R.id.lineChartAbs);
                setData(mChart, mXValues, mAbsorbanceFloat);
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_reflectance) {
                mChart = (LineChart) layout.findViewById(R.id.lineChartRef);
                setData(mChart, mXValues, mReflectanceFloat);
            }

            mChart.setDrawGridBackground(false);
            mChart.setDescription("");//设置表格的描述
            mChart.setTouchEnabled(true);//设置是否可以触摸
            mChart.setDragEnabled(true);//设置是否可以拖拽
            mChart.setScaleEnabled(true);//设置是否可以缩放
            mChart.setPinchZoom(true);// 如果为false，缩放缩放可能会被单独作用与X 轴，Y 轴

            // X 轴限制线
            LimitLine llXAxis = new LimitLine(10f, "Index 10");
            llXAxis.setLineWidth(4f);
            llXAxis.enableDashedLine(10f, 10f, 0f);
            llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
            llXAxis.setTextSize(10f);

            XAxis xAxis = mChart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.removeAllLimitLines(); //重置所有限制线，以避免重叠线
            leftAxis.setStartAtZero(false); //纵坐标从0 开始绘制
            leftAxis.enableGridDashedLine(10f, 10f, 0f);
            leftAxis.setDrawLimitLinesBehindData(true);

            mChart.setAutoScaleMinMaxEnabled(true);
            mChart.getAxisRight().setEnabled(false);
            mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

            Legend l = mChart.getLegend();
            l.setForm(Legend.LegendForm.LINE);

            if (customPagerEnum.getLayoutResId() == R.layout.page_graph_intensity) {
                leftAxis.setStartAtZero(true);//强度图从0 开始绘制
            }

            return layout;
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

        /*
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

    /**
     * 为一个指定的图表设置X 轴，Y 轴数据
     * @param mChart 为那个图标更新数据
     * @param xValues 横坐标是String类型
     * @param yValues 纵坐标是数字类型
     */
    private void setData(LineChart mChart, ArrayList<String> xValues, ArrayList<Entry> yValues) {

        int themeColor = ThemeManageUtil.getCurrentThemeColor();

        LineDataSet lineDataSet = new LineDataSet(yValues, fileName);

        //设置线型为这样的 "- - - - - -"
        lineDataSet.enableDashedLine(10f, 5f, 0f);
        lineDataSet.enableDashedHighlightLine(10f, 5f, 0f);
        lineDataSet.setColor(themeColor); //设置线的颜色
        lineDataSet.setCircleColor(themeColor); //设置圆圈的颜色
        lineDataSet.setLineWidth(1f); //设置线宽
        lineDataSet.setCircleSize(3f); //设置圆圈大小
        lineDataSet.setDrawCircleHole(true);
        lineDataSet.setValueTextSize(9f);
        lineDataSet.setFillAlpha(65); //设置填充的透明度
        lineDataSet.setFillColor(themeColor); //设置填充颜色
        lineDataSet.setDrawFilled(true); //设置是否填充

        ArrayList<LineDataSet> dataSets = new ArrayList<>();
        dataSets.add(lineDataSet);

        LineData data = new LineData(xValues, dataSets);//设置横坐标数据值，和纵坐标及数据样式

        mChart.setData(data);

        mChart.setMaxVisibleValueCount(20);
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
     * 当数据接收完毕后会触发这个广播接收器，然后依次执行以下几步：
     * 1. 旋转进度条消失
     * 2. 获取扫描数据，扫描类型，扫描时间
     * 3. 获取参考校准对象{@link KSTNanoSDK.ReferenceCalibration}，可以从本地获取，也可以从Nano 中读取
     * 4. 根据扫描数据和参考校准对象，获得扫描结果对象{@link KSTNanoSDK.ScanResults}，是利用JNI 调用C 语言函数
     * 5. 根据扫描结果对象计算出吸收率，反射率，强度
     * 6. 画图
     * 7. 保存数据（如果设置了）
     * 8. 继续扫描（如果设置了）
     *
     */
    public class scanDataReadyReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "gaohuixx:scanDataReadyReceiver.onReceive()");
            calProgress.setVisibility(View.GONE);
            btn_scan.setText(getString(R.string.scan));
            byte[] scanData = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_DATA);//从Intent中获取数据

//            String scanType = intent.getStringExtra(KSTNanoSDK.EXTRA_SCAN_TYPE);//好像不应该从Intent中获取扫描类型
//            String scanType = activeConf.getScanType();//从activeConf 中获取扫描类型，只有三种：Hadamard，Column，Slew
            String scanType = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, "Column 1");//从数据库中获取当前配置名称

            String scanDate = intent.getStringExtra(KSTNanoSDK.EXTRA_SCAN_DATE);//17031800200720
            scanDate = TimeUtil.convertTime(scanDate);

            //获取到参考校正数据对象，这个对象是一个全局变量，是当时我们接收完成后就有了的，我们直接获取，不是从本地读取的！
            //就是说在本地保存的那个参考校正数据根本没用上。。。

            boolean b = SettingsManager.getBooleanPref(mContext, "ReferenceCalibration", false);//获取设置
            KSTNanoSDK.ReferenceCalibration ref = null;

            if (b){
                ref = KSTNanoSDK.ReferenceCalibration.currentCalibration.get(0);//获取Nano中的参考校正对象
            }else{
                try {
                    ref = NanoUtil.getRefCal(getResources().getAssets().open("refcals"));//通过我自己的方法从本地获取参考校正对象
                    Log.i(TAG, "ref.getRefCalCoefficients(): " + ref.getRefCalCoefficients());
                    Log.i(TAG, "ref.getRefCalMatrix(): " + ref.getRefCalMatrix());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //利用参考校准系数和参考校准矩阵计算出扫描结果并封装成对象，计算过程调用的是C 语言写的函数
            results = KSTNanoSDK.KSTNanoSDK_dlpSpecScanInterpReference(scanData, ref.getRefCalCoefficients(), ref.getRefCalMatrix());

            mXValues.clear();
            mIntensityFloat.clear();
            mAbsorbanceFloat.clear();
            mReflectanceFloat.clear();
            mWavelengthFloat.clear();

            int index;//反射率和吸光率都是从其它数据算出来的，这个算法叼炸天，不明觉厉
            for (index = 0; index < results.getLength(); index++) {
                mXValues.add(String.format("%.02f", KSTNanoSDK.ScanResults.getSpatialFreq(mContext, results.getWavelength()[index])));
                mIntensityFloat.add(new Entry((float) results.getUncalibratedIntensity()[index], index));
                mAbsorbanceFloat.add(new Entry((-1) * (float) Math.log10((double) results.getUncalibratedIntensity()[index] / (double) results.getIntensity()[index]), index));
                mReflectanceFloat.add(new Entry((float) results.getUncalibratedIntensity()[index] / results.getIntensity()[index], index));
                mWavelengthFloat.add((float) results.getWavelength()[index]);
            }

            //还是获取最大值与最小值，获取最大最小波长还有点用，但是后面获取反射率那些就没用了
            float minWavelength = mWavelengthFloat.get(0);
            float maxWavelength = mWavelengthFloat.get(0);

            for (Float f : mWavelengthFloat) {
                if (f < minWavelength) minWavelength = f;
                if (f > maxWavelength) maxWavelength = f;
            }


            mViewPager.setAdapter(mViewPager.getAdapter());//开始绘制光谱图
            mViewPager.invalidate();


            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyMMddHHmmss", java.util.Locale.getDefault());
            String ts = simpleDateFormat.format(new Date());//这个是文件名中的显示

            ActionBar ab = getSupportActionBar();
            if (ab != null) {

                if (filePrefix.getText().toString().equals("")) {
                    ab.setTitle("Nano" + ts);
                } else {
                    ab.setTitle(filePrefix.getText().toString() + ts);
                }
            }

            boolean saveOS = btn_os.isChecked();//判断是否需要保存到手机
            boolean continuous = btn_continuous.isChecked();//判断是否继续扫描

            writeCSV(ts, results, saveOS);//ts 是170318200720，这个不用变，
            writeCSVDict(ts, scanType, scanDate, String.valueOf(minWavelength), String.valueOf(maxWavelength), String.valueOf(results.getLength()), String.valueOf(results.getLength()), "1", "2.00", saveOS);

            //是否继续扫描，如果是就再扫
            SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.prefix, filePrefix.getText().toString());
            if (continuous) {
                calProgress.setVisibility(View.VISIBLE);
                btn_scan.setText(getString(R.string.scanning));
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.SEND_DATA));
            }
        }
    }

    /**
     * 自定义一个接收器接收返回参考校正数据
     *
     */
    public class refReadyReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "gaohuixx:refReadyReceiver.onReceive()");
            byte[] refCoeff = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_REF_COEF_DATA);
            byte[] refMatrix = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_REF_MATRIX_DATA);
            ArrayList<KSTNanoSDK.ReferenceCalibration> refCal = new ArrayList<>();
            refCal.add(new KSTNanoSDK.ReferenceCalibration(refCoeff, refMatrix));
            KSTNanoSDK.ReferenceCalibration.writeRefCalFile(mContext, refCal);//把参考校正数据写到本地,是直接把对象给保存了
            calProgress.setVisibility(View.GONE);

        }
    }

    /**
     * 当点击开始扫描按钮后触发这个广播接收器
     */
    public class ScanStartedReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "gaohuixx:ScanStartedReceiver.onReceive()");
            calProgress.setVisibility(View.VISIBLE);
            btn_scan.setText(getString(R.string.scanning));
        }
    }

    /**
     * 自定义接收器用来设置Nano 的时间
     */
    public class notifyCompleteReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "gaohuixx:notifyCompleteReceiver.onReceive()");//发广播时也可以传参数啊！
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.SET_TIME));
        }
    }

    /**
     * 把扫描数据写到CSV 文件中
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
            String csvOS = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/csv/" + prefix + currentTime + ".csv";

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
     * @param numPoints numPoints 和resolution 是相同的
     * @param resolution the scan resolution
     * @param numAverages 平均扫描数，是写死的，1
     * @param measTime 总测量时间，是写死的，2.00
     * @param saveOS boolean indicating if this file should be saved to the OS
     */
    private void writeCSVDict(String currentTime, String scanType, String timeStamp, String spectStart, String spectEnd, String numPoints, String resolution, String numAverages, String measTime, boolean saveOS) {

        String prefix = filePrefix.getText().toString();
        if (prefix.equals("")) {
            prefix = "Nano";
        }

        if (saveOS) {
            String csv = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/dict/" + prefix + currentTime + ".dict";
            File file = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/dict");
            if(!file.exists())
                file.mkdirs();

            CSVWriter writer;
            try {
                writer = new CSVWriter(new FileWriter(csv));
                List<String[]> data = new ArrayList<String[]>();
                data.add(new String[]{"测量方法", scanType});
                data.add(new String[]{"测量时间", timeStamp});
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

    /**
     * 代码去管理Service 的生命周期
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override   //这个方法分别会在Activity与Service 建立关联的时候调用
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "ServiceConnection.onServiceConnected()");

            //获取NanoBLEService 这个服务的引用
            mNanoBLEService = ((NanoBLEService.LocalBinder) service).getService();

            //初始化蓝牙，如果BLE 不可用，那么结束
            if (!mNanoBLEService.initialize()) {
                finish();
            }

            //开始扫描匹配DEVICE_NAME 的设备
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

        @Override   //这个方法分别会在Activity与Service 解除关联的时候调用
        public void onServiceDisconnected(ComponentName componentName) {
            mNanoBLEService = null;
        }
    };

    /**
     * 这个是蓝牙扫描的回掉函数，每扫到一个蓝牙设备就调用一次这个方法
     * 首先获取到这个设备，然后判断它的名字是否是“NIRScanNano”，如果是就连接它
     * 然后将标志位connected 置为true，以后即使在扫描到Nano 设备，只要connected 为true
     * 就不再连接了
     *
     */
    private final ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null && connected == false) {
                if (device.getName().equals(DEVICE_NAME)) {    //只有当蓝牙设备名是NIRScanNano 时才连接
                    Log.i(TAG, "mLeScanCallback：此时搜索到相应的Nano，准备连接");
                    mNanoBLEService.connect(device.getAddress());//这里只是把蓝牙设备的MAC 地址传过去
                    connected = true;
                    Log.i(TAG, "此时connected = true，代表已经连接成功");

                    //这里很重要，不要在这里停止扫描，因为它和mNanoBLEService.connect() 是同时做的，如果在连接之前就
                    //停止了扫描时不行的，所以这里不要停止扫描，要等6s 之后自动停止扫描，6s 之后肯定已经连接成功了！
                    //其它手机都是先连接上然后才停止扫描的，而我的荣耀6 大多说时候都是先停止扫描才开始连接，所以连不上Nano，
                    //但是偶尔有时候就是先连接上才停止扫描的，这时候就能连接成功，这也就解释了为什么我的坑爹荣耀6 大多数时候
                    //都连接不上，但是偶尔有时候就能连接上
                    //可能是停止扫描之后就把什么给关了，所以就没法连接上了，具体原理我没研究
                    //scanLeDevice(false);//停止扫描
                }
            }
        }
    };

    /**
     * 功能同上，只不过这个是用来处理偏好Nano 的
     */
    private final ScanCallback mPreferredLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null && connected == false) {
                if (device.getName().equals(DEVICE_NAME)) {
                    Log.i(TAG, "mPreferredLeScanCallback：此时搜索到相应的Nano，准备连接");
                    if (device.getAddress().equals(preferredDevice)) {
                        Log.i(TAG, "搜索到的Nano是偏好Nano");
                        mNanoBLEService.connect(device.getAddress());
                        Log.i(TAG, "连接上了偏好Nano");
                        connected = true;
//                        scanPreferredLeDevice(false);
                    }
                }
            }
        }
    };

    /**
     * 用来扫描蓝牙设备。指定时间内没有扫到就返回。扫到之后就调用回掉函数来处理
     *
     * @param enable 为true 时开始扫描，为false 时停止扫描
     *
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
                            Log.i(TAG, "扫了，但是没有扫描到");
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
     * 功能同上，只不过它是用来扫描偏好设备的
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
            if(mBluetoothLeScanner != null) {
                mBluetoothLeScanner.startScan(mPreferredLeScanCallback);
            }else{
                finish();
                Toast.makeText(NewScanActivity.this, "请先开启蓝牙并再次尝试", Toast.LENGTH_SHORT).show();
            }
        } else {
            mBluetoothLeScanner.stopScan(mPreferredLeScanCallback);
        }
    }

    /**
     * 这个对话框告诉用户没有Nano被连接上。当用户选择ok 的时候这个activity 将结束
     */
    private void notConnectedDialog() {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext, R.style.DialogTheme);
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
        alertDialogBuilder.setCancelable(false);

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }

    /**
     * 自定义一个接收器用来接收calibration coefficient data.
     * 这两个接收器都只是用来更新进度条的
     */
    public class requestCalCoeffReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "gaohuixx:requestCalCoeffReceiver.onReceive():下载校准系数");
            intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE, 0);
            Boolean size = intent.getBooleanExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE_PACKET, false);
            if (size) {//第一次接收到执行这个
                calProgress.setVisibility(View.INVISIBLE);
                barProgressDialog = new ProgressDialog(NewScanActivity.this, R.style.DialogTheme);

                barProgressDialog.setTitle(getString(R.string.dl_ref_cal));
                barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                barProgressDialog.setProgress(0);
                barProgressDialog.setMax(intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE, 0));
                barProgressDialog.setCancelable(false);
                barProgressDialog.show();
            } else {//之后执行这个
                barProgressDialog.setProgress(barProgressDialog.getProgress() + intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE, 0));
            }
        }
    }

    /**
     * 自定义一个接收器用来接收calibration matrix data.
     * 当这个动作完成的时候，它将请求active configuration，来保证它能够在listview 中显示出来
     */
    public class requestCalMatrixReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "gaohuixx:requestCalMatrixReceiver.onReceive():下载校准矩阵");
            intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0);
            Boolean size = intent.getBooleanExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE_PACKET, false);
            if (size) {
                barProgressDialog.dismiss();
                barProgressDialog = new ProgressDialog(NewScanActivity.this, R.style.DialogTheme);

                barProgressDialog.setTitle(getString(R.string.dl_cal_matrix));
                barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                barProgressDialog.setProgress(0);
                barProgressDialog.setMax(intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0));//设置最大值
                barProgressDialog.setCancelable(false);
                barProgressDialog.show();
            } else {
                barProgressDialog.setProgress(barProgressDialog.getProgress() + intent.getIntExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0));
            }
            if (barProgressDialog.getProgress() == barProgressDialog.getMax()) {
                //接收完之后就请求active 配置
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.REQUEST_ACTIVE_CONF));
            }
        }
    }

    /**
     * 自定义接收器用来处理扫描配置
     * 在触发这个广播接收器的时候会把数据以 byte[] 的格式传过来
     */
    private class ScanConfReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "ScanConfReceiver.onReceive():处理扫描配置");

            byte[] smallArray = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_DATA);
            byte[] addArray = new byte[smallArray.length * 3];
            byte[] largeArray = new byte[smallArray.length + addArray.length];

            System.arraycopy(smallArray, 0, largeArray, 0, smallArray.length);
            System.arraycopy(addArray, 0, largeArray, smallArray.length, addArray.length);

            Log.w("_JNI","largeArray Size: "+ largeArray.length);
            //调用C 语言函数将获取到的数据解析并封装成KSTNanoSDK.ScanConfiguration 对象
            KSTNanoSDK.ScanConfiguration scanConf = KSTNanoSDK.KSTNanoSDK_dlpSpecScanReadConfiguration(intent.getByteArrayExtra(KSTNanoSDK.EXTRA_DATA));

            activeConf = scanConf;

            barProgressDialog.dismiss();
            btn_scan.setClickable(true);//此时按钮可用

            mMenu.findItem(R.id.action_config).setEnabled(true);

            SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, scanConf.getConfigName());
            tv_scan_conf.setText(scanConf.getConfigName());


        }
    }
    /**
     *
     * 广播接收器处理断开连接事件，一旦Nano 连接断开，这个activity 会结束并返回到{@link MainActivity}，
     * 同时显示一条message 告诉用户连接已经断开
     * 当Nano 断开连接后大约12s 后才能触发这个广播
     *
     */
    public class DisconnReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "DisconnReceiver.onReceive():Nano连接断开");
            Toast.makeText(mContext, R.string.nano_disconnected, Toast.LENGTH_SHORT).show();
            finish();
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(1000);//震动1s
        }
    }

}
