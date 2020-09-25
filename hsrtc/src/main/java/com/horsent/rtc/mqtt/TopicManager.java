package com.horsent.rtc.mqtt;

import android.util.Log;

import com.horsent.rtc.SessionController;

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

    private String mClientId;
    private String[] mSubscribeTopics;
    private int[] mSubscribeTopicQos;

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
    public static final String TOPIC_SESSION_REPORT = "call/{group}";

    /**
     * 上线通知
     */
    public static final String TOPIC_ONLINE_REPORT = "online/{clientId}/report";

    public TopicManager(String clientId) {
        mClientId = clientId;
        //需要订阅的topic
        List<String> list = new ArrayList<>();
        list.add(getTopic(TOPIC_SESSION_REPORT));
        final int size = list.size();
        mSubscribeTopics = new String[size];
        mSubscribeTopicQos = new int[size];
        for (int i = 0; i < size; i++) {
            mSubscribeTopics[i] = list.get(i);
            mSubscribeTopicQos[i] = 0;
        }
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

    public String getOtherTopic(String topic,String deviceId) {
        if (topic == null || !topic.contains("{clientId}")) {
            return "";
        }
        return topic.replace("{clientId}", deviceId);
    }

    public void dispatch(TopicModel model){
        log("有新消息到达，" + model.toString());
        if (!TopicModel.CODE_OK.equals(model.getCode())){
            log("消息接收失败，" + model.getCode());
            return;
        }
        // 根据不同的topic执行不同的逻辑
        String topic = model.getTopic();
        if (topic != null && topic.contains(mClientId)){
            String key = topic.replace(mClientId,"{clientId}");
            switch (key){
                case TOPIC_SHELL_REQUEST:
                    //shell执行命令
                    break;
                case TOPIC_SESSION_REPORT:
                    //音视频会话通知
                    break;
            }
        }
    }

    /**
     * 发布音视频会话消息
     * @param id
     * @param receiver
     */
    public void publishVideoResponse(String id,String receiver,String mediaType){
        try {
            JSONObject bodyObject = new JSONObject();
            bodyObject.put(TopicModel.KEY_MSG_ID,id);
            bodyObject.put(TopicModel.KEY_MSG_SENDER,mClientId);
            bodyObject.put(TopicModel.KEY_MSG_RECEIVER,receiver);
            bodyObject.put(TopicModel.KEY_MSG_TIMESTAMP, System.currentTimeMillis());
            bodyObject.put(TopicModel.KEY_MSG_BODY,mediaType);
            getMqttManager().publish(getOtherTopic(TOPIC_SESSION_REPORT,receiver),QOS_0,bodyObject.toString());
        }catch (Exception e){
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

    private MqttManager getMqttManager(){
        return SessionController.getInstance().getMqttManager();
    }

    private void log(String msg) {
        Log.i(getClass().getSimpleName(), msg);
    }
}
