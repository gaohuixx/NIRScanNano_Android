package com.kstechnologies.NanoScan;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import com.gaohui.utils.ThemeManageUtil;
import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;
import com.kstechnologies.nirscannanolibrary.SettingsManager;

/**
 * 这个activity 控制着Nano保存扫描配置的视图，这些配置必须被分别地读取
 *
 * 警告：这个activity 使用了JNI函数调用，一定要保证它的名字和位置不被改变，，否则C 光谱库调用将失败
 *
 * @author collinmast,gaohui
 */
public class ScanConfActivity extends BaseActivity {

    private static Context mContext;

    private final BroadcastReceiver scanConfReceiver = new ScanConfReceiver();
    private final IntentFilter scanConfFilter = new IntentFilter(KSTNanoSDK.SCAN_CONF_DATA);
    private ScanConfAdapter scanConfAdapter;
    private ArrayList<KSTNanoSDK.ScanConfiguration> configs = new ArrayList<>();
    private ListView lv_configs;
    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();
    private final IntentFilter disconnFilter = new IntentFilter(KSTNanoSDK.ACTION_GATT_DISCONNECTED);
    private BroadcastReceiver scanConfSizeReceiver;
    private BroadcastReceiver getActiveScanConfReceiver;
    private int storedConfSize;
    private int receivedConfSize;

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
        android.support.v7.app.ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle(R.string.stored_configurations);
        actionBar.setDisplayHomeAsUpEnabled(true);

        /* Initialize the receiver for the # of scan configurations
         * When the configuration size is received, show the progress dialog.
         */
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
                    barProgressDialog.show();
                    receivedConfSize = 0;
                }
            }
        };

        /* Initialize the receiver for the active scan configuration
         * When the active configuration is received, set the color of the name of the active
         * scan configuration to green
         */
        getActiveScanConfReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int index = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_ACTIVE_CONF)[0];

                barProgressDialog.dismiss();
                lv_configs.setVisibility(View.VISIBLE);

                for (KSTNanoSDK.ScanConfiguration c : scanConfAdapter.configs) {
                    if (c.getScanConfigIndex() == index) {
                        c.setActive(true);
                        lv_configs.setAdapter(scanConfAdapter);
                    } else {
                        c.setActive(false);
                    }
                }
            }
        };

        //Send broadcast to retrieve the scan configurations
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.GET_SCAN_CONF));

        //register the necessary broadcast receivers
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfReceiver, scanConfFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfSizeReceiver, new IntentFilter(KSTNanoSDK.SCAN_CONF_SIZE));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(getActiveScanConfReceiver, new IntentFilter(KSTNanoSDK.SEND_ACTIVE_CONF));
    }


    /*
     * When the activity is destroyed, unregister the BroadcastReceivers
     * handling receiving scan configurations, disconnect events, the # of configurations,
     * and the active configuration
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfSizeReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(getActiveScanConfReceiver);
    }



    /*
     * Broadcast receiver for scan configurations. When the expected number of configurations are
      * received, the dialog will be closed, and the list of configurations will be displayed.
     */
    private class ScanConfReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            KSTNanoSDK.ScanConfiguration scanConf = KSTNanoSDK.KSTNanoSDK_dlpSpecScanReadConfiguration(intent.getByteArrayExtra(KSTNanoSDK.EXTRA_DATA));
            lv_configs = (ListView) findViewById(R.id.lv_configs);

            lv_configs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    byte[] index = {0, 0};
                    index[0] = (byte) scanConfAdapter.configs.get(i).getScanConfigIndex();
                    Intent setActiveConfIntent = new Intent(KSTNanoSDK.SET_ACTIVE_CONF);
                    setActiveConfIntent.putExtra(KSTNanoSDK.EXTRA_SCAN_INDEX, index);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(setActiveConfIntent);
                }
            });

            receivedConfSize++;
            if (receivedConfSize == storedConfSize) {
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.GET_ACTIVE_CONF));
            } else {
                barProgressDialog.setProgress(receivedConfSize);
            }

            configs.add(scanConf);
            scanConfAdapter = new ScanConfAdapter(mContext, configs);
            lv_configs.setAdapter(scanConfAdapter);
        }
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
        }
    }
}
