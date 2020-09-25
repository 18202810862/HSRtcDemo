package com.chentao.mqttstutdy

import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.horsent.rtc.SessionController
import com.horsent.rtc.mqtt.TopicManager
import com.horsent.rtc.mqtt.TopicModel
import com.horsent.rtc.rtc.HSRtcListener
import com.horsent.rtc.rtc.HSRtcManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val APP_ID = "4c2af063920041a4a4d028dd4351653f"

    private var hsRtcManager: HSRtcManager? = null

    private val DEVICEID1 = "01206a2fe455"
    private val DEVICEID2 = "014b7f53e25c"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val deviceId = DeviceUtil.getDeviceId(this);
        log("deviceId = " + deviceId);
//        01206a2fe455
        SessionController.getInstance().init(applicationContext, deviceId)
        SessionController.getInstance().mqttManager.connect()

        publishCallNotify.setOnClickListener {
            //发送通话信息通知
            if (deviceId == DEVICEID1){
                SessionController.getInstance().topicManager.publishVideoResponse("af2456234234f",DEVICEID2,"VIDEO")
            }else {
                SessionController.getInstance().topicManager.publishVideoResponse("af2456234234f",DEVICEID1,"VIDEO")
            }
        }

        //以下是视频通话
//        val hsRtcManager = HSRtcManager()
//
//        hsRtcManager.hsRtcListener = object : HSRtcListener {
//            override fun onEngineInitComplete(code: Int, msg: String) {
//            }
//
//            override fun onCallOutgoing() {
//            }
//
//            override fun onCallConnected(localView: SurfaceView) {
//                localContainer.addView(localView)
//            }
//
//            override fun onCallMemberJoin(remoteView: SurfaceView) {
//                log("远端用户视频建立回调")
//                remoteContainer.addView(remoteView)
//            }
//
//            override fun onCallMemberOffline() {
//                log("远端用户离开回调")
//                localContainer.removeAllViews()
//                remoteContainer.removeAllViews()
//
//            }
//
//        }
//
//        hsRtcManager.init(applicationContext, APP_ID)
//
//
//        leaveChannel.setOnClickListener {
//            localContainer.removeAllViews()
//            remoteContainer.removeAllViews()
//            hsRtcManager.leaveChannel()
//        }
//
//        joinChannel.setOnClickListener {
//            hsRtcManager.joinChannel("customChannel")
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (hsRtcManager != null) {
            hsRtcManager?.leaveChannel()
        }
    }

    private fun log(msg: String) {
        Log.i(localClassName, msg)
    }
}