package com.kstechnologies.NanoScan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

import java.util.ArrayList;

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


        KSTNanoSDK.ScanConfiguration activeConf = null;
        if(getIntent().getSerializableExtra("conf") != null){
            activeConf = (KSTNanoSDK.ScanConfiguration) getIntent().getSerializableExtra("conf");
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        android.support.v7.app.ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        if(activeConf != null) {
            actionBar.setTitle(activeConf.getConfigName());
        }
        actionBar.setDisplayHomeAsUpEnabled(true);

        lv_configs = (ListView) findViewById(R.id.lv_configs);


        if(activeConf != null && activeConf.getScanType().equals("Slew")){
            int numSections = activeConf.getSlewNumSections();
            int i;
            for(i = 0; i < numSections; i++){
                sections.add(new KSTNanoSDK.SlewScanSection(activeConf.getSectionScanType()[i],
                        activeConf.getSectionWidthPx()[i],
                        (activeConf.getSectionWavelengthStartNm()[i] & 0xFFFF),
                        (activeConf.getSectionWavelengthEndNm()[i] & 0xFFFF),
                        activeConf.getSectionNumPatterns()[i],
                        activeConf.getSectionNumRepeats()[i],
                        activeConf.getSectionExposureTime()[i]));
            }
            Log.i("__ACTIVE_CONF","Setting slew conf adapter");
            slewScanConfAdapter = new SlewScanConfAdapter(mContext, sections);
            lv_configs.setAdapter(slewScanConfAdapter);
        }else{
            configs.add(activeConf);
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

                viewHolder.scanType.setVisibility(View.GONE);
                LinearLayout ll_range_start = (LinearLayout)convertView.findViewById(R.id.ll_range_start);
                LinearLayout ll_range_end = (LinearLayout)convertView.findViewById(R.id.ll_range_end);
                LinearLayout ll_patterns= (LinearLayout)convertView.findViewById(R.id.ll_patterns);
                LinearLayout ll_width = (LinearLayout)convertView.findViewById(R.id.ll_width);
                ll_range_start.setVisibility(View.VISIBLE);
                ll_range_end.setVisibility(View.VISIBLE);
                ll_patterns.setVisibility(View.VISIBLE);
                ll_width.setVisibility(View.VISIBLE);

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

                Log.i("gaohui", "scanType: " + config.getSectionScanType());
                Log.i("gaohui", "rangeStart: " + config.getWavelengthStartNm());
                Log.i("gaohui", "rangeEnd: " + config.getWavelengthEndNm());
                Log.i("gaohui", "width: " + config.getWidthPx());
                Log.i("gaohui", "patterns: " + config.getNumPatterns());
                Log.i("gaohui", "repeats: " + config.getNumRepeats());
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
                viewHolder.rangeStart.setText(getString(R.string.range_start_value, config.getWavelengthStartNm()));
                viewHolder.rangeEnd.setText(getString(R.string.range_end_value, config.getWavelengthEndNm()));
                viewHolder.width.setText(getString(R.string.width_value, config.getWidthPx()));
                viewHolder.patterns.setText(getString(R.string.patterns_value, config.getNumPatterns()));
                viewHolder.repeats.setText(getString(R.string.repeats_value, config.getNumRepeats()));
            }
            Log.i("gaohui", "scanType: " + config.getSectionScanType());
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
     * 返回到{@link ScanListActivity} ，同时弹出一条信息告知用户连接已经断开
     */
    public class DisconnReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(mContext, R.string.nano_disconnected, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
