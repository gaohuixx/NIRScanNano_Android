package com.gaohui.NanoScan;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.support.v7.app.ActionBar;


import java.util.ArrayList;

/**
 * 这个activity控制着信息连接。每一个信息item 将有一个标题，信息体，和关联的URL。当一个item 被点击的时候，
 * 浏览器会打开这个URL
 *
 * @author collinmast,gaohui
 */
public class InfoActivity extends BaseActivity {

    private ListView infoList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle("更多信息"); //4. 设置标题
        actionBar.setDisplayHomeAsUpEnabled(true); //5. 设置返回按钮


        infoList = (ListView) findViewById(R.id.lv_info);
    }

    @Override
    public void onResume() {
        super.onResume();
        ArrayList<InfoManager> infoManagerArrayList = new ArrayList<>();
        int length = getResources().getStringArray(R.array.info_title_array).length;
        int index;
        for (index = 0; index < length; index++) {
            infoManagerArrayList.add(new InfoManager(
                    getResources().getStringArray(R.array.info_title_array)[index],
                    getResources().getStringArray(R.array.info_body_array)[index],
                    getResources().getStringArray(R.array.info_url_array)[index]));
        }

        final InformationAdapter adapter = new InformationAdapter(this, R.layout.row_info_item, infoManagerArrayList);
        infoList.setAdapter(adapter);

        //当一个info item 被点击时，使用ACTION_VIEW intent 来启动一个URL
        infoList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent webIntent = new Intent(Intent.ACTION_VIEW);
                webIntent.setData(Uri.parse(adapter.getItem(i).getInfoURL()));
                startActivity(webIntent);
            }
        });
    }




    /**
     * 这个类用来封装信息标题，信息体，信息URL
     */
    private class InfoManager {

        private String infoTitle;//信息标题
        private String infoBody;//信息体
        private String infoURL;//信息URL

        public InfoManager(String infoTitle, String infoBody, String infoURL) {
            this.infoTitle = infoTitle;
            this.infoBody = infoBody;
            this.infoURL = infoURL;
        }

        public String getInfoTitle() {
            return infoTitle;
        }

        public String getInfoBody() {
            return infoBody;
        }

        public String getInfoURL() {
            return infoURL;
        }
    }

    /**
     * 自定义一个适配器去支持{@link InfoActivity.InfoManager}对象
     * 并且添加他们到listview
     */
    public class InformationAdapter extends ArrayAdapter<InfoManager> {
        private ViewHolder viewHolder;

        public InformationAdapter(Context context, int textViewResourceId, ArrayList<InfoManager> items) {
            super(context, textViewResourceId, items);
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(this.getContext())
                        .inflate(R.layout.row_info_item, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.infoTitle = (TextView) convertView.findViewById(R.id.tv_info_title);
                viewHolder.infoBody = (TextView) convertView.findViewById(R.id.tv_info_body);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final InfoManager item = getItem(position);
            if (item != null) {

                viewHolder.infoTitle.setText(item.getInfoTitle());
                viewHolder.infoBody.setText(item.getInfoBody());
            }
            return convertView;
        }

        /**
         * View holder for {@link InfoActivity.InfoManager} objects
         */
        private class ViewHolder {
            private TextView infoTitle;
            private TextView infoBody;
        }
    }
}
