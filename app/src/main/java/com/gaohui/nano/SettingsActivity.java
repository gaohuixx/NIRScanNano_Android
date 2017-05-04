package com.gaohui.nano;

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
import android.widget.Toast;
import android.widget.ToggleButton;

import com.kstechnologies.nirscannanolibrary.SettingsManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
    private ToggleButton tb_refCal;
    private Button btn_set;
    private Button btn_forget;
    private Button btn_export;
    private AlertDialog alertDialog;
    private TextView tv_pref_nano;
    private String preferredNano;
    private boolean tb_refCal_flag;

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
        tb_refCal = (ToggleButton) findViewById(R.id.tb_refCal);//参考校准数据来源转换按钮
        btn_set = (Button) findViewById(R.id.btn_set);//设置我的nano
        btn_forget = (Button) findViewById(R.id.btn_forget);//清除我的nano
        tv_pref_nano = (TextView) findViewById(R.id.tv_pref_nano);//已经设置的Nano
        btn_export = (Button) findViewById(R.id.btn_export);//已经设置的Nano

        //添加监听
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

        tb_refCal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b && tb_refCal_flag)
                    Toast.makeText(mContext, "一旦您这样选择，请先保证您的Nano已经正确校准，否则请选择本地", Toast.LENGTH_LONG).show();
                SettingsManager.storeBooleanPref(mContext, "ReferenceCalibration", b);//true：Nano no：本地
            }
        });

        btn_set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //跳到新页面去选择一个Nano
                startActivity(new Intent(mContext, ScanActivity.class));
            }
        });

        btn_export.setOnClickListener(new View.OnClickListener() {//点击导出数据库
            @Override
            public void onClick(View view) {
                btn_export.setEnabled(false);
//                Toast.makeText(mContext, "正在导出数据库. . .", Toast.LENGTH_LONG).show();
                exportDB();
                Toast.makeText(mContext, "导出成功，位置如下：本地存储/Nano/db/Nano_export.db", Toast.LENGTH_LONG).show();
                btn_export.setEnabled(true);
            }
        });

    }

    @Override//当重新开始此Activity的时候调用
    public void onResume() {
        super.onResume();

        //初始化偏好设备
        preferredNano = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null);

        //初始化切换按钮状态
        tb_temp.setChecked(SettingsManager.getBooleanPref(this, SettingsManager.SharedPreferencesKeys.tempUnits, SettingsManager.CELSIUS));
        tb_spatial.setChecked(SettingsManager.getBooleanPref(this, SettingsManager.SharedPreferencesKeys.spatialFreq, SettingsManager.WAVELENGTH));
        tb_refCal_flag = false;//这样做是为了防止进入设置页面时也会提示
        tb_refCal.setChecked(SettingsManager.getBooleanPref(this, "ReferenceCalibration", false));
        tb_refCal_flag = true;

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

    public void exportDB(){

        File file = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/db");
        if(!file.exists())
            file.mkdirs();

        String dbExport = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/db/Nano_export.db";

        try {
            InputStream myInput = new FileInputStream("/data/data/com.gaohui.nanoscan/databases/Nano.db");
            OutputStream myOutput = new FileOutputStream(dbExport);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = myInput.read(buffer)) > 0) {
                myOutput.write(buffer, 0, length);
            }

            myOutput.flush();
            myOutput.close();
            myInput.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
