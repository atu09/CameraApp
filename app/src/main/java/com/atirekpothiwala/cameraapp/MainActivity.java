package com.atirekpothiwala.cameraapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    TextureView cameraView;
    ImageView ivSnap;
    ImageView ivSwitchCamera;
    ImageView ivFlash;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 280);
    }

    Integer cameraId;
    CameraManager cameraManager;
    CameraDevice cameraDevice;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest.Builder captureRequestBuilder;
    Size imageDimension;

    boolean isFrontCameraAvailable = false;
    boolean isBackCameraAvailable = false;
    boolean isFlashSupported;

    static final int REQUEST_CAMERA_PERMISSION = 200;
    static final int SELECT_VIDEO = 300;
    Handler handlerBackground;
    HandlerThread handlerBackgroundThread;

    //FFmpeg fFmpeg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.ivFlash = (ImageView) findViewById(R.id.ivFlash);
        this.ivSwitchCamera = (ImageView) findViewById(R.id.ivSwitchCamera);
        this.ivSnap = (ImageView) findViewById(R.id.ivSnap);
        this.cameraView = (TextureView) findViewById(R.id.cameraView);

        //loadFFMpegBinary();

        this.ivSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cameraId == CameraCharacteristics.LENS_FACING_FRONT){
                    cameraId = CameraCharacteristics.LENS_FACING_BACK;
                } else {
                    cameraId = CameraCharacteristics.LENS_FACING_FRONT;
                }
                openCamera(cameraId);
            }
        });

        this.ivSnap.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("StaticFieldLeak")
            @Override
            public void onClick(View view) {
                //Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                //startActivityForResult(intent, SELECT_VIDEO);
                takeSnap();
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    private void compressVideo(final String sourcePath) {
        new AsyncTask<String, String, Boolean>() {

            ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.setTitle(getString(R.string.app_name));
                progressDialog.setMessage("Processing");
                progressDialog.setCancelable(false);
                progressDialog.show();

            }

            @Override
            protected Boolean doInBackground(String... strings) {

                File source = new File(sourcePath);
                Log.d("file>>", "Source: " + source.getAbsolutePath());
                File target = new File(Environment.getExternalStorageDirectory() + "/" + UUID.randomUUID().toString() + ".mp4");
                Log.d("file>>", "Target: " + target.getAbsolutePath());

                return MediaController.getInstance().convertVideo(source.getAbsolutePath(), target.getPath());
            }

            @Override
            protected void onPostExecute(Boolean result) {
                super.onPostExecute(result);

                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                if (result) {
                    Toast.makeText(MainActivity.this, "Video Converted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to Convert Video!", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

/*
    @SuppressLint("StaticFieldLeak")
    private void execute(final String sourcePath) {

        new AsyncTask<String, String, Integer>() {

            ProgressDialog progressDialog;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.setTitle(getString(R.string.app_name));
                progressDialog.setMessage("Converting Video");
                progressDialog.setCancelable(false);
                progressDialog.show();

            }

            @Override
            protected Integer doInBackground(String... strings) {

                File source = new File(sourcePath);
                Log.d("file>>", "Source: " + source.getAbsolutePath());
                File target = new File(Environment.getExternalStorageDirectory() + "/" + UUID.randomUUID().toString() + ".avi");
                Log.d("file>>", "Target: " + target.getAbsolutePath());
                return com.arthenica.mobileffmpeg.FFmpeg.execute("-i", source.getAbsolutePath(), "-vf", "scale=1280:-1", "-c:v", "libx264", "-preset", "veryslow", "-crf", "24", target.getAbsolutePath());

            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);

                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }


                if (result > 0) {
                    Toast.makeText(MainActivity.this, "Video Converted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to Convert Video!", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();

    }
*/
/*
    private void execFFmpegBinary(String sourcePath) {

        try {
            File source = new File(sourcePath);
            Log.d("file>>", "Source: " + source.getAbsolutePath());
            File target = new File(Environment.getExternalStorageDirectory() + "/" + UUID.randomUUID().toString() + ".avi");
            Log.d("file>>", "Target: " + target.getAbsolutePath());
            //"-i", source.getAbsolutePath(), "-c:v", "libxvid", target.getAbsolutePath()
            fFmpeg.execute(new String[]{"-i", source.getAbsolutePath(), "-vf", "scale=1280:-1", "-c:v", "libx264", "-preset", "veryslow", "-crf", "24", target.getAbsolutePath()},
                    new FFmpegExecuteResponseHandler() {

                        ProgressDialog progressDialog;

                        @Override
                        public void onSuccess(String message) {
                            Toast.makeText(MainActivity.this, "FFmpeg Success", Toast.LENGTH_SHORT).show();
                            Log.d("ffmpeg>>", "onSuccess: " + message);
                        }

                        @Override
                        public void onProgress(String message) {
                            Log.d("ffmpeg>>", "onProgress: " + message);
                            progressDialog.setMessage("Processing: " + message);
                        }

                        @Override
                        public void onFailure(String message) {
                            Toast.makeText(MainActivity.this, "FFmpeg Failure", Toast.LENGTH_SHORT).show();
                            Log.d("ffmpeg>>", "onFailure: " + message);
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        }

                        @Override
                        public void onStart() {
                            progressDialog = new ProgressDialog(MainActivity.this);
                            progressDialog.setTitle(getString(R.string.app_name));
                            progressDialog.setMessage("Processing");
                            progressDialog.setCancelable(false);
                            progressDialog.show();

                            Toast.makeText(MainActivity.this, "FFmpeg Started", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFinish() {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
*/

    public String getPath(Uri uri) {
        String[] projection = {MediaStore.Video.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } else
            return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == SELECT_VIDEO) {
            compressVideo(getPath(data.getData()));
            //execFFmpegBinary(getPath(data.getData()));
            //execute(getPath(data.getData()));
        }
    }

/*
    private void loadFFMpegBinary() {
        fFmpeg = FFmpeg.getInstance(MainActivity.this);
        try {
            fFmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    showUnsupportedExceptionDialog();
                }
            });
        } catch (FFmpegNotSupportedException e) {
            showUnsupportedExceptionDialog();
        }
    }

    private void showUnsupportedExceptionDialog() {
        new AlertDialog.Builder(MainActivity.this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.app_name))
                .setMessage("Device not supported")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadFFMpegBinary();
                    }
                })
                .create()
                .show();

    }
*/

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

    void takeSnap() {
        if (cameraDevice == null) {
            return;
        }

        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            Size[] sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

            int width = 640;
            int height = 480;
            if (sizes != null && sizes.length > 0) {
                width = sizes[0].getWidth();
                height = sizes[0].getHeight();
            }
            final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(cameraView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            final File file = new File(Environment.getExternalStorageDirectory() + "/" + UUID.randomUUID().toString() + ".jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = null;
                    try {
                        image = imageReader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream outputStream = null;
                    try {
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    } finally {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    }
                }

            };
            reader.setOnImageAvailableListener(readerListener, handlerBackground);

            final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        cameraCaptureSession.capture(captureBuilder.build(), captureCallback, handlerBackground);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, handlerBackground);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = cameraView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null) {
                        return;
                    }
                    MainActivity.this.cameraCaptureSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            }, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handlerBackground);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void checkCameraAvailability() {

        try {

            cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String ID : cameraManager.getCameraIdList()) {
                Log.d("camera>>", "ID: " + ID);
                if (Integer.valueOf(ID) == CameraCharacteristics.LENS_FACING_FRONT) {
                    isBackCameraAvailable = true;
                } else if (Integer.valueOf(ID) == CameraCharacteristics.LENS_FACING_BACK) {
                    isFrontCameraAvailable = true;
                }
            }

            if (isFrontCameraAvailable && isBackCameraAvailable) {
                ivSnap.setVisibility(View.VISIBLE);
                ivSwitchCamera.setVisibility(View.VISIBLE);
                cameraId = CameraCharacteristics.LENS_FACING_BACK;
            } else {
                ivSwitchCamera.setVisibility(View.GONE);
                if (isFrontCameraAvailable) {
                    ivSnap.setVisibility(View.VISIBLE);
                    cameraId = CameraCharacteristics.LENS_FACING_FRONT;
                } else if (isBackCameraAvailable) {
                    ivSnap.setVisibility(View.VISIBLE);
                    cameraId = CameraCharacteristics.LENS_FACING_BACK;
                } else {
                    Toast.makeText(this, "Something went wrong with cameras, try again later!", Toast.LENGTH_SHORT).show();
                    ivSnap.setVisibility(View.GONE);
                    cameraId = null;
                    return;
                }
            }

            openCamera(cameraId);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    void openCamera(Integer cameraId) {

        if (cameraDevice != null){
            cameraDevice.close();
        }

        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(String.valueOf(cameraId));
            StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert streamConfigurationMap != null;
            imageDimension = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO
                }, REQUEST_CAMERA_PERMISSION);
                return;
            }

            cameraManager.openCamera(String.valueOf(cameraId), new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    MainActivity.this.cameraDevice = cameraDevice;
                    createCameraPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    MainActivity.this.cameraDevice.close();
                    MainActivity.this.cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    MainActivity.this.cameraDevice.close();
                    MainActivity.this.cameraDevice = null;
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You can't use camera without permission.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
        if (cameraView.isAvailable()) {
            checkCameraAvailability();
        } else {
            cameraView.setSurfaceTextureListener(this);
        }
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        handlerBackgroundThread.quitSafely();
        try {
            handlerBackgroundThread.join();
            handlerBackgroundThread = null;
            handlerBackground = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        handlerBackgroundThread = new HandlerThread("Camera Background");
        handlerBackgroundThread.start();
        handlerBackground = new Handler(handlerBackgroundThread.getLooper());
    }
}
