/*
 * Copyright (c) 2009 Amazon.com, Inc.
 */
package com.google.zxing.client.android.activity;

import com.zxing.client.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * Activity to display help with barcode scans help view.
 */
public class BarcodeScanHelpActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        setContentView(R.layout.barcode_scan_help);
        setTitle(R.string.barcode_scan_help_title);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Button btn =  (Button) findViewById(R.id.barcode_scan_help_OK_button);
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }  
        });
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) { 
        if (KeyEvent.KEYCODE_BACK == event.getKeyCode() && 
                KeyEvent.ACTION_DOWN == event.getAction()) { 
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
