package com.gaohui.NanoScan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

/**
 * 这个activity 在Nano连接上之后控制着设备信息视图，当activity 被创建后就开始向{@link NanoBLEService}发
 * 广播，然后开始接收设备信息
 *
 * @author collinmast,gaohui
 */

public class DeviceInfoActivity extends BaseActivity {

    private static Context mContext;

    private TextView tv_manuf;
    private TextView tv_model;
    private TextView tv_serial;
    private TextView tv_hw;
    private TextView tv_tiva;
    private TextView tv_spec;

    private BroadcastReceiver mInfoReceiver;

    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();
    private final IntentFilter disconnFilter = new IntentFilter(KSTNanoSDK.ACTION_GATT_DISCONNECTED);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);

        mContext = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        android.support.v7.app.ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle(R.string.device_information);
        actionBar.setDisplayHomeAsUpEnabled(true);

        //获取这个视图的UI引用
        tv_manuf = (TextView) findViewById(R.id.tv_manuf);
        tv_model = (TextView) findViewById(R.id.tv_model);
        tv_serial = (TextView) findViewById(R.id.tv_serial);
        tv_hw = (TextView) findViewById(R.id.tv_hw);
        tv_tiva = (TextView) findViewById(R.id.tv_tiva);
        tv_spec = (TextView) findViewById(R.id.tv_spectrum);

        //向BLE service 发送广播来请求设备信息
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(KSTNanoSDK.GET_INFO));

        /*
         * 初始化设备信息广播接收器
         * 所有设备设备信息都被发送到一个广播
         * 一旦信息被接收到了，就把progress bar 设置为不可见
         */
        mInfoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                tv_manuf.setText(intent.getStringExtra(KSTNanoSDK.EXTRA_MANUF_NAME).replace("\n", ""));
                tv_model.setText(intent.getStringExtra(KSTNanoSDK.EXTRA_MODEL_NUM).replace("\n", ""));
                tv_serial.setText(intent.getStringExtra(KSTNanoSDK.EXTRA_SERIAL_NUM));
                tv_hw.setText(intent.getStringExtra(KSTNanoSDK.EXTRA_HW_REV));
                tv_tiva.setText(intent.getStringExtra(KSTNanoSDK.EXTRA_TIVA_REV));
                tv_spec.setText(intent.getStringExtra(KSTNanoSDK.EXTRA_SPECTRUM_REV));

                ProgressBar pb = (ProgressBar) findViewById(R.id.pb_info);
                pb.setVisibility(View.INVISIBLE);
            }
        };

        //注册广播接收器
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mInfoReceiver, new IntentFilter(KSTNanoSDK.ACTION_INFO));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
    }


    /**
     * 当这个activity 结束时，移除BroadcastReceiver 的注册，处理断开连接事件
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mInfoReceiver);
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
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(1000);//震动1s
        }
    }
}
