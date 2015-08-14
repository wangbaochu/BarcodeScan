package com.google.zxing.client.android.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.zxing.client.android.camera.CameraThread;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * A class helper to do camera operation
 */
public class CameraUtils {

    public static int CAMERA_PICTURE_SIZE_LIMITATION_SMALL = 100000; //100kB
    public static int CAMERA_PICTURE_EXPECTED_MAX_WIDTH_SMALL = 300;
    public static int CAMERA_PICTURE_EXPECTED_MAX_HEIGHT_SMALL = 400;
    
    public static int CAMERA_PICTURE_SIZE_LIMITATION = 300000; //300kB
    public static int CAMERA_PICTURE_EXPECTED_MAX_WIDTH = 480;
    public static int CAMERA_PICTURE_EXPECTED_MAX_HEIGHT = 640;
    
    
    private static final int MAXIMUM_FILES = 100;
    private static int viewId = 0;
    public static boolean isEnableCropImage = false;
    
    //The Maximum FPS rate to send frames to FSE. 
    //private static final int CAMERA_MAX_FRAMES_PER_SECOND = 30;
    public static Camera openCamera(CameraThread cameraThread) {
        cameraThread.start();
        // wait for it to complete
        cameraThread.openCamera();
        try {
            cameraThread.openAwait(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            //do nothing
        }
        return cameraThread.getCamera();
    }
    
    public static boolean releaseCamera(CameraThread cameraThread, Camera camera) {
        if (cameraThread != null && camera != null) {
            try {
                cameraThread.releaseCamera();
                cameraThread.releaseAwait(5, TimeUnit.SECONDS);
                return true;
            } catch (Exception e){
                e.printStackTrace(); 
            }
        }
        
        return false;
    }

    /**
     * switch the camera flash mode, default is FLASH_MODE_OFF
     * @return true if switch successfully, otherwise false
     */
    public static boolean switchCameraFlashMode(Camera camera) {
        // This is the standard setting to flash mode that all devices should honor.
        if (camera != null) {
            Parameters parameters = camera.getParameters();
            String flashMode = parameters.getFlashMode();
            if (Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                camera.setParameters(parameters);
                return true;
            } else  if (Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameters);
                return true;
            }
        }
        return false;
    }

    public static void setCameraParameters(Camera camera, Rect surfaceSize, PreviewCallback callback, int orientation) {
        Camera.Parameters parameters = camera.getParameters();
        
        // We detected that in Meizu M9 phone, JPEG is not supported, so we have to detect whether jpeg picture format is supported
        // We don't use api parameters.getSupportedPictureFormats() is because it is supported from 2.0 and although snap it feature
        // only exist in 2.0+, but we want to be consistent with remember feature since it is supported in 1.5+
        final String pictureFormatValues = parameters.get("picture-format-values");
        if (pictureFormatValues != null && pictureFormatValues.length() > 0  && pictureFormatValues.contains("jpeg")) {
            parameters.setPictureFormat(PixelFormat.JPEG);
        }
        
        //set preview size
        List<Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        Size previewSize = CameraUtils.getBestMatchedSize(supportedPreviewSizes, surfaceSize.right, surfaceSize.bottom, orientation);
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        
        //set picture size 
        List<Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
        //Note: here we force to open camera in landscape mode.
        Size puictureSize = CameraUtils.getBestMatchedSize(supportedPictureSizes, CAMERA_PICTURE_EXPECTED_MAX_HEIGHT, CAMERA_PICTURE_EXPECTED_MAX_WIDTH, orientation);
        parameters.setPictureSize(puictureSize.width, puictureSize.height);
        
        //set flash mode
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(parameters);
        camera.setPreviewCallback(callback);
    }

    /**
     * Translate a point from camera coordinates to canvas preview coordinates
     * @param sourcePoint
     * @param sorceRect
     * @param targetRect
     * @param orientationDegrees
     * @return
     */
    public static PointF translatePoint(PointF sourcePoint, int sourceWidth, int sourceHeight, 
            int targeWidth, int targetHeight, int orientationDegrees) {
        switch (orientationDegrees) {
        case 0:  // landscape right
            return new PointF(sourcePoint.x*targeWidth/sourceWidth, sourcePoint.y*targetHeight/sourceHeight);
        case 180: // landscape left
            return new PointF((sourceWidth-sourcePoint.x)*targeWidth/sourceWidth, (sourceHeight-sourcePoint.y)*targetHeight/sourceHeight);
        case 270: // upside down portrait, untested, since none of my devices support it
            return new PointF(sourcePoint.y*targeWidth/sourceHeight, (sourceWidth-sourcePoint.x)*targetHeight/sourceWidth);
        default: // 90, normal portrait
            return new PointF((sourceHeight-sourcePoint.y)*targeWidth/sourceHeight, sourcePoint.x*targetHeight/sourceWidth);
        }
    }

