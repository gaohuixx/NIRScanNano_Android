package com.gaohui.utils;

import android.app.Application;

/**
 * Created by gaohui on 2017/3/31.
 *
 * 这个工具类是为了方便在其它程序里获取Context
 */

public class ContextUtil extends Application {

    private static ContextUtil instance;

    public static ContextUtil getInstance(){
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}
