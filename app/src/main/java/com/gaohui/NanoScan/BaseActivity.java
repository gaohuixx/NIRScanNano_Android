package com.gaohui.NanoScan;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import com.gaohui.utils.ThemeManageUtil;
import com.kstechnologies.nirscannanolibrary.SettingsManager;

/**
 * Created by gaohui on 2017/3/12.
 */

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        int theme = ThemeManageUtil.currentTheme;
        if(theme != 0){
            setTheme(theme);
        }
        else{
            setTheme(ThemeManageUtil.getCurrentThemeFromPreferenceManager());
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish();
        }

        return super.onOptionsItemSelected(item);
    }

}
