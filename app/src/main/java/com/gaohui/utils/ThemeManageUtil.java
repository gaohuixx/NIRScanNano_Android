package com.gaohui.utils;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.gaohui.nano.R;

/**
 * Created by gaohui on 2017/3/13.
 *
 * 相当于定义了一个全局变量
 */

public class ThemeManageUtil {

    public static int currentTheme = 0;

    public static int getCurrentThemeColor(){

        switch (currentTheme){
            case R.style.AppTheme:
                return 0xff07afaf;
            case R.style.Color1:
                return 0xfffb7299;
            case R.style.Color2:
                return 0xfff44336;
            case R.style.Color3:
                return 0xffffc107;
            case R.style.Color4:
                return 0xff8bc34a;
            case R.style.Color5:
                return 0xff2194f0;
            case R.style.Color6:
                return 0xff9c27b0;
            case R.style.Color7:
                return 0xff2d2d2d;
            case R.style.Color8:
                return 0xff07afaf;
            case R.style.Color9:
                return 0xff8091b0;
        }

        return 0xff07afaf;
    }

    public static int getCurrentThemeColorInXML(){

        switch (currentTheme){
            case R.style.AppTheme:
                return R.color.default_primary;
            case R.style.Color1:
                return R.color.color1_primary;
            case R.style.Color2:
                return R.color.color2_primary;
            case R.style.Color3:
                return R.color.color3_primary;
            case R.style.Color4:
                return R.color.color4_primary;
            case R.style.Color5:
                return R.color.color5_primary;
            case R.style.Color6:
                return R.color.color6_primary;
            case R.style.Color7:
                return R.color.color7_primary;
            case R.style.Color8:
                return R.color.color8_primary;
            case R.style.Color9:
                return R.color.color9_primary;
        }

        return R.color.default_primary;
    }



    public static void setCurrentThemeToPreferenceManager(int currentTheme) {
        ThemeManageUtil.currentTheme = currentTheme;
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(ContextUtil.getInstance()).edit();
        editor.putInt("NanoCurrentTheme", currentTheme);
        editor.apply();
    }

    public static int getCurrentThemeFromPreferenceManager() {
        Context context = ContextUtil.getInstance();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        ThemeManageUtil.currentTheme = preferences.getInt("NanoCurrentTheme", R.style.AppTheme);
        return ThemeManageUtil.currentTheme;
    }
}
