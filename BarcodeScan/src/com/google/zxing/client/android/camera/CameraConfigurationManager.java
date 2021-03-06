/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.google.zxing.client.android.activity.CaptureActivity;
import com.google.zxing.client.android.util.CameraUtils;
import com.google.zxing.client.android.util.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 */
final class CameraConfigurationManager {

    private static final String TAG = "CameraConfiguration";
    private final Context context;
    private Point screenResolution;
    private Point cameraResolution;
    public Point getCameraResolution() {
        return cameraResolution;
    }
    public Point getScreenResolution() {
        return screenResolution;
    }
    
    CameraConfigurationManager(Context context) {
        this.context = context;
    }

    /**
     * Reads, one time, values from the camera that are needed by the app.
     */
    public void initFromCameraParameters(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();

        screenResolution = new Point(width, height);
        Log.i(TAG, "Screen resolution: " + screenResolution);
        //cameraResolution = findBestPreviewSizeValue(parameters, screenResolution, false);
        cameraResolution = getBestMatchedPreviewSize(parameters, screenResolution, CameraUtils.getOrientation(context));
        Log.i(TAG, "Camera resolution: " + cameraResolution);
    }

    public void setDesiredCameraParameters(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        if (parameters == null) {
            Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.");
            return;
        }

        setFlashMode(parameters);
        parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
        camera.setParameters(parameters);
    }

    private void setFlashMode(Camera.Parameters parameters) {
        // This is a hack to turn the flash off on the Samsung Galaxy.
        // And this is a hack-hack to work around a different value on the Behold II
        // Restrict Behold II check to Cupcake, per Samsung's advice
        if (Build.MODEL.contains("Behold II") && Build.VERSION.SDK_INT == Build.VERSION_CODES.CUPCAKE) {
            parameters.set("flash-value", 1);
        } else {
            parameters.set("flash-value", 2);
        }

        // This is the standard setting to turn the flash off that all devices should honor.
        SharedPreferences sharedPreference = context.getSharedPreferences(CaptureActivity.CONFIGURE_SHAREDPREFERENCES, Context.MODE_PRIVATE);
        String flashMode = sharedPreference.getString(CaptureActivity.FLASH_MODE_KEY, null);
        List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        if (!Utils.isEmpty(flashMode) && !Utils.isEmpty(supportedFlashModes) && supportedFlashModes.contains(flashMode)) {
            parameters.setFlashMode(flashMode);
        } else {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
    }
    
    //  private static Point findBestPreviewSizeValue(Camera.Parameters parameters,
    //                                                Point screenResolution,
    //                                                boolean portrait) {
    //    Point bestSize = null;
    //    int diff = Integer.MAX_VALUE;
    //    for (Camera.Size supportedPreviewSize : parameters.getSupportedPreviewSizes()) {
    //      int pixels = supportedPreviewSize.height * supportedPreviewSize.width;
    //      if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) {
    //        continue;
    //      }
    //      int supportedWidth = portrait ? supportedPreviewSize.height : supportedPreviewSize.width;
    //      int supportedHeight = portrait ? supportedPreviewSize.width : supportedPreviewSize.height;
    //      int newDiff = Math.abs(screenResolution.x * supportedHeight - supportedWidth * screenResolution.y);
    //      if (newDiff == 0) {
    //        bestSize = new Point(supportedWidth, supportedHeight);
    //        break;
    //      }
    //      if (newDiff < diff) {
    //        bestSize = new Point(supportedWidth, supportedHeight);
    //        diff = newDiff;
    //      }
    //    }
    //    if (bestSize == null) {
    //      Camera.Size defaultSize = parameters.getPreviewSize();
    //      bestSize = new Point(defaultSize.width, defaultSize.height);
    //    }
    //    return bestSize;
    //  }


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
    private static Map<Double, Point> previewSizeCache = new HashMap<Double, Point>();
    public static Point getBestMatchedPreviewSize(Camera.Parameters parameters, Point screenResolution, int orientation) {

        final double BASE_TOLERANCE=0.1333334;
        final int IDEAL_WIDTH=1280;
        // calculate the aspect ratio between the width and height
        double targetRatio = (double)screenResolution.x/screenResolution.y;
        List<Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        // if the camera is in portrait mode, switch the width and height;
        if (orientation%180 == 90) {
            targetRatio = 1/targetRatio;
        }

        // if we've already calculated the preview size, don't do it again
        Point cachedSize = previewSizeCache.get(targetRatio);
        if (cachedSize != null) {
            return cachedSize;
        }

        Size optimalSize = null;
        double tolerance = 0;
        while (optimalSize == null) {
            tolerance += BASE_TOLERANCE;
            int minDiff = Integer.MAX_VALUE;
            for (Size size : supportedPreviewSizes) {
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

        Point bestSzie = new Point(optimalSize.width, optimalSize.height);
        previewSizeCache.put(targetRatio, bestSzie);
        return bestSzie;
    }   
}
