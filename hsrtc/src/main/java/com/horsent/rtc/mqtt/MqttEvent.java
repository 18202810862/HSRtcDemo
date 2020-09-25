package com.horsent.rtc.mqtt;

public class MqttEvent {

    public static final int TYPE_CONNECTING = 0xA0;
    public static final int TYPE_CONNECTED = 0xA1;
    public static final int TYPE_DISCONNECTED = 0xA2;
    public static final int TYPE_ERROR = 0xA3;

    private int type;
    private String description;

    public MqttEvent(int type, String description) {
        this.type = type;
        this.description = description;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
