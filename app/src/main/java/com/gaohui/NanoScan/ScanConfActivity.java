package com.gaohui.NanoScan;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import com.gaohui.utils.NanoUtil;
import com.gaohui.utils.ThemeManageUtil;
import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;
import com.kstechnologies.nirscannanolibrary.SettingsManager;

/**
 * 这个activity 控制着Nano保存扫描配置的视图，这些配置必须被分别地读取
 *
 * 警告：这个activity 使用了JNI函数调用，一定要保证它的名字和位置不被改变，，否则C 光谱库调用将失败
 *
 * 关于扫描配置索引：
 * 1. Nano 中使用两个字节来代表一个扫描配置索引
 * 2. 获取和设置Active 扫描配置都是以字节数组的形式传输的
 * 3. 获取到扫描配置对象，通过对象的getScanConfigIndex() 获取到的是int 型
 *
 * 这页的几个广播接收器执行顺序：
 * {@link scanConfSizeReceiver} ：接收扫描配置的数量，显示进度条 ScanConfReceiver
 * {@link ScanConfReceiver} ：接收每一个扫描配置，没接收一个，更新下进度条。全部接收完成后，发广播，准备获取active 配置
 * {@link getActiveScanConfReceiver} ：接收active 配置
 *
 * @author collinmast,gaohui
 */
public class ScanConfActivity extends BaseActivity {

    private static Context mContext;

    private final BroadcastReceiver scanConfReceiver = new ScanConfReceiver();
    private final IntentFilter scanConfFilter = new IntentFilter(KSTNanoSDK.SCAN_CONF_DATA);
    private ScanConfAdapter scanConfAdapter;
    private ArrayList<KSTNanoSDK.ScanConfiguration> configs = new ArrayList<>();//代表那个列表对应的集合
    private ListView lv_configs;
    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();
    private final IntentFilter disconnFilter = new IntentFilter(KSTNanoSDK.ACTION_GATT_DISCONNECTED);
    private BroadcastReceiver scanConfSizeReceiver;
    private BroadcastReceiver getActiveScanConfReceiver;
    private int storedConfSize;//Nano 中保存的配置数量
    private int receivedConfSize;//己经接收的配置数量

    ProgressDialog barProgressDialog;

    //C 光谱库调用。只有叫这个名字的activity被允许调用这个函数：
    //public native Object dlpSpecScanReadConfiguration(byte[] data);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_conf);

        mContext = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle(R.string.stored_configurations);
        actionBar.setDisplayHomeAsUpEnabled(true);

        lv_configs = (ListView) findViewById(R.id.lv_configs);

        //为List 的item 添加监听，当点击时，发送设置active 配置广播
        lv_configs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                int indexInt = configs.get(i).getScanConfigIndex();//获取被点击的配置项的index，是int型的
                byte[] indexByteArray = NanoUtil.indexToByteArray(indexInt);//将int 型的index 转成byte[] 型，为了发送

