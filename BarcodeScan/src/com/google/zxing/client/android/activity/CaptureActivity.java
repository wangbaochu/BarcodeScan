package com.google.zxing.client.android.activity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.client.android.BeepManager;
import com.google.zxing.client.android.CaptureActivityHandler;
import com.google.zxing.client.android.DecodeFormatManager;
import com.google.zxing.client.android.FinishListener;
import com.google.zxing.client.android.InactivityTimer;
import com.google.zxing.client.android.Intents;
import com.google.zxing.client.android.ViewfinderView;
import com.google.zxing.client.android.Intents.Scan;
import com.google.zxing.client.android.camera.CameraManager;
import com.zxing.client.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import java.io.IOException;
import java.util.Collection;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 * 
 */
public final class CaptureActivity extends Activity implements
		SurfaceHolder.Callback {

	private static final String TAG = CaptureActivity.class.getSimpleName();
	private static final String PRODUCT_SEARCH_URL_PREFIX = "http://www.google";
	private static final String PRODUCT_SEARCH_URL_SUFFIX = "/m/products/scan";
	private static final String[] ZXING_URLS = {"http://zxing.appspot.com/scan", "zxing://scan/" };
	public static final int HISTORY_REQUEST_CODE = 0x0000bacc;
	public static final String BARCODE_STRING = "BARCODE_STRING";

	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private Result savedResultToShow;
	private ViewfinderView viewfinderView;
	private boolean hasSurface;
	private String sourceUrl;
	private Collection<BarcodeFormat> decodeFormats;
	private String characterSet;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;
	
	private ImageButton helpButton;
    private ImageButton flashModeButton;
    public static final String CONFIGURE_SHAREDPREFERENCES = "Configure_SharedPreferences";
    public static final String FLASH_MODE_KEY = "FLASH_MODE_KEY";

    public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.barcode_capture);

		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);
		
		// CameraManager must be initialized here, not in onCreate(). This is
        // necessary because we don't
        // want to open the camera driver and measure the screen size if we're
        // going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the
        // wrong size and partially
        // off screen.
        cameraManager = new CameraManager(getApplication());
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);
        
        helpButton = (ImageButton) findViewById(R.id.barcode_scan_help_icon_button);
        helpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CaptureActivity.this, BarcodeScanHelpActivity.class);
                startActivity(intent);
            } 
        });
        
        flashModeButton = (ImageButton) findViewById(R.id.barcode_scan_flash_mode_button);
        flashModeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CaptureActivity.this, BarcodeScanFlashModeSettingActivity.class);
                startActivity(intent);
            }   
        });
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		Intent intent = getIntent();
		decodeFormats = null;
		characterSet = null;
		handler = null;
		
		updateCameraFlashMode();
		inactivityTimer.onResume();
		beepManager.updatePrefs();
		
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		if (intent != null) {
			String action = intent.getAction();
			String dataString = intent.getDataString();
			if (Intents.Scan.ACTION.equals(action)) {
				// Scan the formats the intent requested, and return the result
				// to the calling activity.
				decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
				if (intent.hasExtra(Intents.Scan.WIDTH)
						&& intent.hasExtra(Intents.Scan.HEIGHT)) {
					int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
					int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
					if (width > 0 && height > 0) {
						cameraManager.setManualFramingRect(width, height);
					}
				}
			} else if (dataString != null
					&& dataString.contains(PRODUCT_SEARCH_URL_PREFIX)
					&& dataString.contains(PRODUCT_SEARCH_URL_SUFFIX)) {
				// Scan only products and send the result to mobile Product Search.
				sourceUrl = dataString;
				decodeFormats = DecodeFormatManager.PRODUCT_FORMATS;
			} else if (isZXingURL(dataString)) {
				// Scan formats requested in query string (all formats if none
				// specified).
				// If a return URL is specified, send the results there.
				// Otherwise, handle it ourselves.
				sourceUrl = dataString;
				Uri inputUri = Uri.parse(sourceUrl);
				decodeFormats = DecodeFormatManager.parseDecodeFormats(inputUri);
			}
			characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);
		}
	}

	private static boolean isZXingURL(String dataString) {
		if (dataString == null) {
			return false;
		}
		for (String url : ZXING_URLS) {
			if (dataString.startsWith(url)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		beepManager.release();
		
		cameraManager.closeDriver();
		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_FOCUS
				|| keyCode == KeyEvent.KEYCODE_CAMERA) {

		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
			return true;
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
		// Bitmap isn't used yet -- will be used soon
		if (handler == null) {
			savedResultToShow = result;
		} else {
			if (result != null) {
				savedResultToShow = result;
			}
			if (savedResultToShow != null) {
				Message message = Message.obtain(handler,
						R.id.decode_succeeded, savedResultToShow);
				handler.sendMessage(message);
			}
			savedResultToShow = null;
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG,
					"*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
		cameraManager.stopPreview();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	/**
	 * A valid barcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult
	 *            The contents of the barcode.
	 * @param barcode
	 *            A greyscale bitmap of the camera data which was decoded.
	 */
	public static Bitmap sBarcodeBitmap = null;
	public void handleDecode(Result rawResult, Bitmap barcode) {
	    if (BarcodeDebugTestingActivity.DEBUG) {
	        sBarcodeBitmap = barcode;
	    }
	    inactivityTimer.onActivity();
	    Intent intent = new Intent();
	    if (rawResult != null) {
	        intent.putExtra(BARCODE_STRING, rawResult.getText());
	    }
	    setResult(Activity.RESULT_OK, intent);
	    Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
	    v.vibrate(100);
	    finish();
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			cameraManager.openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, decodeFormats,
						characterSet, cameraManager);
			}
			decodeOrStoreSavedBitmap(null, null);
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
		builder.setOnCancelListener(new FinishListener(this));
		builder.show();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}
	
	/**
     * Set the camera flash mode parameter and update the flash mode button state.
     */
    private void updateCameraFlashMode() {
        //initiate camera flash mode.
       SharedPreferences sharedPreference = getSharedPreferences(CONFIGURE_SHAREDPREFERENCES, Context.MODE_PRIVATE);
       String camera_flash_mode = sharedPreference.getString(FLASH_MODE_KEY, null);
        if (camera_flash_mode == null) {
            //set the default flash mode as off.
            camera_flash_mode = Camera.Parameters.FLASH_MODE_OFF;
            sharedPreference.edit().putString(FLASH_MODE_KEY, camera_flash_mode)
            .putInt(BarcodeScanFlashModeSettingActivity.FLASH_MODE_STATE_INDEX, 2).commit();    
        }
            
        if (Camera.Parameters.FLASH_MODE_OFF.equals(camera_flash_mode)) {
            flashModeButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.barcode_scan_flash_mode_off));
        } else if (Camera.Parameters.FLASH_MODE_TORCH.equals(camera_flash_mode)) {
            flashModeButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.barcode_scan_flash_mode_on));
        } else if (Camera.Parameters.FLASH_MODE_AUTO.equals(camera_flash_mode)) {
            flashModeButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.barcode_scan_flash_mode_auto));
        }
    }
}
