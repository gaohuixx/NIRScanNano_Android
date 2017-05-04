package com.gaohui.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

/**
 * Created by gaohui on 2017/5/3.
 */

public class DBUtil {

    private static final String DB_PATH = "/data/data/com.gaohui.nanoscan/databases/";
    private static final String DB_NAME = "Nano.db";

    /**
     * 复制assets下数据库到data/data/packagename/databases
     */
    public static void copyDBToDatabases(Context context) {
        String outFileName = DB_PATH + DB_NAME;

        File file = new File(DB_PATH);
        if (!file.mkdirs()) {
            file.mkdirs();
        }

        if (new File(outFileName).exists()) {
            // 数据库已经存在，无需复制
            return;
        }

        try {
            InputStream myInput = context.getAssets().open(DB_NAME);
            OutputStream myOutput = new FileOutputStream(outFileName);

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

    public static int queryScanConfbyName(String name){
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH + DB_NAME, null);
        Cursor cursor = db.rawQuery("select id from scan_config where config_name=?", new String[]{name});


        boolean b = cursor.moveToNext();//判断是否查询出结果，一般来说只应该查出一条结果
        if (b){
            int id = cursor.getInt(cursor.getColumnIndex("id"));
            db.close();//关闭数据库
            return id;//如果查到结果就返回相应id
        }

        db.close();//关闭数据库
        return -1;//如果没有查到结果就返回-1
    }

    public static void insertExperimentResult(String experimentName, String wavelength, String reflectance, String absorbance, String intensity, int scanConfId){
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH + DB_NAME, null);
        Cursor cursor = db.rawQuery("select seq from sqlite_sequence where name='experiment_message'", null);
        cursor.moveToNext();
        int seq = cursor.getInt(cursor.getColumnIndex("seq"));//获取experiment_message 表的最新id
        String experimentId = (seq + 1) + "";

        //向experiment_message 表中插入一条记录
        db.execSQL("insert into experiment_message(date,experiment_name) values(?,?)", new String[]{new Date().toLocaleString(), experimentName});

        //向sample_data 表中插入一条记录
        db.execSQL("insert into sample_data(experiment_id,sample_no,wavelength,reflectance,absorbance,intensity,scan_config_id) values(?,?,?,?,?,?,?)", new String[]{experimentId, "1", wavelength, reflectance, absorbance, intensity, scanConfId + ""});

        db.close();//关闭数据库

    }




}
