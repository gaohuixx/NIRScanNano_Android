package com.kstechnologies.NanoScan;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;


/**
 *
 * 这个关于页面
 *
 * @author collinmast
 */
public class AboutActivity extends AppCompatActivity {


    private static Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        mContext = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar

        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        android.support.v7.app.ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle("关于");
        actionBar.setDisplayHomeAsUpEnabled(true);

        //获取UI元素的引用
    }

    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish();
        }

        return super.onOptionsItemSelected(item);
    }

}
