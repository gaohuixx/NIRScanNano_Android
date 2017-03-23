package com.kstechnologies.NanoScan;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.webkit.WebView;
import android.webkit.WebViewClient;


/**
 *
 * 这个是关于页面
 *
 * @author gaohui
 */
public class AboutActivity extends BaseActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar); //1. 获取到toolbar
        this.setSupportActionBar(toolbar); //2. 将toolbar 设置为ActionBar
        ActionBar actionBar = this.getSupportActionBar(); // 3. 正常获取ActionBar
        actionBar.setTitle("关于");
        actionBar.setDisplayHomeAsUpEnabled(true);

        // 设置WebView
        WebView webView = (WebView) findViewById(R.id.web_view);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setBackgroundColor(0);//这样设置之后，WebView的背景就和主背景一样了
        webView.setWebViewClient(new WebViewClient());//当需要跳转到另外一个网页时，仍然在这个WebView显示，而不是跳到系统浏览器
        webView.loadUrl("file:///android_asset/index.html");
//        webView.setVisibility(View.VISIBLE);


    }


}
