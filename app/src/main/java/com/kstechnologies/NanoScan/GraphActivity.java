package com.kstechnologies.NanoScan;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.gaohui.utils.ScanListDictionaryUtil;
import com.gaohui.utils.ThemeManageUtil;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.renderer.XAxisRenderer;
import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

import com.kstechnologies.nirscannanolibrary.SettingsManager;

/**
 * 这个Activity控制着被保存的扫面文件的绘图
 * 一般来说，自带的raw 文件和CSV 文件都能被绘制
 * 这个Activity是用来显示CSV文件的图的
 *
 * 这个页面包含两部分：
 * 上部分：绘图区
 * 下部分：详情
 * @author collinmast,gaohui
 */
public class GraphActivity extends BaseActivity {

    private static Context mContext;

    private ListView graphListView;
    private ViewPager mViewPager;
    private String fileName;
    private ArrayList<String> mXValues;

    private ArrayList<Entry> mIntensityFloat;
    private ArrayList<Entry> mAbsorbanceFloat;
    private ArrayList<Entry> mReflectanceFloat;

    ArrayList<KSTNanoSDK.ScanListManager> graphDict = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar ab = this.getSupportActionBar(); // 3. 正常获取ActionBar


        mContext = this;

        //从intent 中获取文件名
        Intent intent = getIntent();
        fileName = intent.getStringExtra("file_name");

        //设置action bar标题，返回按钮，和导航tab
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);//设置顶部的返回箭头
            if (fileName.contains(".csv")) {
                ab.setTitle(fileName.substring(0, fileName.length()-4));
            } else {
                ab.setTitle(fileName);
            }
