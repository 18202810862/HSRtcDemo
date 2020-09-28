package com.horsent.rtc;

import android.view.SurfaceView;

/**
 * 会话流程监听
 */
public interface SessionControllerListener {

    /**
     * 收到MQTT 请求会话消息
     */
    void onReceiveCall(String channelName, String receiver);

    /**
     * 收到MQTT 拒绝/取消会话消息
     */
    void onReceiveOutCall();

    /**
     * 引擎初始化
     * @param code:错误码 0-成功
     * @param msg:错误信息
     */
    void onEngineInitComplete(int code, String msg);

    /**
     * 本端创建加入频道后调用
     */
    void onCallOutgoing();

    /**
     * 远端用户加入频道调用
     * @param localView:本端视频预览对象
     */
    void onCallConnected(SurfaceView localView);

    /**
     * 远端用户视频通道建立调用
     */
    void onCallMemberJoin(SurfaceView remoteView);

    /**
     * 远端用户离开频道
     */
    void onCallMemberOffline();
}
