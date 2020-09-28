package com.horsent.rtc.mqtt;

import android.text.TextUtils;
import android.util.Log;

import com.horsent.rtc.SessionController;
import com.horsent.rtc.SessionControllerListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TopicManager {

    /**
     * 至多一次
     */
    public static final int QOS_0 = 0;
    /**
     * 至少一次
     */
    public static final int QOS_1 = 1;
    /**
     * 确保一次
     */
    public static final int QOS_2 = 2;

    //频道名Key
    public static final String KEY_CHANNEL_NAME = "channelName";
    //会话类型Key
    public static final String KEY_SESSION_TYPE = "sessionType";

    public static final String GROUP_FLAG = "重庆碧桂园";

    private String mClientId;
    private String[] mSubscribeTopics;
    private int[] mSubscribeTopicQos;

    private SessionControllerListener mSessionControllerListener;


    /**
     * shell指令执行请求
     */
    public static final String TOPIC_SHELL_REQUEST = "shell/{clientId}/request";

    /**
     * 遗嘱通知
     */
    public static final String TOPIC_WILL_REPORT = "will/{clientId}/report";

    /**
     * 音视频会话通知
     */
    public static final String TOPIC_CALL_REPORT = "call/{group}";

    /**
     * 取消/拒绝/挂断音视频会话通知
     */
    public static final String TOPIC_OUTCALL_REPORT = "outcall/{group}";

    /**
     * 上线通知
     */
    public static final String TOPIC_ONLINE_REPORT = "online/{clientId}/report";

    public TopicManager(String clientId) {
        mClientId = clientId;
        //需要订阅的topic
        List<String> list = new ArrayList<>();
        list.add(getSessionTopic(TOPIC_CALL_REPORT,GROUP_FLAG));
        list.add(getSessionTopic(TOPIC_OUTCALL_REPORT,GROUP_FLAG));
        final int size = list.size();
        mSubscribeTopics = new String[size];
        mSubscribeTopicQos = new int[size];
        for (int i = 0; i < size; i++) {
            mSubscribeTopics[i] = list.get(i);
            mSubscribeTopicQos[i] = 0;
        }
    }

    public void registerControllerListener(SessionControllerListener sessionControllerListener) {
        mSessionControllerListener = sessionControllerListener;
    }

    public void unRegisterControllerListener() {
        mSessionControllerListener = null;
    }

    public String[] getSubscribeTopics() {
        return mSubscribeTopics;
    }

    public int[] getSubscribeTopicQos() {
        return mSubscribeTopicQos;
    }

    public String getTopic(String topic) {
        if (topic == null || !topic.contains("{clientId}")) {
            return "";
        }
        return topic.replace("{clientId}", mClientId);
    }

    public String getSessionTopic(String topic, String group) {
        if (topic == null || !topic.contains("{group}")) {
            return "";
        }
        return topic.replace("{group}", GROUP_FLAG);
    }

    public void dispatch(TopicModel model) {
        log("有新消息到达，" + model.toString());
        if (!TopicModel.CODE_OK.equals(model.getCode())) {
            log("消息接收失败，" + model.getCode());
            return;
        }
        // 根据不同的topic执行不同的逻辑
        String topic = model.getTopic();

        if (topic == null) {
            return;
        }

        //普通消息
        if (topic.contains(mClientId)) {
            String key = topic.replace(mClientId, "{clientId}");
            switch (key) {
                case TOPIC_SHELL_REQUEST:
                    //shell执行命令
                    break;
            }
        }

        //会话消息
        if (topic.contains("call") || topic.contains("outcall")) {
            String key = topic.replace(GROUP_FLAG, "{group}");
            String receiver = model.getReceiver();
            switch (key) {
                case TOPIC_CALL_REPORT:
                    //音视频会话通知
                    if (!TextUtils.isEmpty(receiver) && receiver.equals(mClientId)) {
                        String body = model.getBody();
                        if (!TextUtils.isEmpty(body)) {
                            try {
                                JSONObject object = new JSONObject(body);
                                String channelName = object.optString(TopicManager.KEY_CHANNEL_NAME);
                                if (!TextUtils.isEmpty(channelName)) {
                                    if (mSessionControllerListener != null) {
                                        mSessionControllerListener.onReceiveCall(channelName, receiver);
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
                case TOPIC_OUTCALL_REPORT:
                    //取消/拒绝音视频会话
                    if (!TextUtils.isEmpty(receiver) && receiver.equals(mClientId)) {
                        if (mSessionControllerListener != null) {
                            mSessionControllerListener.onReceiveOutCall();
                        }
                    }
            }
        }
    }

    /**
     * 发布音视频会话消息
     *
     * @param receiver    接收者
     * @param mediaType   通话类型
     * @param channelName 频道名
     */
    public void publishCallResponse(String receiver, String mediaType, String channelName) {

        try {

            // body
            JSONObject bodyObj = new JSONObject();
            bodyObj.put(KEY_CHANNEL_NAME, channelName);
            bodyObj.put(KEY_SESSION_TYPE, mediaType);

            JSONObject responseObject = new JSONObject();
            responseObject.put(TopicModel.KEY_MSG_SENDER, mClientId);
            responseObject.put(TopicModel.KEY_MSG_RECEIVER, receiver);
            responseObject.put(TopicModel.KEY_MSG_TIMESTAMP, System.currentTimeMillis());
            responseObject.put(TopicModel.KEY_MSG_BODY, bodyObj);
            getMqttManager().publish(getSessionTopic(TOPIC_CALL_REPORT, GROUP_FLAG), QOS_0, responseObject.toString());
        } catch (Exception e) {
            log("publishVideoResponse failed," + e.toString());
        }
    }

    /**
     * 发布取消/拒绝/挂断会话消息
     *
     * @param receiver 接收者
     */
    public void publishOutCallResponse(String receiver) {
        try {
            JSONObject bodyObject = new JSONObject();
            bodyObject.put(TopicModel.KEY_MSG_SENDER, mClientId);
            bodyObject.put(TopicModel.KEY_MSG_RECEIVER, receiver);
            bodyObject.put(TopicModel.KEY_MSG_TIMESTAMP, System.currentTimeMillis());
            getMqttManager().publish(getSessionTopic(TOPIC_OUTCALL_REPORT, GROUP_FLAG), QOS_0, bodyObject.toString());
        } catch (Exception e) {
            log("publishVideoResponse failed," + e.toString());
        }
    }

    /**
     * 发布上线通知
     */
    public void publishOnlineReport() {
        try {
            JSONObject rootObj = new JSONObject();
            rootObj.put(TopicModel.KEY_MSG_SENDER, mClientId);
            rootObj.put(TopicModel.KEY_MSG_TIMESTAMP, System.currentTimeMillis());
            getMqttManager().publish(getTopic(TOPIC_ONLINE_REPORT), QOS_0, rootObj.toString());
        } catch (Exception e) {
            log("publishOnlineReport failed, " + e.toString());
        }
    }

    /**
     * 构建遗嘱通知
     *
     * @return String
     */
    public String obtainWillReportPayload() {
        String payload = "";
        try {
            JSONObject rootObj = new JSONObject();
            rootObj.put(TopicModel.KEY_MSG_SENDER, mClientId);
            rootObj.put(TopicModel.KEY_MSG_TIMESTAMP, System.currentTimeMillis());
            payload = rootObj.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        log("遗嘱通知: " + payload);
        return payload;
    }

    private MqttManager getMqttManager() {
        return SessionController.getInstance().getMqttManager();
    }

    private void log(String msg) {
        Log.i(getClass().getSimpleName(), msg);
    }
}
