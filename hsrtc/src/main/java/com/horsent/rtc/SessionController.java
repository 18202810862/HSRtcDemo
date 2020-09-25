package com.horsent.rtc;

import android.content.Context;

import com.horsent.rtc.mqtt.MqttManager;
import com.horsent.rtc.mqtt.TopicManager;
import com.horsent.rtc.rtc.HSRtcManager;

public class SessionController {
    private static SessionController INSTANCE = null;

    private MqttManager mMqttManager;
    private TopicManager mTopicManager;
    private HSRtcManager mHSRtcManager;

    public static SessionController getInstance() {
        if (INSTANCE == null) {
            synchronized (SessionController.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SessionController();
                }
            }
        }
        return INSTANCE;
    }

    public void init(Context context, String deviceId) {
        if (mTopicManager != null && mMqttManager != null && mMqttManager.isConnected()) {
            return;
        }
        if (mTopicManager == null) {
            mTopicManager = new TopicManager(deviceId);
        }
        if (mMqttManager == null) {
            mMqttManager = new MqttManager(deviceId);
        }
        if (mTopicManager == null) {
            mHSRtcManager = new HSRtcManager();
        }
    }

    public MqttManager getMqttManager() {
        return mMqttManager;
    }

    public TopicManager getTopicManager() {
        return mTopicManager;
    }

    public HSRtcManager getAgoraManager() {
        return mHSRtcManager;
    }
}