//            ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

            mViewPager = (ViewPager) findViewById(R.id.viewpager);
            mViewPager.setOffscreenPageLimit(2); //页面来回切换时不会重新加载

        }

        graphListView = (ListView) findViewById(R.id.lv_scan_data);
    }

    @Override
    public void onResume() {
        super.onResume();

        // 初始化 pager adapter
        CustomPagerAdapter pagerAdapter = new CustomPagerAdapter(this);
        mViewPager.setAdapter(pagerAdapter);
        mViewPager.invalidate(); //invalidate() 方法是用来刷新一个view 的

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs); //获取TabLayout
        tabLayout.setupWithViewPager(mViewPager); //将TabLayout 和ViewPager 关联

        mXValues = new ArrayList<>(); //横坐标数组，用来保存从csv文件中读取出来的数据
        ArrayList<String> mIntensityString = new ArrayList<>(); //强度数组，用来保存从csv文件中读取出来的数据
        ArrayList<String> mAbsorbanceString = new ArrayList<>(); //吸收率数组，用来保存从csv文件中读取出来的数据
        ArrayList<String> mReflectanceString = new ArrayList<>(); //反射率数组，用来保存从csv文件中读取出来的数据

        mIntensityFloat = new ArrayList<>();
        mAbsorbanceFloat = new ArrayList<>();
        mReflectanceFloat = new ArrayList<>();
        ArrayList<Float> mWavelengthFloat = new ArrayList<>();

        BufferedReader reader = null;
        BufferedReader dictReader = null;
        InputStream is = null;

        /*
         * 尝试打来一个文件，首先从raw目录，然后从外部存储的目录
         */
        try {
            is = getResources().openRawResource(getResources().getIdentifier(fileName, "raw", getPackageName()));
            reader = new BufferedReader(new InputStreamReader(is));
        } catch (Resources.NotFoundException e) {
            try {
                reader = new BufferedReader(new FileReader(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/csv/" + fileName));
                dictReader = new BufferedReader(new FileReader(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Nano/dict/" + fileName.replace(".csv", ".dict")));
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
                Toast.makeText(mContext, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        //从文件中读一行
        try {
            String line;
            if (reader != null) {
                while ((line = reader.readLine()) != null) {
                    String[] RowData = line.split(",");
                    if (RowData[0].equals("(null)")) {
                        mXValues.add("0");
                    } else {
                        if (RowData[0].equals("Wavelength")) {
                            mXValues.add(RowData[0]);
                        } else {
                            mXValues.add(getSpatialFreq(RowData[0]));//这块是用来将频率进行转换的
                        }
                    }
                    if (RowData[1].equals("(null)")) {
                        mIntensityString.add("0");
                    } else {
                        mIntensityString.add(RowData[1]);
                    }
                    if (RowData[2].equals("(null)")) {
                        mAbsorbanceString.add("0");
                    } else {
                        mAbsorbanceString.add(RowData[2]);
                    }
                    if (RowData[3].equals("(null)")) {
                        mReflectanceString.add("0");
                    } else {
                        mReflectanceString.add(RowData[3]);
                    }
                }
            }
        } catch (IOException ex) {
            // 处理异常
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                // 处理异常
            }
        }

        if (dictReader != null) {
            try {
                String line;
                while ((line = dictReader.readLine()) != null) {
                    String[] RowData = line.split(",");
                    RowData[0] = RowData[0].substring(1, RowData[0].length()-1);
                    RowData[1] = RowData[1].substring(1, RowData[1].length()-1);
                    graphDict.add(new KSTNanoSDK.ScanListManager(RowData[0], RowData[1]));
                }
            } catch (IOException ex) {
                // 处理异常
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException e) {
                    // 处理异常
                }
            }
        }

        //移除第一行，因为第一行是列名
        mXValues.remove(0);
        mIntensityString.remove(0);
        mAbsorbanceString.remove(0);
        mReflectanceString.remove(0);

        //产生数据点并计算最大最小值
        for (int i = 0; i < mXValues.size(); i++) {
            try {
            Float fIntensity = Float.parseFloat(mIntensityString.get(i));
            Float fAbsorbance = Float.parseFloat(mAbsorbanceString.get(i));
            Float fReflectance = Float.parseFloat(mReflectanceString.get(i));
            Float fWavelength = Float.parseFloat(mXValues.get(i));

            mIntensityFloat.add(new Entry(fIntensity, i));
            mAbsorbanceFloat.add(new Entry(fAbsorbance, i));
            mReflectanceFloat.add(new Entry(fReflectance, i));
            mWavelengthFloat.add(fWavelength);

            }catch (NumberFormatException e){
                Toast.makeText(GraphActivity.this, "浮点值解析错误", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        //下面这一堆代码都是用来获取最大最小值的，没用啊
/*        float minWavelength = mWavelengthFloat.get(0);
        float maxWavelength = mWavelengthFloat.get(0);

        for (Float f : mWavelengthFloat) {
            if (f < minWavelength) minWavelength = f;
            if (f > maxWavelength) maxWavelength = f;
        }

        float minAbsorbance = mAbsorbanceFloat.get(0).getVal();
        float maxAbsorbance = mAbsorbanceFloat.get(0).getVal();

        for (Entry e : mAbsorbanceFloat) {
            if (e.getVal() < minAbsorbance) minAbsorbance = e.getVal();
            if (e.getVal() > maxAbsorbance) maxAbsorbance = e.getVal();
        }

        float minReflectance = mReflectanceFloat.get(0).getVal();
        float maxReflectance = mReflectanceFloat.get(0).getVal();

        for (Entry e : mReflectanceFloat) {
            if (e.getVal() < minReflectance) minReflectance = e.getVal();
            if (e.getVal() > maxReflectance) maxReflectance = e.getVal();
        }

        float minIntensity = mIntensityFloat.get(0).getVal();
        float maxIntensity = mIntensityFloat.get(0).getVal();

        for (Entry e : mIntensityFloat) {
            if (e.getVal() < minIntensity) minIntensity = e.getVal();
            if (e.getVal() > maxIntensity) maxIntensity = e.getVal();
        }*/

        //注意：对于那些默认的数据，它是通过 ScanListDictionary 这个类来获取“详情”数据，数据是写死的！
        //这里就是获取每个文件对应的 “详情” 数据，如：aspirin
//        ArrayList<KSTNanoSDK.ScanListManager> graphList = new ScanListDictionary(this).getScanList(fileName);
        ArrayList<KSTNanoSDK.ScanListManager> graphList = new ScanListDictionaryUtil().getScanList(fileName);
        ScanListAdapter mAdapter;
        if (graphList != null) {    //这是默认数据的列表
            mAdapter = new ScanListAdapter(this, R.layout.row_graph_list_item, graphList);

            graphListView.setAdapter(mAdapter);
        } else if (graphDict != null) {     //自己测量数据的列表，graphDict 是从文件中读取出来的
            mAdapter = new ScanListAdapter(this, R.layout.row_graph_list_item, graphDict);
            graphListView.setAdapter(mAdapter);
        }

    }


    //设置菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_graph, menu);
        return true;
    }

    /*
     * 为菜单项添加监听
     * 只有一个选项，是用来发送邮件的
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_email) {
            if (findFile(fileName) != null) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("message/rfc822");
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emailing) + fileName);
                i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(findFile(fileName)));
                try {
                    startActivity(Intent.createChooser(i, getString(R.string.send_mail)));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(GraphActivity.this, getString(R.string.no_email_clients), Toast.LENGTH_SHORT).show();
                }
            } else {

                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("message/rfc822");
                //i.putExtra(Intent.EXTRA_EMAIL, new String[]{"recipient@example.com"});
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emailing) + fileName);
                //i.putExtra(Intent.EXTRA_TEXT, "body of email");
                InputStream inputStream = getResources().openRawResource(getResources().getIdentifier(fileName, "raw", getPackageName()));
                File file = new File(getExternalCacheDir(), "sample.csv");
                try {

                    OutputStream output = new FileOutputStream(file);
                    try {
                        try {
                            byte[] buffer = new byte[4 * 1024]; // or other buffer size
                            int read;

                            while ((read = inputStream.read(buffer)) != -1) {
                                output.write(buffer, 0, read);
                            }
                            output.flush();
                        } finally {
                            output.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace(); // 处理异常, define IOException and others
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                try {
                    startActivity(Intent.createChooser(i, getString(R.string.send_mail)));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(GraphActivity.this, getString(R.string.no_email_clients), Toast.LENGTH_SHORT).show();
                }
            }
        }

        return super.onOptionsItemSelected(item);
    }



    /**
     *
     * 为下方的listview 自定义一个adapter
     * 这个List 每一个元素就是一个KSTNanoSDK.ScanListManager
     */
    public class ScanListAdapter extends ArrayAdapter<KSTNanoSDK.ScanListManager> {
        private ViewHolder viewHolder;

        public ScanListAdapter(Context context, int textViewResourceId, ArrayList<KSTNanoSDK.ScanListManager> items) {
            super(context, textViewResourceId, items);
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(this.getContext())
                        .inflate(R.layout.row_graph_list_item, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.dataTitle = (TextView) convertView.findViewById(R.id.tv_list_head);
                viewHolder.dataBody = (TextView) convertView.findViewById(R.id.tv_list_data);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final KSTNanoSDK.ScanListManager item = getItem(position);
            if (item != null) {

                viewHolder.dataTitle.setText(item.getInfoTitle());
                viewHolder.dataBody.setText(item.getInfoBody());
            }
            return convertView;
        }

        private class ViewHolder {
            private TextView dataTitle;
            private TextView dataBody;
        }
    }

    /**
     * Pager enum to control tab tile and layout resource
     */
    public enum CustomPagerEnum {

        REFLECTANCE(R.string.reflectance, R.layout.page_graph_reflectance),
        ABSORBANCE(R.string.absorbance, R.layout.page_graph_absorbance),
        INTENSITY(R.string.intensity, R.layout.page_graph_intensity);

        private int mTitleResId;
        private int mLayoutResId;

        CustomPagerEnum(int titleResId, int layoutResId) {
            mTitleResId = titleResId;
            mLayoutResId = layoutResId;
        }

        public int getLayoutResId() {
            return mLayoutResId;
        }

    }

    /**
     * 自定义一个pager adapter 用来当tabs 改变时更改页面
     */
    public class CustomPagerAdapter extends PagerAdapter {

        private Context mContext;

        public CustomPagerAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            CustomPagerEnum customPagerEnum = CustomPagerEnum.values()[position];
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(customPagerEnum.getLayoutResId(), collection, false);
            collection.addView(layout);

            if (customPagerEnum.getLayoutResId() == R.layout.page_graph_intensity) {
                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartInt);
                mChart.setDrawGridBackground(false);

                // no description text
                mChart.setDescription("");
                //mChart.setNoDataTextDescription("You need to provide data for the chart.");

                // enable touch gestures
                mChart.setTouchEnabled(true);

                // enable scaling and dragging
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                // if disabled, scaling can be done on x- and y-axis separately
                mChart.setPinchZoom(true);

                // x-axis limit line
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines


                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(true);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                // limit lines are drawn behind data (and not on top)
                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);


                // add data
                setData(mChart, mXValues, mIntensityFloat, ChartType.INTENSITY);

                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // get the legend (only possible after setting data)
                Legend l = mChart.getLegend();

                // modify the legend ...
                l.setForm(Legend.LegendForm.LINE);

                return layout;
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_absorbance) {

                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartAbs);
                mChart.setDrawGridBackground(false);

                // no description text
                mChart.setDescription("");
                //mChart.setNoDataTextDescription("You need to provide data for the chart.");

                // enable touch gestures
                mChart.setTouchEnabled(true);

                // enable scaling and dragging
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                // if disabled, scaling can be done on x- and y-axis separately
                mChart.setPinchZoom(true);

                // x-axis limit line
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(false);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                // limit lines are drawn behind data (and not on top)
                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);


                // add data
                setData(mChart, mXValues, mAbsorbanceFloat, ChartType.ABSORBANCE);

                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // get the legend (only possible after setting data)
                Legend l = mChart.getLegend();

                // modify the legend ...
                l.setForm(Legend.LegendForm.LINE);
                return layout;
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_reflectance) {

                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartRef);
                mChart.setDrawGridBackground(false);

                // no description text
                mChart.setDescription("");
                //mChart.setNoDataTextDescription("You need to provide data for the chart.");

                // enable touch gestures
                mChart.setTouchEnabled(true);

                // enable scaling and dragging
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                // if disabled, scaling can be done on x- and y-axis separately
                mChart.setPinchZoom(true);

                // x-axis limit line
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(false);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                // limit lines are drawn behind data (and not on top)
                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);


                // add data
                setData(mChart, mXValues, mReflectanceFloat, ChartType.REFLECTANCE);

                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // get the legend (only possible after setting data)
                Legend l = mChart.getLegend();

                // modify the legend ...
                l.setForm(Legend.LegendForm.LINE);
                return layout;
            } else {
                return layout;
            }
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }

        @Override
        public int getCount() {
            return CustomPagerEnum.values().length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.reflectance);
                case 1:
                    return getString(R.string.absorbance);
                case 2:
                    return getString(R.string.intensity);
            }
            return null;
        }
    }

    /**
     * 为一个指定的图表设置X 轴，Y 轴数据
     * @param mChart 为那个图标更新数据
     * @param xValues 横坐标是String类型
     * @param yValues 纵坐标是数字类型
     * @param type 要显示的图表类型 {@link com.kstechnologies.NanoScan.GraphActivity.ChartType}
     */
    private void setData(LineChart mChart, ArrayList<String> xValues, ArrayList<Entry> yValues, ChartType type) {

        int themeColor = ThemeManageUtil.getCurrentThemeColor();

        if (type == ChartType.REFLECTANCE) {
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
//            set1.setColor(Color.BLACK);
            set1.setColor(themeColor);
//            set1.setCircleColor(Color.RED);
            set1.setCircleColor(themeColor);//不能写成0xffc107，前面两位是透明度，00代表全透明，ff代表不透明
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
//            set1.setFillColor(Color.RED);
            set1.setFillColor(themeColor);
            set1.setDrawFilled(true);

            ArrayList<LineDataSet> dataSets = new ArrayList<>();
            dataSets.add(set1); // add the datasets

            // create a data object with the datasets
            LineData data = new LineData(xValues, dataSets);//设置横坐标数据值，和纵坐标及数据样式

            // set data
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(20);
        } else if (type == ChartType.ABSORBANCE) {
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(themeColor);
            set1.setCircleColor(themeColor);
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(themeColor);
            set1.setDrawFilled(true);

            ArrayList<LineDataSet> dataSets = new ArrayList<>();
            dataSets.add(set1); // add the datasets

            // create a data object with the datasets
            LineData data = new LineData(xValues, dataSets);

            // set data
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(20);
        } else if (type == ChartType.INTENSITY) {
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(themeColor);
            set1.setCircleColor(themeColor);
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(themeColor);
            set1.setDrawFilled(true);

            ArrayList<LineDataSet> dataSets = new ArrayList<>();
            dataSets.add(set1); // add the datasets

            // create a data object with the datasets
            LineData data = new LineData(xValues, dataSets);

            // set data
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(20);
        } else {
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(themeColor);
            set1.setCircleColor(themeColor);
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(themeColor);
            set1.setDrawFilled(true);

            ArrayList<LineDataSet> dataSets = new ArrayList<>();
            dataSets.add(set1); // add the datasets

            // create a data object with the datasets
            LineData data = new LineData(xValues, dataSets);

            // set data
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(10);
        }
    }

    /**
     * Enumeration of chart types
     */
    public enum ChartType {
        REFLECTANCE,
        ABSORBANCE,
        INTENSITY
    }

    /**
     * 通过名字在外部存储目录中找寻找一个文件
     * @param name the name of the file to search for
     * @return File with the specified name
     */
    public File findFile(String name) {
        String nanoExtPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        File yourDir = new File(nanoExtPath, "/Nano/csv");
        for (File f : yourDir.listFiles()) {
            if (f.isFile()) {
                String fileName = f.getName();
                if (fileName.equals(name)) {
                    return f;
                }
            }
            // Do your stuff
        }
        return null;
    }

    /** 这个函数用来根据设置来返回波长或者波数
     *
     * @param freq The frequency to convert
     * @return string representing either frequency or wavenumber
     */
    private String getSpatialFreq(String freq) {
        Float floatFreq = Float.parseFloat(freq); //现把它转换成数字，然后再根据设置进行相关转换
        if (SettingsManager.getBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.spatialFreq, SettingsManager.WAVELENGTH)) {
            return String.format("%.02f", floatFreq); //波长，单位nm
        } else {
            return String.format("%.02f", (10000000 / floatFreq)); //波数，单位 cm-1
        }
    }

}
