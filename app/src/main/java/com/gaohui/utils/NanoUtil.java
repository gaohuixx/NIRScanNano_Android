package com.gaohui.utils;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

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
}