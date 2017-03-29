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
}