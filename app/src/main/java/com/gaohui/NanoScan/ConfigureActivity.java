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
import android.widget.AdapterView;

import android.widget.ListView;
import android.widget.Toast;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

/**
 * 一旦连接上了一个Nano ，这个activity 将控制设置视图，四个选项会被展示出来，每一个都将会启动一个新的activity
 * 每一个选项都需要Nano 去连接上去执行GATT 操作
 * 1.设备信息 {@link DeviceInfoActivity}
 * 2.设备状态 {@link DeviceStatusActivity}
 * 3.扫描配置 {@link ScanConfActivity}
 * 4.Nano中保存的扫描数据 {@link StoredScanDataActivity}
 *
 * @author collinmast,gaohui
 */
public class ConfigureActivity extends BaseActivity {

    private static Context mContext;

    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();
    private final IntentFilter disconnFilter = new IntentFilter(KSTNanoSDK.ACTION_GATT_DISCONNECTED);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure);

        mContext = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        android.support.v7.app.ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle("配置");
        actionBar.setDisplayHomeAsUpEnabled(true);

        //为listview 中的每一项添加点击事件
        ListView lv_configure = (ListView) findViewById(R.id.lv_configure);
        lv_configure.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                switch (i) {
                    case 0:
                        Intent infoIntent = new Intent(mContext, DeviceInfoActivity.class);
                        startActivity(infoIntent);
                        break;
                    case 1:
                        Intent statusIntent = new Intent(mContext, DeviceStatusActivity.class);
                        startActivity(statusIntent);
                        break;
                    case 2:
                        Intent confIntent = new Intent(mContext, ScanConfActivity.class);
                        startActivity(confIntent);
                        break;
                    case 3:
                        Intent scanDataIntent = new Intent(mContext, StoredScanDataActivity.class);
                        startActivity(scanDataIntent);
                        break;
                    default:
                        break;
                }
            }
        });

        //注册断开连接的广播接收器
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
    }

    /**
     * 当这个activity 结束时，移除BroadcastReceiver 的注册，处理断开连接事件
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
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
