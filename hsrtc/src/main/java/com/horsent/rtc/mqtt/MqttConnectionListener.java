package com.horsent.rtc.mqtt;

public interface MqttConnectionListener extends MqttDisconnectListener {

    /**
     * 连接成功
     */
    void onConnected();

    /**
     * 连接错误
     */
    void onError(String errorMsg);

}
