package com.kstechnologies.NanoScan;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.kstechnologies.nirscannanolibrary.SettingsManager;

/**
 *
 * 这个avtivity 控制着一个全局设置视图，这些设置不需要一个Nano 先连接上
 *
 * 用户能够改变温度和光谱频率单元，同时也可以设置和清除一个偏爱Nano 设备
 *
 * @author collinmast,gaohui
 */
public class SettingsActivity extends BaseActivity {

    private ToggleButton tb_temp;
    private ToggleButton tb_spatial;
    private Button btn_set;
    private Button btn_forget;
    private AlertDialog alertDialog;
    private TextView tv_pref_nano;
    private String preferredNano;

    private static Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mContext = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar

        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle("设置");
        actionBar.setDisplayHomeAsUpEnabled(true);

        //获取UI元素的引用
        tb_temp = (ToggleButton) findViewById(R.id.tb_temp);//温度转换按钮
        tb_spatial = (ToggleButton) findViewById(R.id.tb_spatial);//空间频率转换按钮
        btn_set = (Button) findViewById(R.id.btn_set);//设置我的nano
        btn_forget = (Button) findViewById(R.id.btn_forget);//清除我的nano
        tv_pref_nano = (TextView) findViewById(R.id.tv_pref_nano);
    }

    @Override//当重新开始此Activity的时候调用
    public void onResume() {
        super.onResume();

        //初始化偏好设备
        preferredNano = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null);


        //初始化切换按钮状态，创建事件监听器
        tb_temp.setChecked(SettingsManager.getBooleanPref(this, SettingsManager.SharedPreferencesKeys.tempUnits, SettingsManager.CELSIUS));
        tb_spatial.setChecked(SettingsManager.getBooleanPref(this, SettingsManager.SharedPreferencesKeys.spatialFreq, SettingsManager.WAVELENGTH));

        tb_temp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SettingsManager.storeBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.tempUnits, b);
            }
        });

        tb_spatial.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SettingsManager.storeBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.spatialFreq, b);
            }
        });

        btn_set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //调到新页面去选择一个Nano
                startActivity(new Intent(mContext, ScanActivity.class));
            }
        });

        if(preferredNano == null){
            btn_forget.setEnabled(false);
        }else{
            btn_forget.setEnabled(true);
        }
        btn_forget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (preferredNano != null) {
                    confirmationDialog(preferredNano);
                }
            }
        });

        //Update set button and field based on whether a preferred nano has been set or not
        if (preferredNano != null) {
            btn_set.setVisibility(View.INVISIBLE);
            tv_pref_nano.setText(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null));
            tv_pref_nano.setVisibility(View.VISIBLE);
        } else {
            btn_set.setVisibility(View.VISIBLE);
            tv_pref_nano.setVisibility(View.INVISIBLE);
        }

    }

    /*
     * When the activity is destroyed, make a call to super class
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
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
     * In this case, there is are two items, the up indicator, and the settings button
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish();
        }

        else if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Function for displaying the dialog to confirm clearing the stored Nano
     * @param mac the mac address of the stored Nano
     */
    public void confirmationDialog(String mac) {

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle(mContext.getResources().getString(R.string.nano_confirmation_title));
        alertDialogBuilder.setMessage(mContext.getResources().getString(R.string.nano_forget_msg, mac));

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                alertDialog.dismiss();
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null);
                btn_set.setVisibility(View.VISIBLE);
                tv_pref_nano.setVisibility(View.INVISIBLE);
                btn_forget.setEnabled(false);
            }
        });

        alertDialogBuilder.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialogBuilder.setCancelable(false);

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }
}
