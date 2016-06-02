package com.sam_chordas.android.stockhawk.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.sam_chordas.android.stockhawk.service.StockTaskService;

/*
    created by rohit
    [purpose- to be used by activities to listen to local broadcasts sent by StockTaskService
 */
public class StockTaskServiceBroadcastReceiver extends BroadcastReceiver{
    private Activity mActivity;

    public StockTaskServiceBroadcastReceiver(Activity activity){
        mActivity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(StockTaskService.INTENT_ACTION_TOAST)) {
            if (intent.hasExtra(StockTaskService.INTENT_EXTRA_MESSAGE)) {
                if(mActivity != null){
                    Toast.makeText(mActivity, intent.getStringExtra(StockTaskService.INTENT_EXTRA_MESSAGE),
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
