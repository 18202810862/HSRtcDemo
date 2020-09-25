package com.horsent.rtc.mqtt;

import android.text.TextUtils;
import android.util.Log;

import com.horsent.rtc.SessionController;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MqttManager {

    //暂时先写在这，后面写入BuildConfig
    private static final String MQTT_URI = "tcp://192.168.0.119:1883";
    private static final String MQTT_USERNAME = "admin";
    private static final String MQTT_PASSWORD = "123456";

    private MqttClient mMqttClient = null;
    private boolean mIsConnecting = false;
    private String mClientId;
    private List<MqttConnectionListener> mMqttConnectionListeners;
    private ObservableEmitter<MqttEvent> mConnectionEmitter;

    public MqttManager(String clientId) {
        mClientId = clientId;
        mMqttConnectionListeners = new ArrayList<>();
    }

    public void connect() {
        // 检查是否已经连接
        if (isConnected()) {
            log("MqttClient has already connected");
            return;
        }
        // 检查是否正在连接
        if (mIsConnecting) {
            log("MqttClient is connecting now");
            return;
        }

        //创建MQTT连接的被观察者
        Observable<MqttEvent> connectionObservable = Observable.create(new ObservableOnSubscribe<MqttEvent>() {
            @Override
            public void subscribe(ObservableEmitter<MqttEvent> emitter) throws Exception {
                mConnectionEmitter = emitter;
                doConnect(emitter);
            }
        });

        //创建MQTT连接的观察者
        Observer<MqttEvent> connectionObserver = new Observer<MqttEvent>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(MqttEvent mqttEvent) {
                log(mqttEvent.getDescription());
                switch (mqttEvent.getType()) {
                    case MqttEvent.TYPE_CONNECTING:
                        break;
                    case MqttEvent.TYPE_CONNECTED:
                        for (MqttConnectionListener listener : mMqttConnectionListeners) {
                            listener.onConnected();
                        }
                        break;
                    case MqttEvent.TYPE_DISCONNECTED:
                        for (MqttConnectionListener listener : mMqttConnectionListeners) {
                            listener.onDisconnected();
                        }
                        break;
                    case MqttEvent.TYPE_ERROR:
                        for (MqttConnectionListener listener : mMqttConnectionListeners) {
                            listener.onError(mqttEvent.getDescription());
                        }
                        break;

                }
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        };

        connectionObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(connectionObserver);
    }

    /**
     * 断开连接
     *
     * @param disconnectListener
     */
    public void disconnect(final MqttDisconnectListener disconnectListener) {
        if (mMqttClient == null) {
            log("MqttClient is null");
            if (disconnectListener != null) {
                disconnectListener.onDisconnected();
            }
            return;
        }

        //断开连接被观察者
        Observable<MqttEvent> disConnObservable = Observable.create(new ObservableOnSubscribe<MqttEvent>() {
            @Override
            public void subscribe(ObservableEmitter<MqttEvent> emitter) throws Exception {
                if (mMqttClient != null) {
                    try {
                        log("发布离线通知");
                        if (mMqttClient.isConnected()) {
                            MqttMessage msg = new MqttMessage();
                            msg.setPayload(getTopicManager().obtainWillReportPayload().getBytes(StandardCharsets.UTF_8));
                            msg.setQos(TopicManager.QOS_0);
                            msg.setRetained(false);
                            mMqttClient.publish(getTopicManager().getTopic(TopicManager.TOPIC_WILL_REPORT), msg);
                        }
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        log("Disconnect触发");
                        mMqttClient.disconnect();
                        mMqttClient.close();
                        log("已断开连接");
                    } catch (MqttException e) {
                        log("断开连接出错，" + e.toString());
                    }
                    mMqttClient = null;
                }
                emitter.onNext(new MqttEvent(MqttEvent.TYPE_DISCONNECTED, "Disconnect by user"));
                emitter.onComplete();
                if (mConnectionEmitter != null && mConnectionEmitter.isDisposed()) {
                    mConnectionEmitter.onComplete();
                    mConnectionEmitter = null;
                }
            }
        });

        //断开连接观察者
        Observer<MqttEvent> disConnObserver = new Observer<MqttEvent>() {

            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(MqttEvent mqttEvent) {
                log(mqttEvent.getDescription());
                if (disconnectListener != null) {
                    disconnectListener.onDisconnected();
                }
                if (mqttEvent.getType() == MqttEvent.TYPE_DISCONNECTED) {
                    for (MqttConnectionListener listener : mMqttConnectionListeners) {
                        listener.onDisconnected();
                    }
                }
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {
                log("task of disconnection shutdown");
            }
        };

        disConnObservable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(disConnObserver);
    }

    /**
     * 执行MQTT连接
     *
     * @param emitter
     */
    private void doConnect(ObservableEmitter<MqttEvent> emitter) {
        //检查是否已经连接
        if (isConnected()) {
            emitter.onNext(new MqttEvent(MqttEvent.TYPE_CONNECTED, "MqttClient has already connected"));
            return;
        }
        //检查是否正在连接
        if (mIsConnecting) {
            emitter.onNext(new MqttEvent(MqttEvent.TYPE_CONNECTING, "MqttClient is connecting now"));
            return;
        }
        //检查clienId
        if (TextUtils.isEmpty(mClientId)) {
            emitter.onNext(new MqttEvent(MqttEvent.TYPE_ERROR, "[clientId] should not be null"));
            emitter.onComplete();
            return;
        }
        log("开始连接MQTT服务器");
        mIsConnecting = true;
        try {
            if (mMqttClient == null) {
                mMqttClient = new MqttClient(MQTT_URI, mClientId,null);
                mMqttClient.setCallback(new MqttCallbackImpl());
                mMqttClient.connect(getConnectionOptions(MQTT_USERNAME,MQTT_PASSWORD));
            } else {
                mMqttClient.reconnect();
            }
        } catch (MqttException e) {
            log("连接出错 + " + e.getCause());
            emitter.onNext(new MqttEvent(MqttEvent.TYPE_ERROR, e.toString()));
            emitter.onComplete();
        }
        log("结束连接MQTT服务器");
        mIsConnecting = false;
    }

    private MqttConnectOptions getConnectionOptions(String username,String password){
        MqttConnectOptions options = new MqttConnectOptions();
        //断开后，是否自动连接
        options.setAutomaticReconnect(true);
        // 是否清空客户端的连接记录。若为true，则断开后，broker将自动清除该客户端连接信息
        options.setCleanSession(false);
        // 设置超时时间，单位为秒
        options.setConnectionTimeout(60);
        // 设置用户名，跟ClientID不同，用户名可以看做权限等级
        options.setUserName(username);
        // 设置登录密码
        options.setPassword(password.toCharArray());
        // 心跳时间，单位为秒，即多长时间确认一次Client端是否在线
        options.setKeepAliveInterval(30);
        // 允许同时发送几条消息（未收到broker确认信息）
        options.setMaxInflight(10);
        // 选择MQTT版本
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        // 设置遗嘱消息
        options.setWill(getTopicManager().getTopic(TopicManager.TOPIC_WILL_REPORT), getTopicManager().obtainWillReportPayload().getBytes(), TopicManager.QOS_0, false);
        return options;
    }

    /**
     * 订阅
     * @param topics 主题(多个)
     * @param qos 每个主题对应的服务质量
     */
    private void subscribe(String[] topics,int[] qos){
        if (topics == null || qos == null || topics.length ==0 || qos.length == 0){
            return;
        }
        try {
            log("开始订阅: " + Arrays.toString(topics));
            mMqttClient.subscribe(topics,qos);
            log("订阅完毕");
        } catch (MqttException e) {
            log("subscribe " + e.toString());
        }
    }

    /**
     * 发布
     * @param topic
     * @param qos
     * @param payload
     */
    public void publish(final String topic, final int qos, final String payload){
        if (isConnected()){
            Observable<MqttEvent> publishObservable = Observable.create(new ObservableOnSubscribe<MqttEvent>() {
                @Override
                public void subscribe(ObservableEmitter<MqttEvent> emitter) throws Exception {
                    log("start publish---> topic: " + topic + ", qos: " + qos + ", payload: " + payload);
                    try {
                        MqttMessage msg = new MqttMessage();
                        //设置消息内容
                        msg.setPayload(payload.getBytes(StandardCharsets.UTF_8));
                        msg.setQos(qos);
                        //服务器是否保存最后一条消息，若保存，client再次上线时，将再次收到上次发送的最后一条消息
                        msg.setRetained(false);
                        mMqttClient.publish(topic,msg);
                    }catch (Exception e){
                        log("error publish---> topic: " + topic + ", " + e.toString());
                    }
                    emitter.onComplete();
                }
            });

            Observer<MqttEvent> publishObserver = new Observer<MqttEvent>() {

                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onNext(MqttEvent mqttEvent) {

                }

                @Override
                public void onError(Throwable e) {

                }

                @Override
                public void onComplete() {

                }
            };

            publishObservable
                    .subscribeOn(Schedulers.io())
                    .subscribe(publishObserver);
        }
    }

    public boolean isConnected() {
        return mMqttClient != null && mMqttClient.isConnected();
    }

    /**
     * MQTT回调实现类
     */
    private class MqttCallbackImpl implements MqttCallbackExtended {

        MqttCallbackImpl() {
        }

        /**
         * MQTT连接成功回调此方法
         *
         * @param reconnect true表示本次连接是自动重连，false本次连接不是自动重连
         * @param serverURI MQTT服务器地址
         */
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            log("连接成功，reconnect = " + reconnect + ", serverURI = " + serverURI);
            // 连接成功，订阅主题
            subscribe(getTopicManager().getSubscribeTopics(), getTopicManager().getSubscribeTopicQos());
            // 发布上线通知
            getTopicManager().publishOnlineReport();
            // 回调连接成功
            if (mConnectionEmitter != null && !mConnectionEmitter.isDisposed()) {
                mConnectionEmitter.onNext(new MqttEvent(MqttEvent.TYPE_CONNECTED, "Connected"));
            }
        }

        /**
         * MQTT连接断开回调此方法
         *
         * @param cause the reason behind the loss of connection.
         */
        @Override
        public void connectionLost(Throwable cause) {
            log("连接丢失，" + cause.toString());
            if (mConnectionEmitter != null && !mConnectionEmitter.isDisposed()) {
                mConnectionEmitter.onNext(new MqttEvent(MqttEvent.TYPE_DISCONNECTED, "Disconnected"));
            }
        }

        /**
         * 当有新消息到达时回调此方法
         * <p>
         * 由MqttClient同步回调
         * 该方法执行完毕时，才会向服务端发送回执
         * <p>
         * 如果在此方法内部抛出了异常，MqttClient将会被关闭
         * 下一次重新连接客户端时，服务器将重新传递任何QoS为1或2消息
         * <p>
         * 在此方法执行期间到达的所有其他消息，将在内存中累积，然后备份到网络上
         * <p>
         * 如果需要保留数据，则应确保在从此方法返回之前保留数据，因为从此方法返回之后，该消息被视为已传递，并且将无法重现
         * <p>
         * 可以在此方法中发送新消息（例如，对此消息的响应），但该实现不得断开客户端的连接，因为将无法发送对正在处理的消息的确认，并且 会发生死锁
         * <p>
         *
         * @param topic   name of the topic on the message was published to
         * @param message the actual message.
         */
        @Override
        public void messageArrived(String topic, MqttMessage message) {
            getTopicManager().dispatch(TopicModel.obtain(topic, message));
        }

        /**
         * 在完成消息传递并收到所有确认后调用
         * 对于QoS为0的消息，一旦将消息传递到网络进行传递，就会调用该消息
         * 对于QoS为1的消息，在收到PUBACK时调用
         * 对于QoS为2的消息，在收到PUBCOMP时调用
         * 该token与发布消息时返回的token相同
         *
         * @param token the delivery token associated with the message.
         */
        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            log("success publish---> topic: " + Arrays.toString(token.getTopics()));
        }
    }

    private TopicManager getTopicManager() {
        return SessionController.getInstance().getTopicManager();
    }

    private void log(String msg) {
        Log.i(getClass().getSimpleName(), msg);
    }
}
