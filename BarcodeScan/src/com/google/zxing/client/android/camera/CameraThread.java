package com.google.zxing.client.android.camera;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
/**
 * This class represents a thread with a handler that data sources can use to
 * handle Android events.
 */
public class CameraThread extends Thread {
    private Handler mHandler = null;
    private Camera mCamera = null;
    private final CountDownLatch mThreadInitLatch;
    private final CountDownLatch mCameraOpenLatch;
    private final CountDownLatch mCameraReleaseLatch;
    public CameraThread() {
        mThreadInitLatch = new CountDownLatch(1);
        mCameraOpenLatch = new CountDownLatch(1);
        mCameraReleaseLatch = new CountDownLatch(1);
    }    //Actually, start() is called in UI thread.
    @Override
    public void start() {
        super.start();
        try {
        	mThreadInitLatch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Could not start camera thread");
        }
    }
    @Override
    public void run() {
        Looper.prepare();
        mHandler = new Handler();
        mThreadInitLatch.countDown();
        Looper.loop();
    }
    public Camera getCamera() {
        return mCamera;
    }
    public void openAwait(long timeout, TimeUnit units) throws InterruptedException {
    	mCameraOpenLatch.await(timeout, units);
    }
    public void releaseAwait(long timeout, TimeUnit units) throws InterruptedException {
    	mCameraReleaseLatch.await(timeout, units);
    }
    /**
     * Called from main UI thread
     */
    public void openCamera() {
    	mHandler.post(new Runnable() {
    		@Override
    		public void run() {
    			try {
    				mCamera = Camera.open();
    			} catch (Throwable t) {
    				t.printStackTrace();
    			} finally {
    				mCameraOpenLatch.countDown();
    			}
    		}
    	});
    }
    
    /**
     * Called from main UI thread
     */
    public void releaseCamera() {
    	mHandler.postAtFrontOfQueue(new Runnable() {
    		@Override
    		public void run() {
    			try {
    				mCamera.setPreviewCallback(null);
    				mCamera.stopPreview();
    			} catch (Throwable t) {
    			    // ignore: tried to stop a non-existent preview
    			}    			    			try {    			    mCamera.release();                } catch (Exception e){                    // ignore: tried to stop a non-existent preview                }    			    			mCamera = null;    			shutdownCameraThread();
    			mCameraReleaseLatch.countDown();
    		}
    	});
    }
    private void shutdownCameraThread() {
        mHandler.getLooper().quit();
    }
}
