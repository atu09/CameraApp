package com.atirekpothiwala.cameraapp;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public class Camera2Activity extends AppCompatActivity {

    AutoFitTextureView textureView;
    ImageView ivSnap;
    ImageView ivSwitchCamera;
    ImageView ivFlash;
    View viewFlash;

    CameraManager cameraManager;
    Integer defaultCameraId;
    CameraDevice defaultCameraDevice;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest.Builder captureRequestBuilder;
    Handler cameraHandler;
    HandlerThread cameraHandlerThread;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 280);
    }

    boolean isFrontCameraAvailable = false;
    boolean isBackCameraAvailable = false;
    boolean isFlashSupported = false;
    int flashMode = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
    int orientation;

    String[] permissions = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    private MediaRecorder mediaRecorder;
    private boolean isRecordingVideo = false;

    File file;

    int currentBrightness = 255;
    int maxBrightness = 255;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        this.ivFlash = (ImageView) findViewById(R.id.ivFlash);
        this.ivSwitchCamera = (ImageView) findViewById(R.id.ivSwitchCamera);
        this.ivSnap = (ImageView) findViewById(R.id.ivSnap);
        this.textureView = (AutoFitTextureView) findViewById(R.id.textureView);
        this.viewFlash = (View) findViewById(R.id.viewFlash);

        this.ivSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                swapCamera();
            }
        });

        this.ivSnap.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("StaticFieldLeak")
            @Override
            public void onClick(View view) {

                if (isRecordingVideo) {
                    stopRecordingVideo();
                } else {
                    takeSnap();
                }
            }
        });

        this.ivSnap.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                startRecordingVideo();
                return false;
            }
        });

        this.ivFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (isFlashSupported) {
                    switch (flashMode) {
                        case CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH:
                            flashMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
                            ivFlash.setImageResource(R.drawable.ic_flash_on);
                            break;
                        case CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                            flashMode = CaptureRequest.CONTROL_AE_MODE_OFF;
                            ivFlash.setImageResource(R.drawable.ic_flash_off);
                            break;
                        case CaptureRequest.CONTROL_AE_MODE_OFF:
                            flashMode = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
                            ivFlash.setImageResource(R.drawable.ic_flash_auto);
                            break;
                    }
                } else {
                    switch (flashMode) {
                        case CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                            flashMode = CaptureRequest.CONTROL_AE_MODE_OFF;
                            ivFlash.setImageResource(R.drawable.ic_flash_off);
                            break;
                        case CaptureRequest.CONTROL_AE_MODE_OFF:
                            flashMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
                            ivFlash.setImageResource(R.drawable.ic_flash_on);
                            break;
                    }
                }
            }
        });

        /*Size defaultSize = Utils.getScreenSize(this);
        textureView.setAspectRatio(defaultSize.getWidth(),defaultSize.getHeight());*/

    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
        if (textureView.isAvailable()) {
            checkCameraAvailability();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        closeCamera();
        stopBackgroundThread();
    }

    AutoFitTextureView.SurfaceTextureListener textureListener = new AutoFitTextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            checkCameraAvailability();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private void stopBackgroundThread() {
        cameraHandlerThread.quitSafely();
        try {
            cameraHandlerThread.join();
            cameraHandlerThread = null;
            cameraHandler = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        cameraHandlerThread = new HandlerThread("Camera Background");
        cameraHandlerThread.start();
        cameraHandler = new Handler(cameraHandlerThread.getLooper());
    }

    void takeSnap() {

        if (defaultCameraDevice == null) {
            Utils.popToast(this, "Something went wrong, try again later!");
            return;
        }

        try {

            Size defaultSize = Utils.getScreenSize(this);
            Utils.checkLog("camera", "Dimensions: " + defaultSize.toString(), null);
            final ImageReader reader = ImageReader.newInstance(defaultSize.getHeight(), defaultSize.getWidth(), ImageFormat.JPEG, 1);

            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(defaultSize.getHeight(), defaultSize.getWidth());

            Surface previewSurface = new Surface(texture);
            Surface imageSurface = reader.getSurface();

            final CaptureRequest.Builder captureRequestBuilder = defaultCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(reader.getSurface());

            int orientation = Utils.getScreenRotation(this);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(orientation));
            if (isFlashSupported) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            }

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    if (!isFlashSupported && flashMode == CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH) {
                        ObjectAnimator.ofFloat(viewFlash, View.ALPHA, 0, 8f, 0f).setDuration(100).start();
                    }
                    new ImageSaverTask(imageReader.acquireLatestImage()).execute();
                }

            };
            reader.setOnImageAvailableListener(readerListener, cameraHandler);

            final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview();
                }
            };

            defaultCameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        if (!isFlashSupported && flashMode == CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH) {
                            ObjectAnimator.ofFloat(viewFlash, View.ALPHA, 0f, 0.8f).setDuration(100).start();
                        }
                        cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, cameraHandler);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, cameraHandler);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    class ImageSaverTask extends AsyncTask<Object, Void, Boolean> {

        Image image;

        public ImageSaverTask(Image image) {
            this.image = image;
        }

        @Override
        protected Boolean doInBackground(Object... object) {

            try {

                file = new File(Environment.getExternalStorageDirectory() + "/" + UUID.randomUUID().toString() + ".jpg");

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);

                OutputStream outputStream = new FileOutputStream(file);
                outputStream.write(bytes);
                outputStream.close();
                image.close();

                return true;

            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            if (result) {
                Utils.popToast(Camera2Activity.this, "Image Captured!");
            } else {
                Utils.popToast(Camera2Activity.this, "Failed to Save!");
            }

        }
    }

    void checkCameraAvailability() {

        try {

            cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String ID : cameraManager.getCameraIdList()) {
                if (Integer.valueOf(ID) == CameraCharacteristics.LENS_FACING_FRONT) {
                    isBackCameraAvailable = true;
                    Utils.checkLog("camera", "ID: " + ID + " - LENS_FACING_FRONT", null);
                } else if (Integer.valueOf(ID) == CameraCharacteristics.LENS_FACING_BACK) {
                    isFrontCameraAvailable = true;
                    Utils.checkLog("camera", "ID: " + ID + " - LENS_FACING_BACK", null);
                } else {
                    Utils.checkLog("camera", "ID: " + ID + " - LENS_FACING_EXTERNAL", null);
                }
            }

            if (isFrontCameraAvailable && isBackCameraAvailable) {
                ivSnap.setVisibility(View.VISIBLE);
                ivSwitchCamera.setVisibility(View.VISIBLE);
                defaultCameraId = CameraCharacteristics.LENS_FACING_BACK;
            } else {
                ivSwitchCamera.setVisibility(View.GONE);
                if (isFrontCameraAvailable) {
                    ivSnap.setVisibility(View.VISIBLE);
                    defaultCameraId = CameraCharacteristics.LENS_FACING_FRONT;
                } else if (isBackCameraAvailable) {
                    ivSnap.setVisibility(View.VISIBLE);
                    defaultCameraId = CameraCharacteristics.LENS_FACING_BACK;
                } else {
                    Utils.popToast(this, "Something went wrong with cameras, try again later!");
                    ivSnap.setVisibility(View.GONE);
                    defaultCameraId = null;
                    return;
                }
            }

            openCamera(defaultCameraId);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    void openCamera(Integer cameraId) {

        Utils.checkLog("camera", "Open: " + cameraId, null);

        if (!Utils.isPermissionsGranted(this, permissions)) {
            Utils.requestPermissions(this, permissions);
            return;
        }

        if (defaultCameraDevice != null) {
            defaultCameraDevice.close();
        }

        try {

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(String.valueOf(cameraId));
            orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            Boolean flash_availability = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            isFlashSupported = (flash_availability == null ? false : flash_availability);
            if (!isFlashSupported && flashMode == CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) {
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
                ivFlash.setImageResource(R.drawable.ic_flash_on);
            }
            cameraManager.openCamera(String.valueOf(cameraId), new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    defaultCameraDevice = cameraDevice;
                    createCameraPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    cameraDevice.close();
                    defaultCameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    cameraDevice.close();
                    defaultCameraDevice = null;
                }
            }, null);
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isFlashSupported) {
                cameraManager.setTorchMode(String.valueOf(defaultCameraId), true);
            }*/
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + orientation + 270) % 360;
    }

    void closeCamera() {

        if (hasWriteSettingsPermission()) {
            changeScreenBrightness(currentBrightness);
        }

        if (null != defaultCameraDevice) {
            defaultCameraDevice.close();
            defaultCameraDevice = null;
        }

        if (null != mediaRecorder) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void createCameraPreview() {
        try {

            if (hasWriteSettingsPermission()) {
                currentBrightness = getScreenBrightness();
                changeScreenBrightness(maxBrightness);
            }

            Size defaultSize = Utils.getScreenSize(this);
            Utils.checkLog("camera", "Dimensions: " + defaultSize.toString(), null);
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(defaultSize.getHeight(), defaultSize.getWidth());

            Surface surface = new Surface(texture);
            captureRequestBuilder = defaultCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            defaultCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (defaultCameraDevice == null) {
                        return;
                    }
                    Camera2Activity.this.cameraCaptureSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Utils.popToast(Camera2Activity.this, "Failed to open camera, try again later!");
                }
            }, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (defaultCameraDevice == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        if (isFlashSupported) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
        }
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, cameraHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void swapCamera() {
        if (defaultCameraId == CameraCharacteristics.LENS_FACING_FRONT) {
            defaultCameraId = CameraCharacteristics.LENS_FACING_BACK;
        } else {
            defaultCameraId = CameraCharacteristics.LENS_FACING_FRONT;
        }
        openCamera(defaultCameraId);
    }

    private void setUpMediaRecorder() throws IOException {

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        file = new File(Environment.getExternalStorageDirectory() + "/" + UUID.randomUUID().toString() + ".mp4");
        mediaRecorder.setOutputFile(file.getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setMaxDuration(10000);

        Size defaultSize = Utils.getScreenSize(this);
        mediaRecorder.setVideoSize(defaultSize.getHeight(), defaultSize.getWidth());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = Utils.getScreenRotation(this);
        mediaRecorder.setOrientationHint(getOrientation(rotation));
        mediaRecorder.prepare();

        mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mediaRecorder, int i, int i1) {
                if (i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || i1 == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecordingVideo();
                }
            }
        });

        FileObserver observer = new FileObserver(file.getAbsolutePath(), FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int i, @Nullable String s) {
                stopRecordingVideo();
            }
        };
        observer.startWatching();

    }

    private void startRecordingVideo() {
        if (null == defaultCameraDevice || !textureView.isAvailable()) {
            return;
        }

        Utils.popToast(this, "Video Recording Started!");

        try {
            closePreviewSession();
            setUpMediaRecorder();

            Size defaultSize = Utils.getScreenSize(this);
            Utils.checkLog("camera", "Dimensions: " + defaultSize.toString(), null);
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(defaultSize.getHeight(), defaultSize.getWidth());

            captureRequestBuilder = defaultCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            Surface previewSurface = new Surface(texture);
            captureRequestBuilder.addTarget(previewSurface);

            Surface recorderSurface = mediaRecorder.getSurface();
            captureRequestBuilder.addTarget(recorderSurface);

            defaultCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recorderSurface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Camera2Activity.this.cameraCaptureSession = cameraCaptureSession;
                    updatePreview();

                    isRecordingVideo = true;
                    mediaRecorder.start();
                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                        }
                    });*/
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, cameraHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    private void closePreviewSession() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
    }

    private void stopRecordingVideo() {

        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.reset();
            Utils.popToast(this, "Video Recording Captured!");

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    isRecordingVideo = false;
                }
            }, 1000);

            compressVideo(file.getAbsolutePath());
        }

        createCameraPreview();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Interfaces.REQUESTS.SYSTEM && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You can't use camera without permission.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            boolean settingsCanWrite = hasWriteSettingsPermission();
            // If do not have then open the Can modify system settings panel.
            if (!settingsCanWrite) {
                changeWriteSettingsPermission();
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void compressVideo(final String sourcePath) {
        new AsyncTask<String, String, Boolean>() {

            ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                progressDialog = new ProgressDialog(Camera2Activity.this);
                progressDialog.setTitle(getString(R.string.app_name));
                progressDialog.setMessage("Processing");
                progressDialog.setCancelable(false);
                progressDialog.show();

            }

            @Override
            protected Boolean doInBackground(String... strings) {

                File source = new File(sourcePath);
                Utils.checkLog("file", "Source: " + source.getAbsolutePath(), null);
                File target = new File(Environment.getExternalStorageDirectory() + "/" + UUID.randomUUID().toString() + ".mp4");
                Utils.checkLog("file", "Target: " + target.getAbsolutePath(), null);

                return MediaController.getInstance().convertVideo(source.getAbsolutePath(), target.getPath());
            }

            @Override
            protected void onPostExecute(Boolean result) {
                super.onPostExecute(result);

                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                if (result) {
                    File file = new File(sourcePath);
                    if (file.isFile()) {
                        file.delete();
                    }
                    Utils.popToast(Camera2Activity.this, "Video Converted!");

                } else {
                    Utils.popToast(Camera2Activity.this, "Failed to Convert Video!");
                }
            }
        }.execute();
    }

    // Check whether this app has android write settings permission.
    private boolean hasWriteSettingsPermission() {
        boolean ret = true;
        // Get the result from below code.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ret = Settings.System.canWrite(this);
        }
        return ret;
    }

    // Start can modify system settings panel to let user change the write settings permission.
    private void changeWriteSettingsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            startActivity(intent);
        }
    }

    // This function only take effect in real physical android device,
    // it can not take effect in android emulator.
    private void changeScreenBrightness(int screenBrightnessValue) {
        Utils.checkLog("brightness", "Value: " + screenBrightnessValue, null);
        // Change the screen brightness change mode to manual.
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        // Apply the screen brightness value to the system, this will change the value in Settings ---> Display ---> Brightness level.
        // It will also change the screen brightness for the device.
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, screenBrightnessValue);
        /*
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.screenBrightness = screenBrightnessValue / 255f;
        window.setAttributes(layoutParams);
        */
    }

    private int getScreenBrightness() {
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return 255;
    }
}
