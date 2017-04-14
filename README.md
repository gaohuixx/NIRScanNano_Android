# 关于这个项目

这个项目是本人的毕设作品，是基于KST公司的开源项目[NIRScanNano_Android](https://github.com/kstechnologies/NIRScanNano_Android)进行的二次开发，主要是美化了界面，修改了几个bug，添加了几个小功能。在界面部分主要参考了[GithubTrends](https://github.com/laowch/GithubTrends)这个项目，在此一并表示感谢。

# 和原项目相比更新之处
1. 修改了界面，加入了沉浸式状态栏，侧边抽屉栏，使用AppBar代替ActionBar。所有Activity继承自我写的BaseActivity，而BaseActivity继承自AppCompatActivity
2. 加入了几个小功能，开启/关闭蓝牙，选择主题，关于页面，以及可以选择参考校准数据来源功能
3. 修改了几个Bug，首先就是我的荣耀6手机偶尔连接不上Nano，测量方法这个参数显示不合适的问题，当扫描配置大于2的时候的bug，当扫描配置索引变成两个字节时的bug，当选择了偏好设备后，连接会出问题的bug
4. 优化了部分重复代码，主要是绘图那块的
5. 重构了java文件的包名，修改了发布时的包名
6. 汉化


# 兼容性

* Android 5.0+ (Lollipop / SDK 21)
* 需要您的手机支持BLE

# 构建要求

* nirscannanolibrary.aar (1)
* NanoBLEService.java
* MPAndroidChart (2)

# License

Software License Agreement (BSD License)

Copyright (c) 2015, KS Technologies, LLC
All rights reserved.

Redistribution and use of this software in source and binary forms,
with or without modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

* Neither the name of KS Technologies, LLC nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission of KS Technologies, LLC.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.