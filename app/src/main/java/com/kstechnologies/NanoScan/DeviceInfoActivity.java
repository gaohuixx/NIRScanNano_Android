package com.kstechnologies.NanoScan;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

/**
 * 这个activity 在Nano连接上之后控制着设备信息视图，当activity 被创建后就开始向{@link NanoBLEService}发
 * 广播，然后开始接收设备信息
 *
 * @author collinmast
 */

public class DeviceInfoActivity extends Activity {

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

        //设置action bar 标题和添加返回按钮
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(getString(R.string.device_information));
        }

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
         * 一旦信息被接收到了，就把progress bar 设置为不可视
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

    /*
     * On resume, make a call to the superclass.
     * Nothing else is needed here besides calling
     * the super method.
     */
    @Override
    public void onResume() {
        super.onResume();
    }

    /*
     * When the activity is destroyed, unregister the BroadcastReceiver
     * handling disconnection events, and the receiver handling the device information
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mInfoReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
    }

    /*
     * Inflate the options menu
     * In this case, there is no menu and only an up indicator,
     * so the function should always return true.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    /*
     * Handle the selection of a menu item.
     * In this case, there is only the up indicator. If selected, this activity should finish.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Broadcast Receiver handling the disconnect event. If the Nano disconnects,
     * this activity should finish so that the user is taken back to the {@link ScanListActivity}.
     * A toast message should appear so that the user knows why the activity is finishing.
     */
    public class DisconnReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(mContext, R.string.nano_disconnected, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
