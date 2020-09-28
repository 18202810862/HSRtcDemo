package com.horsent.voipdemo;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Date    23/04/2018
 * Author  WestWang
 * 设备编号工具类
 */

public final class DeviceUtil {

    private static final String SETTING_DEVICE_ID = "hw_em_device_id";
    private static String DEVICE_ID = null;

    public synchronized static String getDeviceIdDisplay(Context context) {
        return "设备编号: " + getDeviceId(context);
    }

    /**
     * 获取Installation ID
     *
     * @param context {@linkplain Context}
     * @return {@linkplain String} 应用唯一标识
     */
    public synchronized static String getDeviceId(Context context) {
        if (DEVICE_ID == null) {
            String deviceId = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 6.0及其以上版本，保存在SD卡中
                if (FileUtil.isSDCardMounted()) {
                    try {
                        byte[] buff = readFromFile(new File(getFilePath()));
                        if (buff != null && buff.length > 0) {
                            deviceId = new String(buff, "UTF-8");
                        }
                    } catch (Throwable t) {
                        // nothing
                    }
                }
            } else {
                // 6.0以下版本，保存在系统设置中
                deviceId = Settings.System.getString(context.getContentResolver(), SETTING_DEVICE_ID);
            }
            DEVICE_ID = deviceId;
        }
        Log.e("DeviceUtil", "DeviceId: " + DEVICE_ID);
        return DEVICE_ID;
    }

    /**
     * 更新设备编号
     *
     * @param context  {@linkplain Context}
     * @param deviceId {@linkplain String}
     */
    public synchronized static void updateDeviceId(Context context, String deviceId) {
        if (TextUtils.isEmpty(deviceId)) return;
        if (!deviceId.equals(DEVICE_ID)) {
            DEVICE_ID = deviceId;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // 6.0以下版本，保存在系统设置中
            Settings.System.putString(context.getContentResolver(), SETTING_DEVICE_ID, deviceId);
        }
        if (FileUtil.isSDCardMounted()) {
            try {
                saveToFile(new File(getFilePath()), deviceId.getBytes("UTF-8"));
            } catch (Throwable t) {
                // nothing
            }
        }
    }

    /**
     * 从文件中读取设备ID
     *
     * @param file {@linkplain File}
     * @return byte[]
     */
    private static byte[] readFromFile(File file) {
        if (file != null && file.exists() && !file.isDirectory()) {
            FileInputStream is = null;
            try {
                is = new FileInputStream(file);
                byte[] buff = new byte[is.available()];
                is.read(buff);
                return buff;
            } catch (Throwable t) {
                // nothing
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (Throwable t) {
                    // nothing
                }
            }
            return null;
        } else {
            return null;
        }
    }

    /**
     * 保存设备ID至文件中
     *
     * @param file file {@linkplain File}
     * @param data byte[]
     */
    private static void saveToFile(File file, byte[] data) {
        if (file != null && !file.isDirectory() && data != null && data.length > 0) {
            FileOutputStream os = null;
            try {
                os = new FileOutputStream(file);
                os.write(data);
            } catch (Throwable t) {
                // nothing
            } finally {
                try {
                    if (os != null) {
                        os.flush();
                        os.close();
                    }
                } catch (Throwable t) {
                    // nothing
                }
            }
        }
    }

    /**
     * 获取文件路径
     *
     * @return {@linkplain String}
     */
    private static String getFilePath() {
        String path = null;
        try {
            path = Environment.getExternalStorageDirectory().getPath();
        } catch (Throwable t) {
            // nothing
        }
        if (path != null && path.length() > 0) {
            path = path + "/data/";
            File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            path += "." + SETTING_DEVICE_ID;
        }
        return path;
    }
}
