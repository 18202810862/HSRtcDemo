package com.horsent.rtc;

import android.content.Context;
import android.text.TextUtils;
import android.view.SurfaceView;

import com.horsent.rtc.mqtt.MqttManager;
import com.horsent.rtc.mqtt.TopicManager;
import com.horsent.rtc.rtc.HSRtcManager;
import com.horsent.rtc.statemachine.DeviceStateMachine;

/**
 * 会话controller
 * 视频通话过程所有业务逻辑操作类
 */
public class SessionController {
    private static SessionController INSTANCE = null;

    private MqttManager mMqttManager;
    private TopicManager mTopicManager;
    private HSRtcManager mHSRtcManager;
    private DeviceStateMachine mDeviceStateMachine;
    private HSSessionListener mHsSessionListener;

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

    public void init(Context context, String deviceId, String appId) {
        if (mTopicManager != null && mMqttManager != null && mMqttManager.isConnected()) {
            return;
        }
        if (mTopicManager == null) {
            mTopicManager = new TopicManager(deviceId);
            mTopicManager.registerControllerListener(mSessionControllerListener);
        }
        if (mMqttManager == null) {
            mMqttManager = new MqttManager(deviceId);
        }
        if (mHSRtcManager == null) {
            mHSRtcManager = new HSRtcManager();
            mHSRtcManager.init(context, appId);
            mHSRtcManager.setSessionControllerListener(mSessionControllerListener);
        }

        if (mDeviceStateMachine == null) {
            mDeviceStateMachine = DeviceStateMachine.makeDevice();
        }
    }

    public void setHsSessionListener(HSSessionListener hsSessionListener){
        mHsSessionListener = hsSessionListener;
    }

    private SessionControllerListener mSessionControllerListener = new SessionControllerListener() {

        /**
         * 收到MQTT 请求会话消息
         */
        @Override
        public void onReceiveCall(String channelName, String receiver) {

            if (mTopicManager == null || mDeviceStateMachine == null || mMqttManager == null || mHSRtcManager == null) {
                return;
            }
            if (mDeviceStateMachine.isIdelState()) {
                //空闲状态才处理音视频会话请求(或者是之前推送消息设备回传消息)
                //状态转换为waiting状态
                mDeviceStateMachine.sendMessage(mDeviceStateMachine.obtainMessage(DeviceStateMachine.MSG_RECEIVE_CALL));
                //加入频道(临时逻辑,方便测试主流程)
                mHSRtcManager.joinChannel(channelName);
                //回执加入频道信息(暂时先不加,测试少发一次mqtt时间会用多少)
            }
        }

        /**
         * 收到MQTT 取消/拒绝会话消息 (当接通后挂断会走rtc的onUserOffline()回调通知，所以暂时不选择在发一次MQTT消息通知)
         */
        @Override
        public void onReceiveOutCall() {
            if (mDeviceStateMachine.isWaittingState()) {
                //状态转换为idel状态
                mDeviceStateMachine.sendMessage(mDeviceStateMachine.obtainMessage(DeviceStateMachine.MSG_CANCEL_CALL));
                //回调接口切换页面
            }
        }

        /**
         * 引擎初始化
         * @param code:错误码 0-成功
         * @param msg:错误信息
         */
        @Override
        public void onEngineInitComplete(int code, String msg) {
            if (mHsSessionListener != null){
                mHsSessionListener.onEngineInitComplete(code,msg);
            }

        }

        /**
         *  本端创建加入频道发出呼叫请求
         */
        @Override
        public void onCallOutgoing() {
            if (mHsSessionListener != null){
                mHsSessionListener.onCallOutgoing();
            }
        }

        /**
         * 远端用户加入频道
         */
        @Override
        public void onCallConnected(SurfaceView localView) {
            if (mDeviceStateMachine.isWaittingState()){
                //状态转换为online状态
                mDeviceStateMachine.sendMessage(mDeviceStateMachine.obtainMessage(DeviceStateMachine.MSG_ANSWER_CALL));
            }
            if (mHsSessionListener != null){
                mHsSessionListener.onCallConnected(localView);
            }
        }

        /**
         * 音视频连通
         */
        @Override
        public void onCallMemberJoin(SurfaceView remoteView) {
            //远端视频surfaceView回调给页面使用
            if (mHsSessionListener != null){
                mHsSessionListener.onCallMemberJoin(remoteView);
            }
        }

        /**
         * 远端用户离开
         */
        @Override
        public void onCallMemberOffline() {
            if (mDeviceStateMachine.isOnlineState()){
                //状态转换为idel状态
                mDeviceStateMachine.sendMessage(mDeviceStateMachine.obtainMessage(DeviceStateMachine.MSG_HANGUP_CALL));
            }
            //离开频道
            mHSRtcManager.leaveChannel();
            if (mHsSessionListener != null){
                mHsSessionListener.onCallMemberOffline();
            }
        }
    };

