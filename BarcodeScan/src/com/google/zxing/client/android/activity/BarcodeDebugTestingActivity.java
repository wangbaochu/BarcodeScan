package com.google.zxing.client.android.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.zxing.client.R;

public class BarcodeDebugTestingActivity extends Activity {

    //**********************************************************
    //************ true will enter into debug mode *************
    public static boolean DEBUG = false;
    //**********************************************************
    
	TextView textview;
	Button btn;
	ImageView mImageView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.barcode_debug_testing);
		textview = (TextView) findViewById(R.id.textView1);
		btn = (Button) findViewById(R.id.button1);
		btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				startActivityForResult(new Intent(BarcodeDebugTestingActivity.this, CaptureActivity.class), 101);
			}
		});
		
		mImageView = (ImageView) findViewById(R.id.image_barcode);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		if(resultCode == RESULT_OK){
			if(requestCode == 101){
				textview.setText(data.getStringExtra(CaptureActivity.BARCODE_STRING));
				if (DEBUG) {
				    mImageView.setImageBitmap(CaptureActivity.sBarcodeBitmap);
				}
			}
		}
	}

}
