package com.gaohui.nano;

import android.content.BroadcastReceiver;
import android.content.Context;
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
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

import java.util.ArrayList;

/**
 * 由于对于Nano 的扫描配置参数的各个属性不是很明白，结构不是很清楚，而且我觉得他的程序也不是很好。最无语的是，这
 * 里我改了一天，还不好使，总是出些奇怪的问题。所以我决定废弃这个页面！
 */
@Deprecated
public class ActiveScanActivity extends BaseActivity {

    private static Context mContext;

    private ScanConfAdapter scanConfAdapter;
    private SlewScanConfAdapter slewScanConfAdapter;
    private ArrayList<KSTNanoSDK.ScanConfiguration> configs = new ArrayList<>();
    private ArrayList<KSTNanoSDK.SlewScanSection> sections = new ArrayList<>();
    private ListView lv_configs;
    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();
    private final IntentFilter disconnFilter = new IntentFilter(KSTNanoSDK.ACTION_GATT_DISCONNECTED);

    //C 光谱库调用。只有叫这个名字的activity被允许调用这个函数：
    //public native Object dlpSpecScanReadConfiguration(byte[] data);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_scan);


        mContext = this;


        KSTNanoSDK.ScanConfiguration activeConf = null;//定义一个ScanConfiguration 对象，从NewScanActivity 那里获取的
        if(getIntent().getSerializableExtra("conf") != null){//如果这里获取不到那么activeConf 就为空
            activeConf = (KSTNanoSDK.ScanConfiguration) getIntent().getSerializableExtra("conf");
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        if(activeConf != null) {
            actionBar.setTitle(activeConf.getConfigName());//以配置名称作为标题，在getConfigName() 时，内部自动将byte[]转化为String
        }
        actionBar.setDisplayHomeAsUpEnabled(true);

        lv_configs = (ListView) findViewById(R.id.lv_configs);//取到activity_active_scan 布局里面唯一的一个ListView

        Log.i("gaohui", "扫描类型是：" + activeConf.getScanType());
        //如果这个扫描配置包含多个扫描，即多个section，那么它就是由多个section 组成的，将它拆成多个section 然后放到sections 集合里
        //section 集合代表SlewScanConfAdapter 对应的ArrayList
        //这个类型有三种Hadamard, Column, slew
        if(activeConf != null && activeConf.getScanType().equals("Slew")){
            int numSections = activeConf.getSlewNumSections();
            int i;
            for(i = 0; i < numSections; i++){
                sections.add(new KSTNanoSDK.SlewScanSection(activeConf.getSectionScanType()[i],//类型，这个类型只有两种Hadamard,Column
                        activeConf.getSectionWidthPx()[i],//宽度，单位是px
                        (activeConf.getSectionWavelengthStartNm()[i] & 0xFFFF),//波长起始范围
                        (activeConf.getSectionWavelengthEndNm()[i] & 0xFFFF),//波长结束范围
                        activeConf.getSectionNumPatterns()[i],//NumPatterns
                        activeConf.getSectionNumRepeats()[i],//重复次数
                        activeConf.getSectionExposureTime()[i]));//曝光时间
            }
            Log.i("gaohui","设置 slewScanConfAdapter");
            slewScanConfAdapter = new SlewScanConfAdapter(mContext, sections);
            lv_configs.setAdapter(slewScanConfAdapter);
        }else{
            Log.i("gaohui","设置 scanConfAdapter");
            configs.add(activeConf);//对应ListView 的那个数组，但是只有一个元素，正常也就只有一个元素，不知道为什么要弄成对应ListView
            scanConfAdapter = new ScanConfAdapter(mContext, configs);
            lv_configs.setAdapter(scanConfAdapter);
        }



        //注册必要的广播服务
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
    }


    /*
     * 当activity 被销毁的时候，取消注册广播接收器
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
    }


    /**
     * Custom adapter that holds {@link KSTNanoSDK.ScanConfiguration} objects for the listview
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
            Log.i("gaohui", "ScanConfiguration-scanType: " + config.getSectionScanType());
            if (config != null) {
                viewHolder.scanType.setText(config.getScanType());
                viewHolder.rangeStart.setText(getString(R.string.range_start_value, config.getWavelengthStartNm()));
                viewHolder.rangeEnd.setText(getString(R.string.range_end_value, config.getWavelengthEndNm()));
                viewHolder.width.setText(getString(R.string.width_value, config.getWidthPx()));
                viewHolder.patterns.setText(getString(R.string.patterns_value, config.getNumPatterns()));
                viewHolder.repeats.setText(getString(R.string.repeats_value, config.getNumRepeats()));
                viewHolder.serial.setText(config.getScanConfigSerialNumber());
            }
            return convertView;
        }
    }

    /**
     * Custom adapter that holds {@link KSTNanoSDK.ScanConfiguration} objects for the listview
     */
    public class SlewScanConfAdapter extends ArrayAdapter<KSTNanoSDK.SlewScanSection> {
        private final ArrayList<KSTNanoSDK.SlewScanSection> sections;


        public SlewScanConfAdapter(Context context, ArrayList<KSTNanoSDK.SlewScanSection> values) {
            super(context, -1, values);
            this.sections = values;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(this.getContext())
                        .inflate(R.layout.row_slew_scan_configuration_item, parent, false);

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

            final KSTNanoSDK.SlewScanSection config = getItem(position);
            if (config != null) {
//                viewHolder.scanType.setText(config.getSectionScanType());
                viewHolder.rangeStart.setText(getString(R.string.range_start_value, config.getWavelengthStartNm()));
                viewHolder.rangeEnd.setText(getString(R.string.range_end_value, config.getWavelengthEndNm()));
                viewHolder.width.setText(getString(R.string.width_value, config.getWidthPx()));
                viewHolder.patterns.setText(getString(R.string.patterns_value, config.getNumPatterns()));
                viewHolder.repeats.setText(getString(R.string.repeats_value, config.getNumRepeats()));
            }
            Log.i("gaohui", "SlewScanSection-scanType: " + config.getSectionScanType());
            Log.i("gaohui", "rangeStart: " + config.getWavelengthStartNm());
            Log.i("gaohui", "rangeEnd: " + config.getWavelengthEndNm());
            Log.i("gaohui", "width: " + config.getWidthPx());
            Log.i("gaohui", "patterns: " + config.getNumPatterns());
            Log.i("gaohui", "repeats: " + config.getNumRepeats());
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
     * 返回到{@link MainActivity} ，同时弹出一条信息告知用户连接已经断开
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