    /**
     * 发起呼叫
     *
     * @param receiver  接收方
     * @param mediaType 通话类型
     */
    public void startCall(String receiver, String mediaType, String channelName) {
        if (TextUtils.isEmpty(receiver) || TextUtils.isEmpty(mediaType)) {
            return;
        }
        if (mTopicManager == null || mDeviceStateMachine == null || mMqttManager == null || mHSRtcManager == null) {
            return;
        }
        if (mDeviceStateMachine.isIdelState()) {
            //发布MQTT-call通知
            mTopicManager.publishCallResponse(receiver, mediaType, channelName);
            //改变状态为waiting状态
            mDeviceStateMachine.sendMessage(mDeviceStateMachine.obtainMessage(DeviceStateMachine.MSG_SEND_CALL));
            //开启计时器倒计时暂定40s，如果未接通要不轮询下一个要不取消状态重置未idel状态

            //发起方直接加入频道(临时性逻辑，方便测试主流程通)
            mHSRtcManager.joinChannel(channelName);
        }

    }


    /**
     * 结束呼叫
     *
     * @param receiver 接收方
     */
    public void endCall(String receiver) {
        if (TextUtils.isEmpty(receiver)) {
            return;
        }
        if (mTopicManager == null || mDeviceStateMachine == null || mMqttManager == null || mHSRtcManager == null) {
            return;
        }

        if (mDeviceStateMachine.isWaittingState()) {
            //发布MQTT-outcall通知
            mTopicManager.publishOutCallResponse(receiver);
            //改变状态为idel状态
            mDeviceStateMachine.sendMessage(mDeviceStateMachine.obtainMessage(DeviceStateMachine.MSG_CANCEL_CALL));
        }

        if (mDeviceStateMachine.isOnlineState()) {
            //改变状态为idel状态
            mDeviceStateMachine.sendMessage(mDeviceStateMachine.obtainMessage(DeviceStateMachine.MSG_HANGUP_CALL));
        }

        //离开频道(离开频道会走rtc的onUserOffline()回调，暂时这里设计不用再发一次MQTT消息)
        mHSRtcManager.leaveChannel();

    }

    public void unInit() {
        if (mMqttManager != null) {
            mMqttManager = null;
        }

        if (mTopicManager != null) {
            mTopicManager.unRegisterControllerListener();
            mTopicManager = null;
        }
        if (mHSRtcManager != null) {
            mHSRtcManager.leaveChannel();
            mHSRtcManager = null;
        }

        mDeviceStateMachine = null;
    }


    public MqttManager getMqttManager() {
        return mMqttManager;
    }

    public TopicManager getTopicManager() {
        return mTopicManager;
    }

    public HSRtcManager getHSRtcManager() {
        return mHSRtcManager;
    }

    public DeviceStateMachine getDeviceStateMachine() {
        return mDeviceStateMachine;
    }
}
