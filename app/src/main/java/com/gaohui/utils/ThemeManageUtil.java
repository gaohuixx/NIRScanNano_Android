package com.gaohui.utils;

import com.kstechnologies.NanoScan.R;

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
                return 0xffaf967b;
            case R.style.Color9:
                return 0xff8091b0;
        }

        return 0xff07afaf;
    }

}
