package com.homework.ksing.mettingondoor;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;

import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.FaceSimilar;
import com.arcsoft.face.VersionInfo;
import com.homework.ksing.mettingondoor.common.Constants;
import com.homework.ksing.mettingondoor.util.camera.CameraHelper;
import com.homework.ksing.mettingondoor.util.camera.CameraListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class TestActivity extends Activity {

    FaceEngine faceEngine = null;
    static int errorCode = -1;

    private static final String TAG = "TestActivity";
    private CameraHelper cameraHelper;
    private Camera.Size previewSize;
    private int processMask = FaceEngine.ASF_FACE_RECOGNITION | FaceEngine.ASF_FACE_DETECT | FaceEngine.ASF_AGE | FaceEngine.ASF_GENDER | FaceEngine.ASF_FACE3DANGLE | FaceEngine.ASF_LIVENESS;
    private Integer cameraID = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private TextureView previewView ;//相机预览显示控件


    /**
     * 所需的所有权限信息
     */
    private static final int ACTION_REQUEST_PERMISSIONS = 0x001;
    private static final String[] NEEDED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        previewView=findViewById(R.id.face);
        if(!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS);
        } else {
            initEngine();
            initCamera();
        }
    }

    /**
     * 检查是否授权
     * @param neededPermissions
     * @return
     */
    private boolean checkPermissions(String[] neededPermissions) {
        if (neededPermissions == null || neededPermissions.length == 0) {
            return true;
        }
        boolean allGranted = true;
        for (String neededPermission : neededPermissions) {
            allGranted &= ContextCompat.checkSelfPermission(this.getApplicationContext(), neededPermission) == PackageManager.PERMISSION_GRANTED;
        }
        return allGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ACTION_REQUEST_PERMISSIONS) {
            boolean isAllGranted = true;
            for (int grantResult : grantResults) {
                isAllGranted &= (grantResult == PackageManager.PERMISSION_GRANTED);
            }
            if (isAllGranted) {
                initEngine();
                initCamera();
            } else {
                Log.i(TAG, "未授权权限");
            }
        }
    }

    /**
     * 初始化引擎
     */
    private void initEngine() {
        faceEngine = new FaceEngine();
        //激活引擎
        int activeCode = faceEngine.active(this, Constants.ArcFace_APP_ID, Constants.ArcFace_SDK_KEY);

        /**
         * faceEngine.init()初始化引擎
         * @param context上下文对象
         * @param 视频模式检测
         * @param 人脸检测方向为多方向检测
         * @param 人脸相对于所在图片的长边的占比 [2, 16]
         * @param 引擎最多能检测出的人脸数 [1, 50]
         * @param 引擎功能:人脸检测、人脸识别、年龄检测、人脸三维角度检测、性别检测、活体检测
         */
        int errorCode = faceEngine.init(this.getApplicationContext(), FaceEngine.ASF_DETECT_MODE_VIDEO,
                FaceEngine.ASF_OP_0_HIGHER_EXT,
                16, 1,
                processMask);
        //获取版本信息
        VersionInfo versionInfo = new VersionInfo();
        faceEngine.getVersion(versionInfo);
        if (errorCode != ErrorInfo.MOK) {
            Log.i(TAG, "初始化引擎失败");
        } else {
            //deBug信息
            Log.i(TAG, "初始化引擎成功!  errorCode: " + errorCode + "  引擎版本号:" + versionInfo);
        }
    }

    /**
     * 销毁引擎
     */
    private void unInitEngine() {
        if (errorCode == ErrorInfo.MOK) {
            errorCode = faceEngine.unInit();
            Log.i(TAG, "销毁引擎!  errorCode: " + errorCode);
        }
    }



    private byte[] faceFeatureData; //存储人脸特征值数据


    private void initCamera() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        CameraListener cameraListener = new CameraListener() {
            @Override
            public void onCameraOpened(Camera camera, int cameraId, int displayOrientation, boolean isMirror) {
                Log.i(TAG, "onCameraOpened: " + cameraId + "  " + displayOrientation + " " + isMirror);
                previewSize = camera.getParameters().getPreviewSize();
            }

            @Override
            public void onPreview(byte[] nv21, Camera camera) {
                List<FaceInfo> faceInfoList = new ArrayList<>();
                int code = faceEngine.detectFaces(nv21, previewSize.width, previewSize.height, FaceEngine.CP_PAF_NV21, faceInfoList);
                Log.i("code", "code:"+code);
                if (code == ErrorInfo.MOK && faceInfoList.size() > 0) {
                    FaceFeature faceFeatures = new FaceFeature();
                    //从图片解析出人脸特征数据
                    int extractFaceFeatureCodes = faceEngine.extractFaceFeature(nv21, previewSize.width, previewSize.height, FaceEngine.CP_PAF_NV21, faceInfoList.get(0), faceFeatures);
                    if(extractFaceFeatureCodes == ErrorInfo.MOK) {
                        faceFeatureData = faceFeatures.getFeatureData();
                        System.out.print("特征值:");
                        System.out.println(bytesToHex(faceFeatureData));

                        FaceFeature f = new FaceFeature(faceFeatureData);
                        FaceSimilar faceSimilar = new FaceSimilar();//存储人脸相似度信息
                        int compareErrCode = faceEngine.compareFaceFeature(faceFeatures, f, faceSimilar);
                        if(compareErrCode == ErrorInfo.MOK) {
                            Log.i("相似度", "相似度:" + faceSimilar.getScore());
                        }
                        return;
                    }
                }else {
                    return;
                }
            }

            @Override
            public void onCameraClosed() {
                Log.i(TAG, "onCameraClosed: ");
            }

            @Override
            public void onCameraError(Exception e) {
                Log.i(TAG, "onCameraError: " + e.getMessage());
            }

            @Override
            public void onCameraConfigurationChanged(int cameraID, int displayOrientation) {
                Log.i(TAG, "onCameraConfigurationChanged: " + cameraID + "  " + displayOrientation);
            }
        };
        cameraHelper = new CameraHelper.Builder()
                .metrics(metrics)
                .rotation(getWindowManager().getDefaultDisplay().getRotation())
                .specificCameraId(cameraID != null ? cameraID : Camera.CameraInfo.CAMERA_FACING_FRONT)
                .isMirror(false)
                .previewOn(previewView)
                .cameraListener(cameraListener)
                .build();
        cameraHelper.init();
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if(hex.length() < 2){
                sb.append(0);
            }
            sb.append(hex);
        }
        return sb.toString();
    }
    public static File getFile(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        File file = new File(Environment.getExternalStorageDirectory() + "/temp.jpg");
        try {
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            InputStream is = new ByteArrayInputStream(baos.toByteArray());
            int x = 0;
            byte[] b = new byte[1024 * 100];
            while ((x = is.read(b)) != -1) {
                fos.write(b, 0, x);
            }
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

}
