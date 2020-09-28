package com.horsent.rtc.statemachine;

import android.os.Message;

public interface IState {

    /**
     * 指示消息已被处理
     */
    static final boolean HANDLED = true;

    /**
     * 指示消息未处理
     */
    static final boolean NOT_HANDLED = false;

    /**
     * 进入状态时调用
     */
    void enter();

    /**
     * 离开状态时调用
     */
    void exit();

    /**
     *  消息处理
     * @param msg 消息
     * @return 处理->HANDLED 或者 未处理->NOT_HANDLED
     */
    boolean processMessage(Message msg);

    /**
     * 获取状态名
     * @return 状态名
     */
    String getName();
}
