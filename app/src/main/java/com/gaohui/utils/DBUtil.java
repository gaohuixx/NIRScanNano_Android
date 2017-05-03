package com.gaohui.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by gaohui on 2017/5/3.
 */

public class DBUtil {

    private static final String DB_PATH = "/data/data/com.gaohui.nanoscan/databases/";
    private static final String DB_NAME = "Nano.db";

    /**
     * 复制assets下数据库到data/data/packagename/databases
     *
     * @param context
     * @throws IOException
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

}
