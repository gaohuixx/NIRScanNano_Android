package com.gaohui.nano;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

import com.kstechnologies.nirscannanolibrary.KSTNanoSDK;
import com.kstechnologies.nirscannanolibrary.SettingsManager;

/**
 * BLE service for interacting with a NIRScan Nano. This service is intended to be used as a
 * template for custom applications. This service create the link between the Nano and the SDK.
 * This service also serves as a link between the activities and the SDK
 *
 * This service manages the BLE connection the a Nano, while the SDK provides the command interface.
 * This means that the service is in charge of
 *
 * Commands are send from app to user using the functions in this class. Since the service handles
 * enumeration, it is important that the GATT operation return codes are checked to see if a
 * characteristic is null when issuing a command.
 *
 * This SDK also contains the JNI functions and custom classes for interfacing
 * with TI's Spectrum C Library. This library is written in C, and requires the NDK to compile.
 * The native functions in this file must match the JNI signature defined in the interface.c file.
 * Along with a correct JNI signature, the classes used to return JNI objects must match the class
 * hierarchy in order to be properly used by the NDK
 */
public class NanoBLEService extends Service {

    public static final long SCAN_PERIOD = 6000;
    ByteArrayOutputStream scanData = new ByteArrayOutputStream();//扫描数据具体内容
    ByteArrayOutputStream refConf = new ByteArrayOutputStream();//校正系数，这个名字起的不是很合理，应该叫refCoef
    ByteArrayOutputStream refMatrix = new ByteArrayOutputStream();//校正矩阵
    ByteArrayOutputStream scanConf = new ByteArrayOutputStream();//一条扫描配置具体内容
    ByteArrayOutputStream scanConfIndexs = new ByteArrayOutputStream();//保存所有扫描配置索引数据，每个索引占2 个字节

    //扫描和参考校准信息变量
    int size;//扫描数据大小，就是字节数
    int refSize;//校正系数大小，就是字节数
    int refSizeIndex;
    int refMatrixSize;//校正矩阵大小，就是字节数
    int scanConfSize;//一个扫描配置的数据长度，就是字节数，如：155
    int scanConfIndexSize;//扫描配置索引数量
    int scanConfIndexsSize;//和scanConfIndexs 一起使用，用来记录所有扫描配置索引数据一共占几个字节，每个索引占2 个字节
    int receivedConfNum;//已经接收的扫描配置数量
    int scanConfIndex;//当前扫描配置索引，即这是第几条配置
    int storedSDScanSize;
    private String scanName;
    private byte[] storedScanName;
    private String scanType;
    private String scanDate;
    private String scanPktFmtVer;

    //设备信息变量
    private String manufName;
    private String modelNum;
    private String serialNum;
    private String hardwareRev;
    private String tivaRev;
    private String spectrumRev;

    //设备状态变量
    private int battLevel;
    private float temp;
    private float humidity;
    private String devStatus;
    private String errStatus;
    private byte[] tempThresh;
    private byte[] humidThresh;

    //逻辑控制标志
    private boolean readingStoredScans = false;
    private boolean scanStarted = false;
    private boolean activeConfRequested = false;

    private static final boolean debug = true;

    public NanoBLEService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private static final String TAG = "gaohui";

    private BluetoothManager mBluetoothManager;
    private String mBluetoothDeviceAddress;

    private ArrayList<byte[]> scanConfList = new ArrayList<>();//这里面存的是扫描配置索引
    private ArrayList<byte[]> storedScanList = new ArrayList<>();//保存在SD 卡中的数据

    private static BroadcastReceiver mDataReceiver;
    private static BroadcastReceiver mInfoRequestReceiver;
    private static BroadcastReceiver mStatusRequestReceiver;
    private static BroadcastReceiver mScanConfRequestReceiver;
    private static BroadcastReceiver mStoredScanRequestReceiver;
    private static BroadcastReceiver mSetTimeReceiver;
    private static BroadcastReceiver mStartScanReceiver;
    private static BroadcastReceiver mDeleteScanReceiver;
    private static BroadcastReceiver mGetActiveScanConfReceiver;
    private static BroadcastReceiver mSetActiveScanConfReceiver;
    private static BroadcastReceiver mUpdateThresholdReceiver;
    private static BroadcastReceiver mRequestActiveConfReceiver;

    public static final String ACTION_SCAN_STARTED = "com.kstechnologies.NanoScan.bluetooth.service.ACTION_SCAN_STARTED";

    //Initialize the current scan index to a four-byte zero array
    private byte scanIndex[] = {0x00, 0x00, 0x00, 0x00};

