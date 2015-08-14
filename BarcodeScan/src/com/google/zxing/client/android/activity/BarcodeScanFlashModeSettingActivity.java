package com.google.zxing.client.android.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.zxing.client.R;

public class BarcodeScanFlashModeSettingActivity extends Activity implements OnCancelListener {
    
    public static final String FLASH_MODE_STATE_INDEX ="FLASH_MODE_STATE_INDEX";
    
    private SharedPreferences mSharedPreferences;
    private ListView mListView;
    private AlertDialog mDialog;
    private static final int[] flashModeStateIconIDs = {
        R.drawable.barcode_scan_flash_mode_auto,
        R.drawable.barcode_scan_flash_mode_on,
        R.drawable.barcode_scan_flash_mode_off
    };
    
    private static final String[] flashModeStateValues = new String[] {
        Camera.Parameters.FLASH_MODE_AUTO,
        // use TORCH instead of ON, because ON has the same effect as AUTO.
        Camera.Parameters.FLASH_MODE_TORCH,
        Camera.Parameters.FLASH_MODE_OFF
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferences = getSharedPreferences(CaptureActivity.CONFIGURE_SHAREDPREFERENCES, Context.MODE_PRIVATE);
        
        ItemViewAdapter listAdapter = new ItemViewAdapter(this, android.R.layout.simple_list_item_single_choice, 
                getResources().getStringArray(R.array.camera_flash_mode_choice));
                
        mListView = new ListView(this);
        mListView.setAdapter(listAdapter);
        mListView.setItemsCanFocus(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mListView.setItemChecked(mSharedPreferences.getInt(FLASH_MODE_STATE_INDEX, 2), true);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mSharedPreferences.edit()
                .putString(CaptureActivity.FLASH_MODE_KEY, flashModeStateValues[position])
                .putInt(FLASH_MODE_STATE_INDEX, position).commit();
                
                //remember to dismiss the dialog, or window leak exception will happen.
                if (mDialog != null) {
                    mDialog.dismiss();
                    mDialog = null;
                }
                
                BarcodeScanFlashModeSettingActivity.this.finish();
            }
        });
        
        mDialog = new AlertDialog.Builder(this).setTitle(R.string.camera_flash_mode_select_title).setIcon(android.R.drawable.ic_menu_more).create();
        mDialog.setView(mListView);
        mDialog.setOnCancelListener(this);
        mDialog.show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
    }
      
    @Override
    public void onCancel(DialogInterface arg0) {
        BarcodeScanFlashModeSettingActivity.this.finish();
    }
        
    //Adapter for flash mode selection list. 
    private class ItemViewAdapter extends ArrayAdapter<String> {
        public ItemViewAdapter(Context context, int textViewResourceId, String[] objects) {
            super(context, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CheckedTextView view = (CheckedTextView) super.getView(position, convertView, parent);
            
            SpannableStringBuilder spanned = new SpannableStringBuilder();
            int start = spanned.length();
            spanned.append('a');
            int end = spanned.length();
            spanned.setSpan(new ImageSpan(BarcodeScanFlashModeSettingActivity.this, flashModeStateIconIDs[position]), 
                    start, end, ImageSpan.ALIGN_BASELINE);
            spanned.append(" ");
            spanned.append(view.getText().toString());
            view.setText(spanned);
            
            return view;
        } 
    }
}
