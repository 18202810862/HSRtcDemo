package com.horsent.rtc.rtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;

public class HSRtcManager {


    private RtcEngine mRtcEngine;
    private boolean mCallEnd;
    private boolean mMuted;

    private Context mContext;

    private SurfaceView mLocalView;
    private SurfaceView mRemoteView;
    private HSRtcListener mHSRtcListener;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    public HSRtcManager() {

    }

    public void init(Context context, String appid) {
        initEngine(context, appid);
        setupVideoConfig();
    }


    public void setHSRtcListener(HSRtcListener hsRtcListener) {
        mHSRtcListener = hsRtcListener;
    }

    public HSRtcListener getHSRtcListener() {
        return mHSRtcListener;
    }

    /**
     * 初始化RtcEngine
     */
    private void initEngine(Context context, String appid) {
        mContext = context;
        log("RtcEngine引擎初始化开始");
        try {
            mRtcEngine = RtcEngine.create(context, appid, mRtcEventHandler);
            if (mHSRtcListener != null) {
                mHSRtcListener.onEngineInitComplete(HSRtcConstans.CODE_ENGINE_INIT_SUCCESS, "engine init success");
            }
        } catch (Exception e) {
            log("RtcEngine引擎初始化出错 -> " + e.toString());
            if (mHSRtcListener != null) {
                mHSRtcListener.onEngineInitComplete(HSRtcConstans.CODE_ENGINE_INIT_FAIL, e.toString());
            }
        }
        log("RtcEngine引擎初始化成功");

    }

    /**
     * 配置video视频设置
     */
    private void setupVideoConfig() {
        mRtcEngine.enableVideo();
        mRtcEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                VideoEncoderConfiguration.VD_480x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
        ));
        log("视频设置成功");

    }

    /**
     * 设置本地视频
     */
    private void setupLocalVideo() {
        if (mRtcEngine == null) {
            return;
        }
        mLocalView = null;
        mLocalView = RtcEngine.CreateRendererView(mContext);
        mLocalView.setZOrderMediaOverlay(true);
        mRtcEngine.setupLocalVideo(new VideoCanvas(mLocalView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
    }

    /**
     * 设置远端视频
     *
     * @param uid
     */
    private void setupRemoteVideo(final int uid) {
        if (mRtcEngine == null) {
            return;
        }
        mRemoteView = null;
        mRemoteView = RtcEngine.CreateRendererView(mContext);
        // Initializes the video view of a remote user.
        mRtcEngine.setupRemoteVideo(new VideoCanvas(mRemoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
        mRemoteView.setTag(uid);
    }

    /**
     * 加入频道
     */
    public void joinChannel(String channelName) {
        if (mRtcEngine == null) {
            return;
        }
        int joinChannelResultId = mRtcEngine.joinChannel(null, channelName, "Extra Optional Data", 0);
        log("加入频道code = " + joinChannelResultId);
    }

    /**
     * 离开频道
     */
    public void leaveChannel() {
        if (mRtcEngine == null) {
            return;
        }

        mRtcEngine.leaveChannel();
        mLocalView = null;
        mRemoteView = null;

    }

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {

        /**
         * 远端音频状态发生改变
         * @param uid uid
         * @param state 状态码
         * @param reason 原因
         * @param elapsed 时间
         */
        @Override
        public void onRemoteAudioStateChanged(int uid, int state, int reason, int elapsed) {
            log("远端音频状态发生改变--->" + state);
            switch (state) {
                case Constants.REMOTE_AUDIO_STATE_DECODING:
                    //远端音频流正在解码，正常播放
                    break;
            }

        }

        /**
         * 本端用户加入频道
         * @param channel 频道名
         * @param uid 本端设备编号
         * @param elapsed 时间
         */
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            log("本端用户加入频道成功--->");
            log("频道名为--->" + channel);
            //本端创建频道并加入成功就执行MQTT推送,并
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setupLocalVideo();
                    if (mHSRtcListener != null) {
                        mHSRtcListener.onCallConnected(mLocalView);
                    }

                    if (mHSRtcListener != null) {
                        mHSRtcListener.onCallOutgoing();
                    }
                }
            });


        }


        /**
         * 远端用户加入频道回调
         * @param uid 远端设备编号
         * @param elapsed 时间
         */
        @Override
        public void onUserJoined(int uid, int elapsed) {
            log("远端用户加入频道回调--->");
        }

        /**
         * 远端用户离开频道回调
         * @param uid 远端设备编号
         * @param reason 原因
         */
        @Override
        public void onUserOffline(int uid, int reason) {
            log("远端用户离开频道回调--->" + reason);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mHSRtcListener != null) {
                        mHSRtcListener.onCallMemberOffline();
                    }
                    leaveChannel();
                }
            });

        }

        /**
         * 远端用户视频状态发生变化回调
         * @param uid
         * @param state
         * @param reason
         * @param elapsed
         */
        @Override
        public void onRemoteVideoStateChanged(final int uid, int state, int reason, int elapsed) {
            log("远端用户视频状态发生变化--->" + state);
            switch (state) {
                case Constants.REMOTE_VIDEO_STATE_DECODING:
                    //远端视频流正在解码，正常播放
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setupRemoteVideo(uid);
                            if (mHSRtcListener != null) {
                                mHSRtcListener.onCallMemberJoin(mRemoteView);
                            }
                        }
                    });
                    break;
                default:
                    break;
            }

        }
    };


    private void log(String logStr) {
        Log.i(getClass().getSimpleName(), logStr);
    }
}
