package com.kstechnologies.NanoScan;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;

/**
 *
 * 这个是关于页面
 *
 * @author gaohui
 */
public class AboutActivity extends BaseActivity {


    private static Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        mContext = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar

        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle("关于");
        actionBar.setDisplayHomeAsUpEnabled(true);

        //获取UI元素的引用
    }


}
