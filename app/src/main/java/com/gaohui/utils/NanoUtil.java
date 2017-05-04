package com.gaohui.utils;

import com.github.mikephil.charting.data.Entry;
import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;

/**
 * Created by gaohui on 2017/3/29.
 */

public class NanoUtil {

    public static KSTNanoSDK.ReferenceCalibration getRefCal(InputStream inputStream) {

        try {
            ObjectInputStream in = new ObjectInputStream(inputStream);
            KSTNanoSDK.ReferenceCalibration refCal = (KSTNanoSDK.ReferenceCalibration) in.readObject();

            return refCal;

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    // byte[] 数组转 int ,字节数组长度是2
    public static int indexToInt(byte[] indexByteArray)
    {
        int indexInt = indexByteArray[0] & 0xFF | (indexByteArray[1] & 0xFF) << 8;
        return indexInt;
    }

    // int 数组转  byte[] ,字节数组长度是2
    public static byte[] indexToByteArray(int indexInt)
    {

        byte[] indexByteArray = new byte[] { (byte) (indexInt & 0xFF), (byte) ((indexInt >> 8) & 0xFF), };

        return indexByteArray;
    }

    public static String convertEntryResultToText(ArrayList<Entry> result){

        StringBuilder sb = new StringBuilder();
        for(Entry entry : result){
            float f = entry.getVal();
            sb.append(f);
            sb.append(" ");//这样的话最后会多出一个空格
        }

        return sb.toString().trim();//去掉最后多出的空格

    }

    public static String convertFloatResultToText(ArrayList<Float> result){

        StringBuilder sb = new StringBuilder();
        for(float f : result){
            sb.append(f);
            sb.append(" ");//这样的话最后会多出一个空格
        }

        return sb.toString().trim();//去掉最后多出的空格

    }
}