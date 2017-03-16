package com.kstechnologies.NanoScan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;
import com.kstechnologies.nirscannanolibrary.SettingsManager;

/**
 * 这个activity 控制着Nano 设备的状态视图
 * 包含了一些像Nano 的温度，湿度，电量百分比信息
 * @author collinmast,gaohui
 */
public class DeviceStatusActivity extends BaseActivity {

    private static Context mContext;

    private TextView tv_batt;
    private TextView tv_temp;
    private TextView tv_humid;
    private EditText et_tempThresh;
    private EditText et_humidThresh;

    private BroadcastReceiver mStatusReceiver;
    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();
    private final IntentFilter disconnFilter = new IntentFilter(KSTNanoSDK.ACTION_GATT_DISCONNECTED);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_status);

        mContext = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        android.support.v7.app.ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle(R.string.device_status);
        actionBar.setDisplayHomeAsUpEnabled(true);

        //获取这个视图的UI引用
        tv_batt = (TextView)findViewById(R.id.tv_batt);
        tv_temp = (TextView)findViewById(R.id.tv_temp);
        tv_humid = (TextView)findViewById(R.id.tv_humid);
        et_tempThresh = (EditText)findViewById(R.id.et_tempThresh);
        et_humidThresh = (EditText)findViewById(R.id.et_humidThresh);
        Button btn_update_thresholds = (Button) findViewById(R.id.btn_update_thresholds);

        //Set up threshold update button
        btn_update_thresholds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent thresholdUpdateIntent = new Intent(KSTNanoSDK.UPDATE_THRESHOLD);
                String tempString = et_tempThresh.getText().toString();
                String humidString = et_humidThresh.getText().toString();
                byte[] tempThreshBytes = {0, 0};
                byte[] humidThreshBytes = {0, 0};
                if (!tempString.equals("")) {
                    int tempThreshFloat = (int) (Float.parseFloat(tempString) * 100);

                    tempThreshBytes[0] = (byte) ((tempThreshFloat) & 0xFF);
                    tempThreshBytes[1] = (byte) (((tempThreshFloat) >> 8) & 0xFF);
                }
                if (!humidString.equals("")) {
                    int humidThreshFloat = (int) (Float.parseFloat(humidString) * 100);
                    humidThreshBytes[0] = (byte) ((humidThreshFloat) & 0xFF);
                    humidThreshBytes[1] = (byte) (((humidThreshFloat) >> 8) & 0xFF);
                }
                thresholdUpdateIntent.putExtra(KSTNanoSDK.EXTRA_TEMP_THRESH, tempThreshBytes);
                thresholdUpdateIntent.putExtra(KSTNanoSDK.EXTRA_HUMID_THRESH, humidThreshBytes);

                LocalBroadcastManager.getInstance(mContext).sendBroadcast(thresholdUpdateIntent);
            }
        });

        //The the hint of the temp threshold based on preferred temperature units
        if(!SettingsManager.getBooleanPref(this, SettingsManager.SharedPreferencesKeys.tempUnits,false)){
            et_tempThresh.setHint(R.string.deg_c);
        }else{
            et_tempThresh.setHint(R.string.deg_f);
        }

        //Send broadcast to get device status information from the BLE service
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.GET_STATUS));

        /*Set up receiver for device status information
         * The receiver recalculates the temperature in preferred units since it is always returned
         * in degrees Celsius
         */
        mStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int batt = intent.getIntExtra(KSTNanoSDK.EXTRA_BATT, 0);
                float temp = intent.getFloatExtra(KSTNanoSDK.EXTRA_TEMP, 0);
                tv_batt.setText(getString(R.string.batt_level_value, batt));
                if(!SettingsManager.getBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.tempUnits, false)){
                    tv_temp.setText(getString(R.string.temp_value_c, Float.toString(temp)));
                }else{
                    temp = (float)(temp * 1.8)+32;
                    tv_temp.setText(getString(R.string.temp_value_f, Float.toString(temp)));
                }

                tv_humid.setText(getString(R.string.humid_value, intent.getFloatExtra(KSTNanoSDK.EXTRA_HUMID, 0)));

                ProgressBar pb = (ProgressBar)findViewById(R.id.pb_status);
                pb.setVisibility(View.INVISIBLE);
            }
        };

        //Register receivers for disconnection events and device status information
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mStatusReceiver, new IntentFilter(KSTNanoSDK.ACTION_STATUS));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
    }


    /**
     * 当这个activity 结束时，移除BroadcastReceiver 的注册，处理断开连接事件
     */
    @Override
    public void onDestroy(){
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mStatusReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
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
