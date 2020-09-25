package com.horsent.rtc.mqtt;

import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Date    2020/7/9
 * Author  WestWang
 * 主题事件
 */
public class TopicModel {

    public static final String KEY_MSG_ID = "id";
    public static final String KEY_MSG_SENDER = "sender";
    public static final String KEY_MSG_RECEIVER = "receiver";
    public static final String KEY_MSG_TIMESTAMP = "timestamp";
    public static final String KEY_MSG_BODY = "body";

    public static final String CODE_OK = "ok";

    private String topic;
    private String code;
    private String id;
    private String sender;
    private String receiver;
    private long timestamp;
    private String body;

    public static TopicModel obtain(String topic, MqttMessage mqttMessage) {
        TopicModel message = new TopicModel();
        message.setTopic(topic);
        // 解析
        String content = mqttMessage.toString();
        if (!TextUtils.isEmpty(content)) {
            try {
                JsonParser parser = new JsonParser();
                JsonObject object = parser.parse(content).getAsJsonObject();
                // id
                if (object.has(KEY_MSG_ID)) {
                    message.setId(object.get(KEY_MSG_ID).getAsString());
                }
                // sender
                if (object.has(KEY_MSG_SENDER)) {
                    message.setSender(object.get(KEY_MSG_SENDER).getAsString());
                }
                // receiver
                if (object.has(KEY_MSG_RECEIVER)) {
                    message.setReceiver(object.get(KEY_MSG_RECEIVER).getAsString());
                }
                // timestamp
                if (object.has(KEY_MSG_TIMESTAMP)) {
                    message.setTimestamp(object.get(KEY_MSG_TIMESTAMP).getAsLong());
                }
                // body
                if (object.has(KEY_MSG_BODY)) {
                    message.setBody(object.get(KEY_MSG_BODY).toString());
                }
                // desc
                message.setCode(CODE_OK);
            } catch (Exception e) {
                message.setCode(e.toString());
            }
        }
        return message;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "TopicEvent{" +
                "topic='" + topic + '\'' +
                ", code='" + code + '\'' +
                ", id='" + id + '\'' +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", timestamp=" + timestamp +
                ", body='" + body + '\'' +
                '}';
    }
}