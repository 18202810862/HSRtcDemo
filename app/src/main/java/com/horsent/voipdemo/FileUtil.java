package com.horsent.voipdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Date    13/06/2018
 * Author  WestWang
 * 文件工具类
 */

public final class FileUtil {

    private static final String TAG = "FileUtil";
    private static final String ROOT_DIR = "/CMGuard/";
    public static final String APK_DIR = ROOT_DIR + "apk/";
    public static final String LOG_DIR = ROOT_DIR + "log/";
    public static final String FACE_DIR = ROOT_DIR + "face/";
    public static final String TEST_DIR = ROOT_DIR + "test/";
    /**
     * 判断SD卡是否被挂载
     *
     * @return true已挂载，false未挂载
     */
    public static boolean isSDCardMounted() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * 根据文件路径删除文件
     *
     * @param path {@linkplain String } 文件路径
     */
    public static void deleteFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            if (file.isFile()) {
                file.delete();
            } else if (file.isDirectory()){
                File[] files = file.listFiles();
                if (files == null || files.length == 0) {
                    // 目录里面没有文件了，直接删除目录
                    file.delete();
                } else {
                    for (File f: files) {
                        if (f != null && f.exists()){
                            if (f.isFile()) {
                                f.delete();
                            } else {
                                deleteFile(f.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 日志文件保存路径
     */
    public static String getLogPath(Context context, String fileName) {
        String dirPath;
        if (isSDCardMounted()) {
            dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + LOG_DIR;
        } else {
            dirPath = context.getCacheDir().getAbsolutePath() + LOG_DIR;
        }
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file.getAbsolutePath();
    }

    /**
     * 人脸图片保存路径
     */
    public static String getFacePath(Context context, String fileName) {
        String dirPath;
        if (isSDCardMounted()) {
            dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + FACE_DIR;
        } else {
            dirPath = context.getCacheDir().getAbsolutePath() + FACE_DIR;
        }
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file.getAbsolutePath();
    }

    /**
     * 麦克风录制文件保存路径
     */
    public static String getTestPath(Context context, String fileName) {
        String dirPath;
        if (isSDCardMounted()) {
            dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + TEST_DIR;
        } else {
            dirPath = context.getCacheDir().getAbsolutePath() + TEST_DIR;
        }
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file.getAbsolutePath();
    }

    /**
     * apk文件下载保存路径
     */
    public static String getApkDownloadPath(Context context, String fileName) {
        String dirPath;
        if (isSDCardMounted()) {
            dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + APK_DIR;
        } else {
            dirPath = context.getCacheDir().getAbsolutePath() + APK_DIR;
        }
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file.getAbsolutePath();
    }

    public static String getRootDir(Context context) {
        try {
            if (isSDCardMounted()) {
                return Environment.getExternalStorageDirectory().getAbsolutePath();
            } else {
                return context.getCacheDir().getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String checkDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    /**
     * 保存图片到私有Cache目录
     *
     * @param bitmap   {@linkplain Bitmap} 要保存的目标图片
     * @param fileName {@linkplain String} 文件名
     * @param context  {@linkplain Context}
     * @return {@linkplain String} 文件路径
     */
    public static String saveBitmapToPrivateCacheDir(Bitmap bitmap, String fileName, Context context) {
        if (bitmap == null || bitmap.isRecycled() || TextUtils.isEmpty(fileName) || context == null) {
            return "";
        }
        String path = "";
        BufferedOutputStream bos = null;
        try {
            File dir;
//            if (isSDCardMounted()) {
//                dir = context.getExternalCacheDir();
//            } else {
//                dir = context.getCacheDir();
//            }
            dir = new File("/sdcard/horsent/face/");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, fileName);
            if (file.exists()) {
                file.delete();
            }
            bos = new BufferedOutputStream(new FileOutputStream(file));
            if (fileName.contains(".png") || fileName.contains(".PNG")) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            }
            bos.flush();
            path = file.getAbsolutePath();
        } catch (Exception e) {
            Log.d(TAG, "saveBitmapToPrivateCacheDir: " + e.getLocalizedMessage());
            path = "";
        } finally {
            try {
                if (bos != null) {
                    bos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return path;
    }

    /**
     * 从指定路径读取文件
     *
     * @param path {@linkplain String} 文件路径
     * @return byte[]
     */
    public static byte[] loadFile(String path) {
        if (TextUtils.isEmpty(path)) return null;
        BufferedInputStream bis = null;
        ByteArrayOutputStream baos = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(new File(path)));
            baos = new ByteArrayOutputStream();
            byte[] buff = new byte[2 * 1024];
            int len;
            while ((len = bis.read(buff)) != -1) {
                baos.write(buff, 0, len);
                baos.flush();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (bis != null) {
                    bis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 从指定路径加载Bitmap
     *
     * @param path {@linkplain String} 文件路径
     * @return byte[]
     */
    public static Bitmap loadBitmap(String path) {
        if (TextUtils.isEmpty(path)) return null;
        byte[] data = loadFile(path);
        if (data != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap != null) return bitmap;
        }
        return null;
    }
}
