package com.horsent.rtc.statemachine;

import android.os.Message;

public class DeviceStateMachine extends StateMachine{

    //发起呼叫
    public static final int MSG_SEND_CALL = 0;
    //接收呼叫
    public static final int MSG_RECEIVE_CALL = 1;
    //接听呼叫
    public static final int MSG_ANSWER_CALL = 2;
    //拒绝呼叫
    public static final int MSG_REFUSE_CALL = 3;
    //取消呼叫
    public static final int MSG_CANCEL_CALL = 4;
    //挂断呼叫
    public static final int MSG_HANGUP_CALL = 5;
    //创建状态
    private State mIdelState = new IdelState();
    private State mWaitingState = new WaitingState();
    private State mOnlineState = new OnlineState();

    protected DeviceStateMachine(String name) {
        super(name);
        //加入状态
        addState(mIdelState);
        addState(mWaitingState);
        addState(mOnlineState);

        //设置idel状态为初始化状态
        setInitialState(mIdelState);
    }

    public static DeviceStateMachine makeDevice(){
        DeviceStateMachine device = new DeviceStateMachine("device");
        device.start();
        return device;
    }

    //是否是空闲状态
    public boolean isIdelState(){
        State currentState = (State) getCurrentState();
        if (currentState != null){
            String name = currentState.getName();
            if (name.equals(IdelState.class.getName())){
                return true;
            }
        }
        return false;
    }

    /**
     * 是否是等待状态
     */
    public boolean isWaittingState(){
        State currentState = (State) getCurrentState();
        if (currentState != null){
            String name = currentState.getName();
            if (name.equals(WaitingState.class.getName())){
                return true;
            }
        }
        return false;
    }

    /**
     * 是否是接听状态
     */
    public boolean isOnlineState(){
        State currentState = (State) getCurrentState();
        if (currentState != null){
            String name = currentState.getName();
            if (name.equals(OnlineState.class.getName())){
                return true;
            }
        }
        return false;
    }


    /**
     * 空闲状态
     */
    class IdelState extends State {

        @Override
        public void enter() {
            log("IdelState enter....");
        }

        @Override
        public void exit() {
            log("IdelState exit....");
        }

        @Override
        public boolean processMessage(Message msg) {
            log("IdelState processMessage ....");
            switch (msg.what){
                case MSG_SEND_CALL:
                case MSG_RECEIVE_CALL:
                    //接收呼叫转入wait状态
                    //发起呼叫转入wait状态
                    transitionTo(mWaitingState);
                    break;
                default:
                    return false;
            }
            return true;
        }
    }

    /**
     * 等待状态
     */
    class WaitingState extends State{

        @Override
        public void enter() {
            log("WaitingState enter.....");
        }

        @Override
        public void exit() {
            log("WaitingState exit.....");
        }

        @Override
        public boolean processMessage(Message msg) {
            log("WaitingState processMessage ....");
            switch (msg.what){
                case MSG_ANSWER_CALL:
                    //接听电话转入online状态
                    transitionTo(mOnlineState);
                    break;
                case MSG_REFUSE_CALL:
                case MSG_CANCEL_CALL:
                    //拒绝、取消装入idel状态
                    transitionTo(mIdelState);
                    break;
                default:
                    return false;
            }

            return true;
        }
    }

    /**
     * 在线状态
     */
    class OnlineState extends State{
        @Override
        public void enter() {
            log("OnlineState enter....");
        }

        @Override
        public void exit() {
            log("OnlineState exit....");
        }

        @Override
        public boolean processMessage(Message msg) {
            log("OnlineState processMessage....");
            switch (msg.what){
                case MSG_HANGUP_CALL:
                    //挂断转入空闲状态
                    transitionTo(mIdelState);
                    break;
                default:
                    return false;
            }
            return true;
        }
    }

}
