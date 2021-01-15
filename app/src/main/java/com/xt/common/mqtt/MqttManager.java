package com.xt.common.mqtt;

import android.os.Handler;
import android.text.TextUtils;

import com.blankj.utilcode.util.ConvertUtils;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xt on 2020/9/1 10:37
 */
public class MqttManager {
    private static final String TAG = MqttManager.class.getSimpleName();

    private static MqttManager mMqttManager;

    private String mServerURI;
    private String mClientId;
    private String mUserName;
    private String mPassword;

    private MqttClient mqttClient;

    private MyMqttCallback myMqttCallback;
    private MqttCallback innerMqttCallback = new MqttCallback() {
        @Override
        public void connectionLost(Throwable cause) {

        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Charset charset = Charset.forName("UTF-8");
            String msg = new String(message.getPayload(), charset);

            if (myMqttCallback == null) {
                return;
            }
            myMqttCallback.messageArrived(topic,msg);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {

        }
    };

//    private Handler mHandler = new Handler();//会导致闪退

    public static MqttManager getInstance() {
        if (mMqttManager == null) {
            mMqttManager = new MqttManager();
        }
        return mMqttManager;
    }

    public boolean connectMqtt(String ipAddress, String port, String clientId, String userName, String password) {
        if (isConnected()) {
            if (isParamsEqual(ipAddress, port, clientId, userName, password)) {
                return true;
            }
            close();
        }

        mServerURI = getServerURI(ipAddress, port);
        mClientId = clientId;
        mUserName = userName;
        mPassword = password;
        return connectMqtt();
    }

    //连接到到服务器,退出应用前未断开mqtt连接，下次进来时，mqttClient状态为未连接，调用连接的方法时会连接失败，应该是应用上次和服务器的连接还在，退出应用是否要断开
    public boolean connectMqtt() {
        if (isConnected()) {
            return true;
        }

        if (TextUtils.isEmpty(mServerURI)) {
            return false;
        }
        if (TextUtils.isEmpty(mClientId)) {
            return false;
        }
        if (TextUtils.isEmpty(mUserName)) {
            return false;
        }
        if (TextUtils.isEmpty(mPassword)) {
            return false;
        }

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setUserName(mUserName);
        connOpts.setServerURIs(new String[]{mServerURI});
        connOpts.setPassword(mPassword.toCharArray());
        connOpts.setCleanSession(true);
        connOpts.setKeepAliveInterval(60);

        try {
            mqttClient = new MqttClient(mServerURI, mClientId, new MemoryPersistence());
            mqttClient.setCallback(innerMqttCallback);
            IMqttToken iMqttToken = mqttClient.connectWithResult(connOpts);
            if (iMqttToken.isComplete()) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            onConnectLost(e);
            return false;
        }
    }

    public boolean isConnected() {
        if (mqttClient == null) {
            return false;
        } else {
            return mqttClient.isConnected();
        }
    }

    /**
     * 订阅到服务器
     *
     * @param topicFilter
     */
    public void mqttSubscribe(String topicFilter) {
        if (mqttClient == null) {
            return;
        }
        try {
            mqttClient.subscribe(topicFilter, 1);
//            AppLogUtil.d(TAG, "订阅成功");
        } catch (MqttException e) {
            e.printStackTrace();
            onConnectLost(e);
        }
    }

    /**
     * 订阅到服务器
     *
     * @param topicFilters
     */
    public void mqttSubscribe(String[] topicFilters) {
        if (mqttClient == null) {
            return;
        }
        if (topicFilters == null) {
            return;
        }
        int[] qos = new int[topicFilters.length];
        for (int i = 0; i < qos.length; i++) {
            qos[i] = 1;
        }
        if (isConnected()) {
            try {
                mqttClient.subscribe(topicFilters, qos);
            } catch (Exception e) {
                e.printStackTrace();
                onConnectLost(e);
            }
        }
    }

    //取消订阅到服务器
    public void mqttUnsubscribe(String topicFilter) {
        try {
            mqttClient.unsubscribe(topicFilter);
        } catch (MqttException e) {
            e.printStackTrace();
            onConnectLost(e);
        }
    }

    //取消订阅到服务器
    public void mqttUnsubscribe(String[] topicFilters) {
        try {
            mqttClient.unsubscribe(topicFilters);
        } catch (MqttException e) {
            e.printStackTrace();
            onConnectLost(e);
        }
    }

    //发布到服务器
    public void mqttPublish(String publishTopic, String messageStr) {
        mqttPublish(publishTopic,messageStr.getBytes());
    }
    //发布到服务器
    public void mqttPublishHex(String publishTopic, String strHex) {
        mqttPublish(publishTopic, ConvertUtils.hexString2Bytes(strHex));
    }
    //发布到服务器
    public void mqttPublish(String publishTopic, byte[] data) {
        if (mqttClient == null) {
            return;
        }
        if (!isConnected()) {
            return;
        }

//        AppLogUtil.d(TAG, "mqtt发送json---------------->" + json);
        MqttMessage message = new MqttMessage(data);
        message.setQos(1);
        try {
            mqttClient.publish(publishTopic, message);
        } catch (Exception e) {
            e.printStackTrace();
            close();
            onConnectLost(e);
        }
    }

    void close() {
        if (mqttClient == null) {
            return;
        }

        mqttClient.setCallback(null);

        try {
            mqttClient.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }

        try {
            mqttClient.close();
        } catch (MqttException e) {
            e.printStackTrace();
        }
        mqttClient = null;
    }

    @NotNull
    private String getServerURI(String ipAddress, String port) {
        return "tcp://" + ipAddress + ":" + port;
    }

    private boolean isParamsEqual(String ipAddress, String port, String clientId, String userName, String password) {
        if (!TextUtils.isEmpty(mServerURI)
                && !TextUtils.isEmpty(mClientId)
                && !TextUtils.isEmpty(mUserName)
                && !TextUtils.isEmpty(mPassword)) {

            if (TextUtils.equals(getServerURI(ipAddress, port), mServerURI)
                    && TextUtils.equals(clientId, mClientId)
                    && TextUtils.equals(userName, mUserName)
                    && TextUtils.equals(password, mPassword)) {

                return true;

            }

        }
        return false;
    }

    private void onConnectLost(Exception e) {

    }

    public interface MyMqttCallback{
        void messageArrived(String topic, String message);
    }

    public void setMyMqttCallback(MyMqttCallback myMqttCallback) {
        this.myMqttCallback = myMqttCallback;
    }
}