    /**
     * Calculate the optimal preview size from the available preview sizes.  The optimal preview has
     * an aspect ratio that matches the display, with a size close to 640x480.  The actual algorithm
     * that we use is:
     *   Choose a tolerance.  We start with .1.
     *   Find all supported sizes with aspect ratios within the tolerance.  For those that match, choose
     *   the one with a width closes to 640.
     *   If we did not find any sizes within the tolerance, increase the tolerance by .1 and try again.
     * @return The aspect ratio to use.
     */
    public static Size getBestMatchedSize(List<Size> supportedSizes, int surfaceWidth, int surfaceHeight, int orientation) {
        final double BASE_TOLERANCE=0.1333334;
        //int IDEAL_WIDTH = 1280;
        int IDEAL_WIDTH = surfaceWidth;
        //calculate the aspect ratio between the width and height
        double targetRatio = (double)surfaceWidth/surfaceHeight;
        // if the camera is in portrait mode, switch the width and height;
        if (orientation%180 == 90) {
            targetRatio = 1/targetRatio;
            IDEAL_WIDTH = surfaceHeight;
        }

        Size optimalSize = null;
        double tolerance = 0;
        while (optimalSize == null) {
            tolerance += BASE_TOLERANCE;
            int minDiff = Integer.MAX_VALUE;
            for (Size size : supportedSizes) {
                double ratio = (double)size.width / size.height;
                if (Math.abs(ratio-targetRatio) < tolerance) {
                    int sizeDiff = Math.abs(size.width - IDEAL_WIDTH);
                    if (sizeDiff < minDiff) {
                        optimalSize = size;
                        minDiff = sizeDiff;
                    }
                }
            }
        }
        return optimalSize;
    }

    /**
     * Determine how the camera is oriented.  This converts from ROTATION attributes to degrees
     * @return degrees for camera rotation
     */
    public static int getOrientation(Context context) {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getOrientation();
        int orientation;
        boolean expectPortrait;
        switch (rotation) {
        case Surface.ROTATION_0:
        default:
            orientation = 90;
            expectPortrait = true;
            break;
        case Surface.ROTATION_90:
            orientation = 0;
            expectPortrait = false;
            break;
        case Surface.ROTATION_180:
            orientation = 270;
            expectPortrait = true;
            break;
        case Surface.ROTATION_270:
            orientation = 180;
            expectPortrait = false;
            break;
        }
        boolean isPortrait = display.getHeight() > display.getWidth();
        if (isPortrait != expectPortrait) {
            orientation = (orientation + 270) % 360; 
        }
        return orientation;
    }
    
    public static boolean compressHighResolutionPicture(Context context, byte[] jpegData, File outputFile) {
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile));
            bos.write(jpegData, 0, jpegData.length);
            /* 调用flush()方法，更新BufferStream */
            bos.flush();
            /* 结束OutputStream */
            bos.close();
            return true;
        } catch (FileNotFoundException e) {
            Toast.makeText(context, "目录不存在", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            Toast.makeText(context, "图片保存失败", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * If the input image size exceed outputMaxSize, compress it and save to the outputFile.
     * @param context
     * @param inputBitmap
     * @param outputFile
     * @param outputMaxSize
     * @return
     */
    public static boolean compressJpegImage(Context context, byte[] jpegData, File outputFile, int outputMaxSize, int reqWidth, int reqHeight) {
        try {
            System.out.println("图片原始大小 = " + jpegData.length/1000 + "KB");

            // 大于 outputMaxSize 压缩
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile));
            if (jpegData.length > outputMaxSize) {
                Bitmap scaledBitmap = jpegToBitmap(jpegData, reqWidth, reqHeight);
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            } else {
                bos.write(jpegData, 0, jpegData.length);
            }
            /* 调用flush()方法，更新BufferStream */
            bos.flush();
            /* 结束OutputStream */
            bos.close();

            System.out.println("图片压缩后大小 = " + outputFile.length()/1000 + "KB");
            return true;

        } catch (FileNotFoundException e) {
            Toast.makeText(context, "目录不存在", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            Toast.makeText(context, "图片保存失败", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Convert the given jpeg data into a Bitmap .
     * The Bitmap will be scaled-down to the desired width and height and to a reasonable data size.
     * @param jpeg
     * @param dstWidth
     * @param dstHeight
     * @return
     */
    public static Bitmap jpegToBitmap(byte[] jpeg, int reqWidth, int reqHeight) {
        
        if (jpeg != null && reqWidth > 0 && reqHeight > 0) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
                options.inJustDecodeBounds = false;
                
                // calculate the sub-sample factor
                options.inSampleSize = 1;
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {

                    // Calculate ratios of height and width to requested height and width
                    final int heightRatio = Math.round((float) options.outHeight / (float) reqHeight);
                    final int widthRatio = Math.round((float) options.outWidth / (float) reqWidth);

                    // Choose the smallest ratio as inSampleSize value, this will guarantee
                    // a final image with both dimensions larger than or equal to the
                    // requested height and width.
                    options.inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
                }

                Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
                if (bitmap.getWidth() != reqWidth || bitmap.getHeight() != reqHeight) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, reqWidth, reqHeight, false);
                }
                return bitmap;
                
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

}