                Intent setActiveConfIntent = new Intent(KSTNanoSDK.SET_ACTIVE_CONF);
                setActiveConfIntent.putExtra(KSTNanoSDK.EXTRA_SCAN_INDEX, indexByteArray);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(setActiveConfIntent);
            }
        });

         //初始化一个扫描配置接收器，当配置数量被接收的时候，显示进度条
        scanConfSizeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                storedConfSize = intent.getIntExtra(KSTNanoSDK.EXTRA_CONF_SIZE, 0);
                if (storedConfSize > 0) {
                    barProgressDialog = new ProgressDialog(ScanConfActivity.this, R.style.DialogTheme);

                    barProgressDialog.setTitle(getString(R.string.reading_configurations));
                    barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    barProgressDialog.setProgress(0);
                    barProgressDialog.setMax(intent.getIntExtra(KSTNanoSDK.EXTRA_CONF_SIZE, 0));
                    barProgressDialog.setCancelable(true);
                    barProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            finish();
                        }
                    });
                    barProgressDialog.setCancelable(false);
                    barProgressDialog.show();
                    receivedConfSize = 0;
                }
            }
        };

         //初始化一个active 配置接收器，当接收到active 配置，设置active 配置的颜色为当前主题颜色
        getActiveScanConfReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                byte[] indexByteArray = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_ACTIVE_CONF);//两个字节长，0代表低位
                int indexInt = NanoUtil.indexToInt(indexByteArray);//将接收到的byte[] 型的index 转成int 型，为了比较

                barProgressDialog.dismiss();//进度条消失

                for (KSTNanoSDK.ScanConfiguration c : configs) {
                    if (c.getScanConfigIndex() == indexInt) {
                        c.setActive(true);

                    } else {
                        c.setActive(false);
                    }
                }

                lv_configs.setAdapter(scanConfAdapter);
                lv_configs.setVisibility(View.VISIBLE);//列表显示
            }
        };

        //发送一个获取扫描配置的广播，告诉别人我要获取扫描配置
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.GET_SCAN_CONF));

        //注册所有广播接收器，一共四个
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfReceiver, scanConfFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfSizeReceiver, new IntentFilter(KSTNanoSDK.SCAN_CONF_SIZE));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(getActiveScanConfReceiver, new IntentFilter(KSTNanoSDK.SEND_ACTIVE_CONF));
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfSizeReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(getActiveScanConfReceiver);
    }



    /**
     * 这个广播接收器用来接收扫描配置，当接收所有扫描配置接收完成后，进度条将关闭，并且将把所有配置列出来
     */
    private class ScanConfReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            //调用C语言方法将数据反序列化解析并封装成ScanConfiguration 对象
            KSTNanoSDK.ScanConfiguration scanConf = KSTNanoSDK.KSTNanoSDK_dlpSpecScanReadConfiguration(intent.getByteArrayExtra(KSTNanoSDK.EXTRA_DATA));

            configs.add(scanConf);//每接收到一个就把它存到集合里
            receivedConfSize++;//己经接收的配置数量加一
            if (receivedConfSize == storedConfSize) {//如果己经接收的配置数量等于Nano 中保存的配置数量
                //开始发广播准备获取当前active 配置
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.GET_ACTIVE_CONF));
                scanConfAdapter = new ScanConfAdapter(mContext, configs);
            } else {
                barProgressDialog.setProgress(receivedConfSize);//更新进度条
            }

        }
    }

    /**
     * listview 的每一行都是一个KSTNanoSDK.ScanConfiguration
     */
    public class ScanConfAdapter extends ArrayAdapter<KSTNanoSDK.ScanConfiguration> {
        private final ArrayList<KSTNanoSDK.ScanConfiguration> configs;


        public ScanConfAdapter(Context context, ArrayList<KSTNanoSDK.ScanConfiguration> values) {
            super(context, -1, values);
            this.configs = values;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(this.getContext())
                        .inflate(R.layout.row_scan_configuration_item, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.scanType = (TextView) convertView.findViewById(R.id.tv_scan_type);
                viewHolder.rangeStart = (TextView) convertView.findViewById(R.id.tv_range_start_value);
                viewHolder.rangeEnd = (TextView) convertView.findViewById(R.id.tv_range_end_value);
                viewHolder.width = (TextView) convertView.findViewById(R.id.tv_width_value);
                viewHolder.patterns = (TextView) convertView.findViewById(R.id.tv_patterns_value);
                viewHolder.repeats = (TextView) convertView.findViewById(R.id.tv_repeats_value);
                viewHolder.serial = (TextView) convertView.findViewById(R.id.tv_serial_value);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final KSTNanoSDK.ScanConfiguration config = getItem(position);
            if (config != null) {
                viewHolder.scanType.setText(config.getConfigName());
                viewHolder.rangeStart.setText(getString(R.string.range_start_value, config.getWavelengthStartNm()));
                viewHolder.rangeEnd.setText(getString(R.string.range_end_value, config.getWavelengthEndNm()));
                viewHolder.width.setText(getString(R.string.width_value, config.getWidthPx()));
                viewHolder.patterns.setText(getString(R.string.patterns_value, config.getNumPatterns()));
                viewHolder.repeats.setText(getString(R.string.repeats_value, config.getNumRepeats()));
                viewHolder.serial.setText(config.getScanConfigSerialNumber());
                if (config.isActive()) {
                    viewHolder.scanType.setTextColor(ThemeManageUtil.getCurrentThemeColor());
                    SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, config.getConfigName());
                } else {
                    viewHolder.scanType.setTextColor(0xff888888);
                }
            }
            return convertView;
        }
    }

    /**
     * View holder for the {@link KSTNanoSDK.ScanConfiguration} class
     */
    private class ViewHolder {
        private TextView scanType;
        private TextView rangeStart;
        private TextView rangeEnd;
        private TextView width;
        private TextView patterns;
        private TextView repeats;
        private TextView serial;
    }

    /**
     * 这个广播接收器处理连接断开事件。如果Nano 的连接断开， 这个activity 会立刻结束，并且将
     * 返回到{@link ScanListActivity} ，同时弹出一条信息告知用户连接已经断开
     */
    public class DisconnReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(mContext, R.string.nano_disconnected, Toast.LENGTH_SHORT).show();
            finish();
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(1000);//震动1s
        }
    }
}
