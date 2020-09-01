package com.xt.common.mqtt;

import android.os.Handler;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author xt on 2020/9/1 10:37
 */
class MqttManager {
    private static final String TAG = MqttManager.class.getSimpleName();

    private static MqttManager mMqttManager;

    private              String mServerURI;
    private              String mClientId;
    private              String mUserName;
    private              String mPassword;

    private MqttClient mqttClient;

    private List<IMqttCallback> mMqttCallbackList = new ArrayList<>();

    private Handler mHandler = new Handler();

    public static MqttManager getInstance() {
        if (mMqttManager == null) {
            mMqttManager = new MqttManager();
        }
        return mMqttManager;
    }

    /**
     * 初始化参数
     *
     * @param ipAddress
     * @param port
     * @param clientId
     * @param userName
     * @param password
     */
    public void initParams(String ipAddress, String port, String clientId, String userName, String password) {
        mServerURI = "tcp://" + ipAddress + ":" + port;
        mClientId = clientId;
        mUserName = userName;
        mPassword = password;
    }

    private void initMqttClient() {
        try {
            close();
            mqttClient = new MqttClient(mServerURI, mClientId, new MemoryPersistence());
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {

                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    Charset charset = Charset.forName("UTF-8");
                    String  msg     = new String(message.getPayload(), charset);
                    for (IMqttCallback mqttCallback : mMqttCallbackList) {
                        mqttCallback.messageArrived(topic, msg);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });
        } catch (Exception e) {
            close();
        }
    }

    private boolean isConnected() {
        if (mqttClient == null) {
            return false;
        } else {
            return mqttClient.isConnected();
        }
    }

    //连接到到服务器,退出应用前未断开mqtt连接，下次进来时，mqttClient状态为未连接，调用连接的方法时会连接失败，应该是应用上次和服务器的连接还在，退出应用是否要断开
    public boolean connectMqtt() {
        try {
            if (isConnected()) {
                return true;
            }

            initMqttClient();

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName(mUserName);
            connOpts.setServerURIs(new String[]{mServerURI});
            connOpts.setPassword(mPassword.toCharArray());
            connOpts.setCleanSession(true);
            connOpts.setKeepAliveInterval(60);
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

    void close() {
        try {
            if (mqttClient != null) {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                    mqttClient.close();
                }
                mqttClient = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            mqttClient = null;
        }
    }

    /**
     * 订阅到服务器
     *
     * @param topicFilter
     */
    void mqttSubscribe(String topicFilter) {
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
    void mqttSubscribe(String[] topicFilters) {
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
        try {
            mqttClient.subscribe(topicFilters, qos);
        } catch (Exception e) {
            e.printStackTrace();
            onConnectLost(e);
        }
    }

    //取消订阅到服务器
    void mqttUnsubscribe(String subscribeTopic) {
        try {
            mqttClient.unsubscribe(subscribeTopic);
        } catch (MqttException e) {
            e.printStackTrace();
            onConnectLost(e);
        }
    }

    //发布到服务器
    void mqttPublish(String publishTopic, String messageStr) {
        if (mqttClient == null) {
            return;
        }
//        AppLogUtil.d(TAG, "mqtt发送json---------------->" + json);
        MqttMessage message = new MqttMessage(messageStr.getBytes());
        message.setQos(1);
        if (mqttClient.isConnected()) {
            try {
                mqttClient.publish(publishTopic, message);
            } catch (Exception e) {
                e.printStackTrace();
                onConnectLost(e);
            }
        }
    }

    private void onConnectLost(Exception e) {

    }


    interface IMqttCallback {
        void messageArrived(String topic, String message);
    }

    public void addMqttCallback(IMqttCallback mqttCallback) {
        mMqttCallbackList.add(mqttCallback);
    }

    public void removeMqttCallback(IMqttCallback mqttCallback) {
        mMqttCallbackList.remove(mqttCallback);
    }
}
