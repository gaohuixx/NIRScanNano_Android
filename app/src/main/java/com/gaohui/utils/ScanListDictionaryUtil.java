package com.gaohui.utils;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

import java.util.ArrayList;

/**
 * 为了将下面的列表详情页汉化，我重写了一个类
 */

public class ScanListDictionaryUtil {
    public ArrayList<KSTNanoSDK.ScanListManager> getScanList(String fileName) {
        ArrayList graphList;
        if(fileName.equals("aspirin")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "standard_scan"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "2/3/2015 @ 14:43:06"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "850.804993"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1779.879761"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "2.00"));
            return graphList;
        } else if(fileName.equals("bc")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "standard_scan"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "2/3/2015 @ 14:47:41"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "850.804993"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1779.879761"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "1.00"));
            return graphList;
        } else if(fileName.equals("bellpepper")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "col8"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "3/3/2015 @ 15:6:32"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "852.15979"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1780.73645"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "2.00"));
            return graphList;
        } else if(fileName.equals("coconutoil")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "standard_scan"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "5/3/2015 @ 11:38:43"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "853.104553"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1794.033813"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "1.00"));
            return graphList;
        } else if(fileName.equals("coffee")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "standard_scan"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "2/3/2015 @ 14:43:06"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "850.804993"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1779.879761"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "2.00"));
            return graphList;
        } else if(fileName.equals("corn_starch")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "standard_scan"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "2/3/2015 @ 14:44:27"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "850.804993"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1779.879761"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "1.00"));
            return graphList;
        } else if(fileName.equals("eucerin_lotion")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "standard_scan"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "4/3/2015 @ 21:32:00"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "853.104553"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1794.033813"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "2.00"));
            return graphList;
        } else if(fileName.equals("string.flour")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "col8"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "3/3/2015 @ 15:01:58"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "852.15979"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1780.73645"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "2.00"));
            return graphList;
        } else if(fileName.equals("glucose")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "col8"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "3/3/2015 @ 15:01:14"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "852.15979"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1780.73645"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "106"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "3.00"));
            return graphList;
        } else if(fileName.equals("out_of_spec_aspirin")) {
            graphList = new ArrayList();
            graphList.add(new KSTNanoSDK.ScanListManager("测量方法", "standard_scan"));
            graphList.add(new KSTNanoSDK.ScanListManager("时间戳", "2/3/2015 @ 14:46:55"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围起点 (nm)", "850.804993"));
            graphList.add(new KSTNanoSDK.ScanListManager("光谱范围终点 (nm)", "1779.879761"));
            graphList.add(new KSTNanoSDK.ScanListManager("波长点数", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("数字分辨率", "85"));
            graphList.add(new KSTNanoSDK.ScanListManager("平均扫描数", "1"));
            graphList.add(new KSTNanoSDK.ScanListManager("总测量时间 (s)", "1.00"));
            return graphList;
        } else {
            return null;
        }
    }
}