    //CCID UUID as a string. The hyphens and lower case letters are intentional and must remain as provided.
    UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * Implements callback methods for GATT events that the app cares about.  These include
     * connection/disconnection, services discovered, and characteristic read/write/notify.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /**
         * Callback handler for connection state changes. If the new state is connected, a call to
         * discover services is made immediately
         *
         * @param gatt the Gatt of the Bluetooth Device that we care about
         * @param status The returned value of the connect/disconnect operation
         * @param newState The new connection state of the Bluetooth Device
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (debug) {
                    Log.i(TAG, "Connected to GATT server.");
                }
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        KSTNanoSDK.mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = KSTNanoSDK.ACTION_GATT_DISCONNECTED;
                refresh();//当Nano 与手机断开连接时，手机不会立刻发现，也就是说不会立刻触发这个状态
                if (debug) {
                    Log.i(TAG, "Disconnected from GATT server.");
                }
                broadcastUpdate(intentAction);
            }
        }

        /**
         * Callback handler for Gatt enumeration
         * @param gatt the Gatt profile that was enumerated after connection
         * @param status The status of the enumeration operation
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            boolean enumerated = KSTNanoSDK.enumerateServices(gatt);

            /*If enumeration was a success, send a broadcast to indicate that the enumeration is
             * complete. This should also kick off the process of subscribing to characteristic
             * notifications.
             *
             * If enumeration is not a success, print a warning if debug is enabled
             */
            if (status == BluetoothGatt.GATT_SUCCESS && enumerated) {
                if (debug)
                    Log.i(TAG, "Services discovered:SUCCESS");
                broadcastUpdate(KSTNanoSDK.ACTION_GATT_SERVICES_DISCOVERED);

                BluetoothGattDescriptor descriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGCISRetRefCalCoefficients.getDescriptor(CCCD_UUID);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(descriptor);

            } else {
                if (debug)
                    Log.e(TAG, "onServicesDiscovered received: " + status);
            }
        }

        /*
         * Handle descriptor write events.
         *
         * This process is kick-started when the GATT enumeration is complete. This allows for a
         * sequential characteristic subscription process. After all notifications have been set
         * up, send a broadcast to indicate that an activity can now kick off another process.
         *
         * It is important that these processes occur without interruption, as the BLE stack can
         * only handle one event at a time.
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (debug)
                Log.i("__onDescriptorWrite", "descriptor: " + descriptor.getUuid() + ". characteristic: " + descriptor.getCharacteristic().getUuid() + ". status: " + status);

            if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GCIS_RET_REF_CAL_COEFF) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GCIS_RET_REF_CAL_COEFF");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGCISRetRefCalMatrix.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GCIS_RET_REF_CAL_MATRIX) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GCIS_RET_REF_CAL_MATRIX");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISStartScanNotify.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSDIS_START_SCAN) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSDIS_START_SCAN");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetScanName.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSDIS_RET_SCAN_NAME) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSDIS_RET_SCAN_NAME");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetScanType.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSDIS_RET_SCAN_TYPE) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSDIS_RET_SCAN_TYPE");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetScanDate.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSDIS_RET_SCAN_DATE) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSDIS_RET_SCAN_DATE");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetPacketFormatVersion.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSDIS_RET_PKT_FMT_VER) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSDIS_RET_PKT_FMT_VER");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetSerialScanDataStruct.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSDIS_RET_SER_SCAN_DATA_STRUCT) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSDIS_RET_SER_SCAN_DATA_STRUCT");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSCISRetStoredConfList.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSCIS_RET_STORED_CONF_LIST) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSCIS_RET_STORED_CONF_LIST");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISSDStoredScanIndicesListData.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSDIS_SD_STORED_SCAN_IND_LIST_DATA) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSDIS_SD_STORED_SCAN_IND_LIST_DATA");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISClearScanNotify.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSDIS_CLEAR_SCAN) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSDIS_CLEAR_SCAN");
                BluetoothGattDescriptor mDescriptor = KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSCISRetScanConfData.getDescriptor(CCCD_UUID);
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                KSTNanoSDK.mBluetoothGatt.writeDescriptor(mDescriptor);
            } else if (descriptor.getCharacteristic().getUuid().compareTo(KSTNanoSDK.NanoGATT.GSCIS_RET_SCAN_CONF_DATA) == 0) {
                if (debug)
                    Log.i(TAG, "Wrote Notify request for GSCIS_RET_SCAN_CONF_DATA");
                Intent notifyCompleteIntent = new Intent(KSTNanoSDK.ACTION_NOTIFY_DONE);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyCompleteIntent);
            }
        }

        /**
         * Callback handler for characteristic reads
         *
         * It is important to note that some characteristic reads will kick off others. This is
         * because the calling activity requires more information, and the number of broadcasts
         * needed is reduced if all of the needed information is attached to a single broadcast
         *
         * @param gatt the Gatt of the connected device
         * @param characteristic the characteristic that was written
         * @param status the returned value of the read operation
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.DIS_MANUF_NAME)) {
                    manufName = new String(characteristic.getValue());
                    KSTNanoSDK.getModelNumber();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.DIS_MODEL_NUMBER)) {
                    modelNum = new String(characteristic.getValue());
                    KSTNanoSDK.getSerialNumber();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.DIS_SERIAL_NUMBER)) {
                    serialNum = new String(characteristic.getValue());
                    KSTNanoSDK.getHardwareRev();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.DIS_HW_REV)) {
                    hardwareRev = new String(characteristic.getValue());
                    KSTNanoSDK.getFirmwareRev();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.DIS_TIVA_FW_REV)) {
                    tivaRev = new String(characteristic.getValue());
                    KSTNanoSDK.getSpectrumCRev();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.DIS_SPECC_REV)) {
                    spectrumRev = new String(characteristic.getValue());
                    final Intent intent = new Intent(KSTNanoSDK.ACTION_INFO);
                    intent.putExtra(KSTNanoSDK.EXTRA_MANUF_NAME, manufName);
                    intent.putExtra(KSTNanoSDK.EXTRA_MODEL_NUM, modelNum);
                    intent.putExtra(KSTNanoSDK.EXTRA_SERIAL_NUM, serialNum);
                    intent.putExtra(KSTNanoSDK.EXTRA_HW_REV, hardwareRev);
                    intent.putExtra(KSTNanoSDK.EXTRA_TIVA_REV, tivaRev);
                    intent.putExtra(KSTNanoSDK.EXTRA_SPECTRUM_REV, spectrumRev);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.BAS_BATT_LVL)) {
                    byte[] data = characteristic.getValue();
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data)
                        stringBuilder.append(String.format("%02X", byteChar));
                    if (debug)
                        Log.i(TAG, "batt level:" + stringBuilder.toString());
                    battLevel = data[0];
                    KSTNanoSDK.getTemp();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GGIS_TEMP_MEASUREMENT)) {
                    byte[] data = characteristic.getValue();
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data)
                        stringBuilder.append(String.format("%02X", byteChar));
                    if (debug)
                        Log.i(TAG, "temp level string:" + stringBuilder.toString());
                    temp = (float) (data[1] << 8 | (data[0] & 0xff)) / 100;
                    if (debug)
                        Log.i(TAG, "temp level int:" + temp);
                    KSTNanoSDK.getHumidity();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GGIS_HUMID_MEASUREMENT)) {
                    byte[] data = characteristic.getValue();
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data)
                        stringBuilder.append(String.format("%02X", byteChar));
                    if (debug)
                        Log.i(TAG, "humid level:" + stringBuilder.toString());
                    humidity = (float) (data[1] << 8 | (data[0] & 0xff)) / 100;
                    KSTNanoSDK.getDeviceStatus();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GGIS_DEV_STATUS)) {
                    byte[] data = characteristic.getValue();
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data)
                        stringBuilder.append(String.format("%02X", byteChar));
                    if (debug)
                        Log.i(TAG, "dev status:" + stringBuilder.toString());
                    devStatus = stringBuilder.toString();
                    KSTNanoSDK.getErrorStatus();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GGIS_ERR_STATUS)) {
                    byte[] data = characteristic.getValue();
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data)
                        stringBuilder.append(String.format("%02X", byteChar));
                    if (debug)
                        Log.i(TAG, "error status:" + stringBuilder.toString());
                    errStatus = stringBuilder.toString();

                    final Intent intent = new Intent(KSTNanoSDK.ACTION_STATUS);
                    intent.putExtra(KSTNanoSDK.EXTRA_BATT, battLevel);
                    intent.putExtra(KSTNanoSDK.EXTRA_TEMP, temp);
                    intent.putExtra(KSTNanoSDK.EXTRA_HUMID, humidity);
                    intent.putExtra(KSTNanoSDK.EXTRA_DEV_STATUS, devStatus);
                    intent.putExtra(KSTNanoSDK.EXTRA_ERR_STATUS, errStatus);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSCIS_NUM_STORED_CONF)) {//Nano 返回扫描配置数量
                    byte[] data = characteristic.getValue();

                    scanConfIndex = 0;// TODO: 2017/5/2
                    scanConfIndexSize = (((data[1]) << 8) | (data[0] & 0xFF));
                    Intent scanConfSizeIntent = new Intent(KSTNanoSDK.SCAN_CONF_SIZE);
                    scanConfSizeIntent.putExtra(KSTNanoSDK.EXTRA_CONF_SIZE, scanConfIndexSize);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(scanConfSizeIntent);
                    if (debug)
                        Log.i(TAG, "Num stored scan configs:" + scanConfIndexSize);

                    KSTNanoSDK.requestStoredConfigurationList();//紧接着就去请求所有扫描配置，Nano 是把所有配置的索引返回，注意，不是返回具体内容
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_NUM_SD_STORED_SCANS)) {
                    byte[] data = characteristic.getValue();

                    storedSDScanSize = (((data[1]) << 8) | (data[0] & 0xFF));
                    if (debug)
                        Log.i(TAG, "Num stored SD scans:" + storedSDScanSize);
                    Intent sdScanSizeIntent = new Intent(KSTNanoSDK.SD_SCAN_SIZE);
                    sdScanSizeIntent.putExtra(KSTNanoSDK.EXTRA_INDEX_SIZE, storedSDScanSize);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(sdScanSizeIntent);
                    KSTNanoSDK.requestScanIndicesList();
                } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSCIS_ACTIVE_SCAN_CONF)) {
                    byte[] data = characteristic.getValue();// TODO: 2017/5/3 当光谱仪把Active 配置的索引发回来时触发 
                    if (!activeConfRequested) {
                        final StringBuilder stringBuilder = new StringBuilder(data.length);
                        for (byte byteChar : data)
                            stringBuilder.append(String.format("%02X", byteChar));
                        if (debug)
                            Log.i(TAG, "Active scan conf index:" + stringBuilder.toString());
                        Intent sendActiveConfIntent = new Intent(KSTNanoSDK.SEND_ACTIVE_CONF);
                        sendActiveConfIntent.putExtra(KSTNanoSDK.EXTRA_ACTIVE_CONF, data);
                        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(sendActiveConfIntent);
                    } else {
                        byte[] confIndex = {data[0], data[1]};
                        if (debug)
                            Log.i(TAG, "Writing request for scan conf at index:" + confIndex[0] + "-" + confIndex[1]);
                        KSTNanoSDK.requestScanConfiguration(confIndex);
                    }
                }
            }
        }

        /**
         * Callback handler for characteristic notify/indicate updates
         *
         * It is important to note that some characteristic reads will kick off others. This is
         * because the calling activity requires more information, and the number of broadcasts
         * needed is reduced if all of the needed information is attached to a single broadcast
         *
         * @param gatt the Gatt profile of the connected device
         * @param characteristic the characteristic that provided the notify/indicate
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (debug)
                Log.i(TAG, "onCharacteristic changed for characteristic:" + characteristic.getUuid().toString());

            if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISStartScanNotify) {
                Intent scanStartedIntent = new Intent(ACTION_SCAN_STARTED);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(scanStartedIntent);
                scanData.reset();
                refConf.reset();
                refMatrix.reset();
                size = 0;
                refSize = 0;
                refMatrixSize = 0;
                final byte[] data = characteristic.getValue();
                if (data[0] == (byte) 0xff) {
                    if (debug)
                        Log.i(TAG, "Scan data is ready to be read");
                    scanIndex[0] = data[1];
                    scanIndex[1] = data[2];
                    scanIndex[2] = data[3];
                    scanIndex[3] = data[4];
                    if (debug)
                        Log.i(TAG, "the scan index is:" + scanIndex[0] + " " + scanIndex[1] + " " + scanIndex[2] + " " + scanIndex[3]);

                    KSTNanoSDK.requestScanName(scanIndex);
                }
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetScanName) {
                final byte[] data = characteristic.getValue();

                if (debug)
                    Log.i(TAG, "Received scan name:" + new String(data));
                scanName = new String(data);

                if (readingStoredScans) {
                    storedScanName = data;
                    KSTNanoSDK.requestScanDate(storedScanList.get(0));
                } else {
                    KSTNanoSDK.requestScanType(scanIndex);
                }
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetScanType) {
                final byte[] data = characteristic.getValue();//这个扫描类型是直接从Characteristic 中读的
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X", byteChar));
                if (debug)
                    Log.i(TAG, "Received scan type:" + stringBuilder.toString());
                scanType = stringBuilder.toString();
                KSTNanoSDK.requestScanDate(scanIndex);
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetScanDate) {
                final byte[] data = characteristic.getValue();
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02d", byteChar));
                if (debug)
                    Log.i(TAG, "Received scan date:" + stringBuilder.toString());
                scanDate = stringBuilder.toString();
                if (readingStoredScans) {

                    broadcastUpdate(KSTNanoSDK.STORED_SCAN_DATA, scanDate, storedScanList.get(0));
                    storedScanList.remove(0);
                    if (storedScanList.size() > 0) {
                        KSTNanoSDK.requestScanName(storedScanList.get(0));
                    } else {
                        readingStoredScans = false;
                    }
                } else {
                    KSTNanoSDK.requestPacketFormatVersion(scanIndex);
                }
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetPacketFormatVersion) {
                final byte[] data = characteristic.getValue();
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                if (debug)
                    Log.i(TAG, "Received Packet Format Version:" + stringBuilder.toString());
                scanPktFmtVer = stringBuilder.toString();
                KSTNanoSDK.requestSerializedScanDataStruct(scanIndex);
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISRetSerialScanDataStruct) {
                final byte[] data = characteristic.getValue();
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                if (debug)
                    Log.i(TAG, "Received Serialized Scan Data Struct:" + stringBuilder.toString());
                if (data[0] == 0x00) {
                    size = (((data[2]) << 8) | (data[1] & 0xFF));
                } else {
                    int i;
                    for (i = 1; i < data.length; i++) {
                        scanData.write(data[i]);
                    }
                }

                if (scanData.size() == size) {
                    size = 0;
                    if (debug)
                        Log.i(TAG, "Done collecting scan data, sending broadcast");
                    broadcastUpdate(KSTNanoSDK.SCAN_DATA, scanData.toByteArray());//扫描数据接收完成，发送广播
                }
                if (debug)
                    Log.i("__SIZE", "new ScanData size:" + scanData.size());
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGCISRetRefCalCoefficients) {
                final byte[] data = characteristic.getValue();
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                if (debug)
                    Log.i(TAG, "Received Reference calibration coefficients:" + stringBuilder.toString());

                if (data[0] == 0x00) {//第一次接收到执行这个
                    refConf.reset();
                    refSizeIndex = 0;
                    refSize = (((data[2]) << 8) | (data[1] & 0xFF));//获取校验系数大小
                    Intent requestCalCoef = new Intent(KSTNanoSDK.ACTION_REQ_CAL_COEFF);
                    requestCalCoef.putExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE, refSize);
                    requestCalCoef.putExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE_PACKET, true);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(requestCalCoef);
                } else {//然后执行这个，和NewActivity 的广播接收器是对应的
                    int i;
                    for (i = 1; i < data.length; i++) {
                        refConf.write(data[i]);
                    }
                    Intent requestCalCoef = new Intent(KSTNanoSDK.ACTION_REQ_CAL_COEFF);
                    requestCalCoef.putExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE, data.length - 1);
                    requestCalCoef.putExtra(KSTNanoSDK.EXTRA_REF_CAL_COEFF_SIZE_PACKET, false);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(requestCalCoef);
                }

                if (refConf.size() == refSize) {
                    refSize = 0;
                    if (debug)
                        Log.i(TAG, "Done collecting reference, sending broadcast");
                    KSTNanoSDK.requestRefCalMatrix();//接收完之后就去请求校正矩阵
                }
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGCISRetRefCalMatrix) {
                final byte[] data = characteristic.getValue();
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                if (debug)
                    Log.i(TAG, "Received Reference calibration matrix:" + stringBuilder.toString());

                if (data[0] == 0x00) {
                    refMatrix.reset();
                    refMatrixSize = (((data[2]) << 8) | (data[1] & 0xFF));
                    Intent requestCalMatrix = new Intent(KSTNanoSDK.ACTION_REQ_CAL_MATRIX);
                    requestCalMatrix.putExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE, refMatrixSize);
                    requestCalMatrix.putExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE_PACKET, true);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(requestCalMatrix);
                } else {
                    int i;
                    for (i = 1; i < data.length; i++) {
                        refMatrix.write(data[i]);
                    }
                    Intent requestCalCoef = new Intent(KSTNanoSDK.ACTION_REQ_CAL_MATRIX);
                    requestCalCoef.putExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE, data.length - 1);
                    requestCalCoef.putExtra(KSTNanoSDK.EXTRA_REF_CAL_MATRIX_SIZE_PACKET, false);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(requestCalCoef);
                }

                if (refMatrix.size() == refMatrixSize) {
                    refSize = 0;
                    if (debug)//校正系数和校正矩阵都完事后就发送广播
                        Log.i(TAG, "Done collecting reference Matrix, sending broadcast");
                    broadcastUpdate(KSTNanoSDK.REF_CONF_DATA, refConf.toByteArray(), refMatrix.toByteArray());
                }
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSCISRetStoredConfList) {
                //Nano返回扫描配置数据索引，如果数据量少的话就只会返回来两次
                //第一次：返回数据量大小，即字节数
                //第二次：开始返回具体索引，每个索引占两个字节，一次最多返回20个字节，而且第一个字节代表是第几次，所以第二次就能返回9个配置，所以我说两次就够了，如果再多可能就需要第三次了
                final byte[] data = characteristic.getValue();// TODO: 2017/3/30
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                if (debug)
                    Log.i(TAG, "Received Scan Conf index:" + stringBuilder.toString());

                if (data[0] == 0x00) {//如果是第一行，就获取数据大小
                    scanConfIndexs.reset();
                    scanConfIndexsSize = (((data[2]) << 8) | (data[1] & 0xFF));//获取数据块的大小
                } else {//如果不是第一行，就是具体数据行
                    int i;
                    for (i = 1; i < data.length; i++) {
                        scanConfIndexs.write(data[i]);
                    }
                }

                if (scanConfIndexs.size() == scanConfIndexsSize) {//说明获取完毕
                    byte[] configs = scanConfIndexs.toByteArray();

                    final StringBuilder stringBuilder2 = new StringBuilder(data.length);
                    for (byte byteChar : configs)
                        stringBuilder2.append(String.format("%02X ", byteChar));
                    if (debug)
                        Log.i(TAG, "已接收到的扫描配置索引为:" + stringBuilder2.toString());

                    byte[] confIndex = {0, 0};
                    confIndex[0] = configs[0];
                    confIndex[1] = configs[1];

                    //这个方式才是具体去根据一个索引去获取具体的配置内容，这里是去获取第一条
                    KSTNanoSDK.requestScanConfiguration(confIndex);

                }


//                scanConfList.add(data);//这里一共就存了两行数据，索引的数据都在第二行
//
//                if (data[0] != 0x00){//data[0] == 0x00 说明是第一行
//                    scanConfIndex = 1;
//                    byte[] confIndex = {0, 0};
//                    confIndex[0] = scanConfList.get(scanConfIndex)[1];
//                    confIndex[1] = scanConfList.get(scanConfIndex)[2];
//
//                    if (debug)
//                        Log.i(TAG, "Writing request for scan conf at index:" + confIndex[0] + "-" + confIndex[1]);
//                    KSTNanoSDK.requestScanConfiguration(confIndex);//这个方式才是具体去根据一个索引去获取具体的配置内容
//                }


            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISSDStoredScanIndicesListData) {
                final byte[] data = characteristic.getValue();
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                if (debug)
                    Log.i(TAG, "Received SD scan indices list:" + stringBuilder.toString());
                int index;
                for (index = 0; index < data.length / 4; index++) {
                    byte[] sdIndex = {data[index * 4], data[(index * 4) + 1], data[(index * 4) + 2], data[(index * 4) + 3]};

                    storedScanList.add(sdIndex);
                    if (debug)
                        Log.i(TAG, "new storedScanList size:" + storedScanList.size());
                }
                if (storedScanList.size() == storedSDScanSize) {
                    byte[] indexData = storedScanList.get(0);
                    readingStoredScans = true;
                    KSTNanoSDK.requestScanName(indexData);
                }

            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSCISRetScanConfData) {//Nano 返回一条扫描配置数据，具体数据内容
                final byte[] data = characteristic.getValue();// TODO: 2017/3/27
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                if (debug)
                    Log.i(TAG, "Received Scan Conf Data:" + stringBuilder.toString());

                if (data[0] == 0x00) {
                    scanConf.reset();
                    scanConfSize = (((data[2]) << 8) | (data[1] & 0xFF));//第一行是数据的大小
                } else {
                    int i;
                    for (i = 1; i < data.length; i++) {
                        scanConf.write(data[i]);
                    }
                }

                if (scanConf.size() == scanConfSize) {//扫描配置数据接收完毕

                    activeConfRequested = false;

                    if (!activeConfRequested) {//如果不是active 配置请求
                        scanConfSize = 0;//接收完之后，scanConfSize 归零
                        if (debug)
                            Log.i(TAG, "Done collecting scanConfiguration, sending broadcast");
                        broadcastScanConfig(KSTNanoSDK.SCAN_CONF_DATA, scanConf.toByteArray());//把这条配置具体内容发送给Activity
                        receivedConfNum++;//已接收的扫描配置数量+1

                        if (receivedConfNum < scanConfIndexSize) {
                            byte[] configs = scanConfIndexs.toByteArray();
                            byte[] confIndex = {0, 0};
                            confIndex[0] = configs[2*receivedConfNum];
                            confIndex[1] = configs[2*receivedConfNum + 1];

                            //这个方式才是具体去根据一个索引去获取具体的配置内容，这里是去获取第receivedConfNum+1 条
                            KSTNanoSDK.requestScanConfiguration(confIndex);
                        }else{//此时说明已经接收完了，置0，没有也行
                            scanConfIndex = 0;
                            receivedConfNum = 0;
                        }


//                        if (scanConfIndex < scanConfIndexSize) {
//                            scanConfIndex++;//当前要接收的第几个数据
//                            byte[] confIndex = {0, 0};
//                            if (debug)
//                                Log.i(TAG, "Retrieving scan at index:" + scanConfIndex + " Size is:" + scanConfList.size());
//                            confIndex[0] = scanConfList.get(1)[2 * scanConfIndex - 1];
//                            confIndex[1] = scanConfList.get(1)[2 * scanConfIndex];
//                            if (debug)
//                                Log.i(TAG, "Writing request for scan conf at index:" + confIndex[0] + "-" + confIndex[1]);
//                            KSTNanoSDK.requestScanConfiguration(confIndex);
//                        } else {
//                            scanConfIndex = 0;
//                        }
                    } else {    //如果是active 配置请求
                        scanConfSize = 0;
                        if (debug)
                            Log.i(TAG, "Done collecting active scanConfiguration");
                        broadcastScanConfig(KSTNanoSDK.SCAN_CONF_DATA, scanConf.toByteArray());
                        scanConfIndex = 0;
                        activeConfRequested = false;
                    }
                }
            } else if (characteristic == KSTNanoSDK.NanoGattCharacteristic.mBleGattCharGSDISClearScanNotify) {
                final byte[] data = characteristic.getValue();
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                if (debug)
                    Log.i(TAG, "Received status from clear scan:" + stringBuilder.toString());
            } else {
                if (debug)
                    Log.i(TAG, "Received notify/indicate from unknown characteristic:" + characteristic.getUuid().toString());
            }
        }

        /**
         * Callback handler for characteristic writes
         *
         * It is important to note that some characteristic reads will kick off others. This is
         * because the calling activity requires more information, and the number of broadcasts
         * needed is reduced if all of the needed information is attached to a single broadcast
         *
         * @param gatt the Gatt profile of the connected device
         * @param characteristic the characteristic that was written
         * @param status the status of the write operation
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_START_SCAN)) {
                if (debug)
                    Log.i(TAG, "Wrote start scan! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_REQ_SER_SCAN_DATA_STRUCT)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for Scan Data Struct! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_REQ_SCAN_TYPE)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for Scan Type! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_REQ_SCAN_NAME)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for Scan Name! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_REQ_SCAN_DATE)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for Scan Date! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_REQ_PKT_FMT_VER)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for Packet Format Version! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GCIS_REQ_REF_CAL_COEFF)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for Reference Calibration Coefficients! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GCIS_REQ_REF_CAL_MATRIX)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for Reference Calibration Matrix! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSCIS_REQ_STORED_CONF_LIST)) {
                Log.i(TAG, "Wrote Request for Scan configuration list! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_SD_STORED_SCAN_IND_LIST)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for SD Stored scan indices list! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSCIS_REQ_SCAN_CONF_DATA)) {
                if (debug)
                    Log.i(TAG, "Wrote Request for Scan Conf data! Status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GDTS_TIME)) {
                if (debug)
                    Log.i(TAG, "Wrote Time! Status=" + status);
                String dataString = SettingsManager.getStringPref(getApplicationContext(), SettingsManager.SharedPreferencesKeys.prefix, "Data");
                if (dataString.equals("")) {
                    dataString = "Data";
                }
                byte[] data = new StringBuilder(dataString).reverse().toString().getBytes();
                if (debug)
                    Log.i(TAG, "Writing scan stub to:" + dataString);
                KSTNanoSDK.setStub(data);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_SET_SCAN_NAME_STUB)) {
                if (debug)
                    Log.i(TAG, "Wrote Scan Name Stub! Status=" + status);
                if (!scanStarted) {
                    if (debug)
                        Log.i(TAG, "Requesting Calibration Data");
                    KSTNanoSDK.requestRefCalCoefficients();
                } else {
                    scanStarted = false;
                    if (debug)
                        Log.i(TAG, "Starting Scan");
                    byte[] data = {0x00};
                    if (SettingsManager.getBooleanPref(getApplicationContext(), SettingsManager.SharedPreferencesKeys.saveSD, false)) {
                        data[0] = 0x01;
                        if (debug)
                            Log.i(TAG, "Save to SD selected, writing 1");
                    } else {
                        data[0] = 0x00;
                        if (debug)
                            Log.i(TAG, "Save to SD not selected, writing 0");
                    }
                    scanData.reset();
                    refConf.reset();
                    refMatrix.reset();
                    size = 0;
                    refSize = 0;
                    refMatrixSize = 0;

                    KSTNanoSDK.startScan(data);
                }
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSDIS_CLEAR_SCAN)) {
                if (debug)
                    Log.i(TAG, "wrote clear scan! status=" + status);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GSCIS_ACTIVE_SCAN_CONF)) {
                if (debug)
                    Log.i(TAG, "Wrote set active scan conf! status=" + status);
                KSTNanoSDK.getActiveConf();//设置完之后再获取一次，这样就能把页面中的显示给刷新了
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GGIS_TEMP_THRESH)) {
                if (debug)
                    Log.i(TAG, "Wrote Temperature threshold! status=" + status);
                KSTNanoSDK.setHumidityThreshold(humidThresh);
            } else if (characteristic.getUuid().equals(KSTNanoSDK.NanoGATT.GGIS_HUMID_THRESH)) {
                if (debug)
                    Log.i(TAG, "Wrote Humidity threshold! status=" + status);
            } else {
                if (debug)
                    Log.i(TAG, "Unknown characteristic");
            }
        }
    };

    /**
     * Sends the desired broadcast action without any extras
     *
     * @param action the action to broadcast
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Sends the desired broadcast action with the data read from the specified characteristic as
     * an extra. If a particular characteristic has a particular format, the characteristic can be
     * examined to determine how to properly format and send the data from the characteristic
     *
     * @param action         the action to broadcast
     * @param characteristic the characteristic to retrieve data from to send
     */
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // For all other profiles, writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            if (debug)
                Log.i(TAG, "Notify characteristic:" + characteristic.getUuid().toString() + " -- Notify data:" + stringBuilder.toString());
            intent.putExtra(KSTNanoSDK.EXTRA_DATA, stringBuilder.toString());
        }

        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Sends the desired broadcast action with the data provided by scanData
     *
     * @param action   the action to broadcast
     * @param scanData the data to add to the broadcast
     */
    private void broadcastUpdate(final String action,
                                 byte[] scanData) {
        final Intent intent = new Intent(action);//触发扫描完成广播，并把数据带过去
        intent.putExtra(KSTNanoSDK.EXTRA_DATA, scanData);//扫描数据
        intent.putExtra(KSTNanoSDK.EXTRA_SCAN_NAME, scanName);//扫描名字
        intent.putExtra(KSTNanoSDK.EXTRA_SCAN_TYPE, scanType);//扫描类型
        intent.putExtra(KSTNanoSDK.EXTRA_SCAN_DATE, scanDate);//扫描日期
        intent.putExtra(KSTNanoSDK.EXTRA_SCAN_FMT_VER, scanPktFmtVer);//不知道他这个是干什么的
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Sends the desired broadcast action with the data provided parameters
     *
     * @param action   the action to broadcast
     * @param scanData byte array of data to broadcast
     */
    private void broadcastScanConfig(final String action,
                                     byte[] scanData) {
        final Intent intent = new Intent(action);
        intent.putExtra(KSTNanoSDK.EXTRA_DATA, scanData);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Sends the desired broadcast action with the data provided parameters
     *
     * @param action    the action to broadcast
     * @param refCoeff  byte array of reference coefficients
     * @param refMatrix byte array of reference calibration matrix
     */
    private void broadcastUpdate(final String action,
                                 byte[] refCoeff, byte[] refMatrix) {
        final Intent intent = new Intent(action);
        intent.putExtra(KSTNanoSDK.EXTRA_DATA, scanData.toByteArray());
        intent.putExtra(KSTNanoSDK.EXTRA_REF_COEF_DATA, refCoeff);
        intent.putExtra(KSTNanoSDK.EXTRA_REF_MATRIX_DATA, refMatrix);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Sends the desired broadcast action with the data provided parameters
     *
     * @param action   the action to broadcast
     * @param scanDate the scan date to be added to the broadcast
     * @param index    the scan index to be added to the broadcast
     */
    private void broadcastUpdate(final String action, String scanDate, byte[] index) {
        final Intent intent = new Intent(action);
        intent.putExtra(KSTNanoSDK.EXTRA_SCAN_NAME, nameToUTF8(storedScanName));
        intent.putExtra(KSTNanoSDK.EXTRA_SCAN_DATE, scanDate);
        intent.putExtra(KSTNanoSDK.EXTRA_SCAN_INDEX, index);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Instance of the binder to be used when binding to the service in app
     */
    public class LocalBinder extends Binder {
        public NanoBLEService getService() {
            return NanoBLEService.this;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        if (debug)
            Log.i(TAG, "onUnbind called");
        //disconnect();
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        if (debug)
            Log.i(TAG, "Initializing BLE");
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        KSTNanoSDK.mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (KSTNanoSDK.mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (KSTNanoSDK.mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && KSTNanoSDK.mBluetoothGatt != null) {
            if (debug)
                Log.i(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return KSTNanoSDK.mBluetoothGatt.connect();
        }

        //这里又通过MAC 地址获取了这个设备对象BluetoothDevice
        final BluetoothDevice device = KSTNanoSDK.mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            if (debug)
                Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.i(TAG, "Using LE Transport");
            KSTNanoSDK.mBluetoothGatt = device.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            //这里获取到BluetoothGatt，然后给KSTNanoSDK.mBluetoothGatt，它相当于一个全局变量
            KSTNanoSDK.mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        }
        if (debug)
            Log.i(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * 在这里初始化一堆广播接收器
     */
    @Override
    public void onCreate() {
        super.onCreate();
        if (debug)
            Log.i(TAG, "onCreate called");

        mDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i) {
                if (i != null) {
                    if (debug)
                        Log.i(TAG, "Starting Scan");
                    byte[] data = {0x00};
                    if (SettingsManager.getBooleanPref(context, SettingsManager.SharedPreferencesKeys.saveSD, false)) {
                        data[0] = 0x01;
                        if (debug)
                            Log.e(TAG, "Save to SD selected, writing 1");
                    } else {
                        data[0] = 0x00;
                        if (debug)
                            Log.e(TAG, "Save to SD not selected, writing 0");
                    }
                    scanData.reset();
                    refConf.reset();
                    refMatrix.reset();
                    size = 0;
                    refSize = 0;
                    refMatrixSize = 0;

                    KSTNanoSDK.startScan(data);
                }
            }
        };

        mInfoRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    if (debug)
                        Log.i(TAG, "Requesting Device Info");
                    KSTNanoSDK.getManufacturerName();
                }
            }
        };

        mStatusRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    if (debug)
                        Log.i(TAG, "Requesting Device Status");
                    KSTNanoSDK.getBatteryLevel();
                }
            }
        };

        mScanConfRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    if (debug)
                        Log.i(TAG, "Requesting Device Status");
                    KSTNanoSDK.getNumberStoredConfigurations();//请求Nano中的配置数量
                }
            }
        };

        mStoredScanRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    if (debug)
                        Log.i(TAG, "Requesting Stored Scans");
                    KSTNanoSDK.getNumberStoredScans();
                }
            }
        };

        mSetTimeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    if (debug)
                        Log.i(TAG, "writing time to nano");
                    KSTNanoSDK.setTime();
                }
            }
        };

        mStartScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                scanStarted = true;
                String dataString = SettingsManager.getStringPref(getApplicationContext(), SettingsManager.SharedPreferencesKeys.prefix, "Nano");
                if (dataString.equals("")) {
                    dataString = "Nano";
                }
                byte[] data = new StringBuilder(dataString).reverse().toString().getBytes();
                if (debug)
                    Log.i(TAG, "Writing scan stub to:" + dataString);
                KSTNanoSDK.setStub(data);
            }
        };

        mDeleteScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                byte[] index = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_SCAN_INDEX);
                if (debug)
                    Log.i(TAG, "deleting index:" + index[0] + "-" + index[1] + "-" + index[2] + "-" + index[3]);
                KSTNanoSDK.deleteScan(index);
            }
        };

        mGetActiveScanConfReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (debug)
                    Log.i(TAG, "Reading active scan conf");
                KSTNanoSDK.getActiveConf();

            }
        };

        mSetActiveScanConfReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (debug)
                    Log.i(TAG, "Setting active scan conf");
                byte[] data = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_SCAN_INDEX);
                KSTNanoSDK.setActiveConf(data);
            }
        };
        mUpdateThresholdReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (debug)
                    Log.i(TAG, "Updating Thresholds");
                tempThresh = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_TEMP_THRESH);
                humidThresh = intent.getByteArrayExtra(KSTNanoSDK.EXTRA_HUMID_THRESH);
                KSTNanoSDK.setTemperatureThreshold(tempThresh);
            }
        };

        mRequestActiveConfReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                activeConfRequested = true;//表明是要获取active配置
                KSTNanoSDK.getActiveConf();
            }
        };

        //Register all needed receivers
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mDataReceiver, new IntentFilter(KSTNanoSDK.SEND_DATA));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mInfoRequestReceiver, new IntentFilter(KSTNanoSDK.GET_INFO));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mStatusRequestReceiver, new IntentFilter(KSTNanoSDK.GET_STATUS));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mScanConfRequestReceiver, new IntentFilter(KSTNanoSDK.GET_SCAN_CONF));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mStoredScanRequestReceiver, new IntentFilter(KSTNanoSDK.GET_STORED_SCANS));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mSetTimeReceiver, new IntentFilter(KSTNanoSDK.SET_TIME));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mStartScanReceiver, new IntentFilter(KSTNanoSDK.START_SCAN));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mDeleteScanReceiver, new IntentFilter(KSTNanoSDK.DELETE_SCAN));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mGetActiveScanConfReceiver, new IntentFilter(KSTNanoSDK.GET_ACTIVE_CONF));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mSetActiveScanConfReceiver, new IntentFilter(KSTNanoSDK.SET_ACTIVE_CONF));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mUpdateThresholdReceiver, new IntentFilter(KSTNanoSDK.UPDATE_THRESHOLD));
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mRequestActiveConfReceiver, new IntentFilter(KSTNanoSDK.REQUEST_ACTIVE_CONF));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (debug)
            Log.i(TAG, "onDestroy called");

        //Clean up the registered receivers
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mDataReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mInfoRequestReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mStatusRequestReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mScanConfRequestReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mStoredScanRequestReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mSetTimeReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mStartScanReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mDeleteScanReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mGetActiveScanConfReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mSetActiveScanConfReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mUpdateThresholdReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mRequestActiveConfReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (debug)
            Log.i(TAG, "onStartCommand called");

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (debug)
            Log.i(TAG, "onTaskRemoved called");
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (KSTNanoSDK.mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter is null");
        }
        if (KSTNanoSDK.mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt is null");
        }
        if (KSTNanoSDK.mBluetoothAdapter == null || KSTNanoSDK.mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        KSTNanoSDK.mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (KSTNanoSDK.mBluetoothGatt == null) {
            return;
        }
        KSTNanoSDK.mBluetoothGatt.close();
        KSTNanoSDK.mBluetoothGatt = null;
    }

    /**
     * Convert byte array of name to UTF8 string
     *
     * @param data the scan name as a byte array
     * @return String in UTF8 of scan name bytes
     */
    private String nameToUTF8(byte[] data) {

        byte[] byteChars = new byte[data.length];
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        for (byte b : byteChars) {
            byteChars[b] = 0x00;
        }
        String s = null;
        for (int i = 0; i < data.length; i++) {

            byteChars[i] = data[i];

            if (data[i] == 0x00) {
                break;
            }

            os.write(data[i]);
        }

        try {
            s = new String(os.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return s;
    }

    /**
     * @param gatt the Bluetooth GATT object to call the refresh reflection method on
     * @return Boolean status of refresh operation; true = success, false = failure.
     */
    private boolean refreshDeviceCache(BluetoothGatt gatt) {
        try {
            BluetoothGatt localBluetoothGatt = gatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh");
            if (localMethod != null) {
                boolean bool = ((Boolean) localMethod.invoke(localBluetoothGatt)).booleanValue();
                return bool;
            }
        } catch (Exception localException) {
            Log.e(TAG, "An exception occurred while refreshing device");
        }
        return false;
    }

    /**
     * Method to refresh GATT device cache using known BluetoothGatt {@link KSTNanoSDK#mBluetoothGatt}
     */
    private void refresh() {
        refreshDeviceCache(KSTNanoSDK.mBluetoothGatt);
    }
}
