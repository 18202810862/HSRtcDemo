package com.chentao.mqttstutdy

import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.horsent.rtc.HSSessionListener
import com.horsent.rtc.SessionController
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val APP_ID = "4c2af063920041a4a4d028dd4351653f"

    private val DEVICEID1 = "01206a2fe455"
    private val DEVICEID2 = "014b7f53e25c"

    private val CHANNELNAME = "014b7f53e25c-channel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val deviceId = DeviceUtil.getDeviceId(this);
        log("deviceId = " + deviceId);
//        01206a2fe455
        SessionController.getInstance().init(applicationContext, deviceId, APP_ID)
//
        SessionController.getInstance().setHsSessionListener(object : HSSessionListener{
            override fun onEngineInitComplete(code: Int, msg: String?) {
                log("onEngineInitComplete...")
            }

            override fun onCallOutgoing() {
                log("onCallOutgoing...")
            }

            override fun onCallMemberJoin(remoteView: SurfaceView?) {
                log("远端用户视频建立回调")
                remoteContainer.addView(remoteView)
            }

            override fun onCallConnected(localView: SurfaceView?) {
                log("onCallConnected...")
                localContainer.addView(localView)
            }

            override fun onCallMemberOffline() {
                log("远端用户离开回调")
                localContainer.removeAllViews()
                remoteContainer.removeAllViews()
            }

        })

        SessionController.getInstance().mqttManager.connect()


        //呼叫
        publishCallNotify.setOnClickListener {
            //发送通话信息通知
            if (deviceId == DEVICEID1) {
                SessionController.getInstance().startCall(DEVICEID2, "VIDEO", CHANNELNAME)
            } else {
                SessionController.getInstance().startCall(DEVICEID1, "VIDEO", CHANNELNAME)
            }
        }

        //挂断
        leaveChannel.setOnClickListener {
            localContainer.removeAllViews()
            remoteContainer.removeAllViews()
            if (deviceId == DEVICEID1) {
                SessionController.getInstance().endCall(DEVICEID2)
            } else {
                SessionController.getInstance().endCall(DEVICEID1)
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SessionController.getInstance().unInit()
    }

    private fun log(msg: String) {
        Log.i(localClassName, msg)
    }
}