package com.gaohui.utils;

/**
 * 用来转换时间的：可以将17031800200720 ( 中间的两个0 代表星期几，00 代表星期日 ) 这样的时间转换为 2017/03/18  20:07:20
 * Created by gaohui on 2017/3/19.
 */

public class TimeUtil {
    public static String convertTime(String timeStamp){
        StringBuilder sb = new StringBuilder();
        sb.append("20");
        sb.append(timeStamp, 0, 2);//年
        sb.append("/");
        sb.append(timeStamp, 2, 4);//月
        sb.append("/");
        sb.append(timeStamp, 4, 6);//日
        sb.append("  ");
        sb.append(timeStamp, 8, 10);//时
        sb.append(":");
        sb.append(timeStamp, 10, 12);//分
        sb.append(":");
        sb.append(timeStamp, 12, 14);//秒
        return sb.toString();
    }

}
