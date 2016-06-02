package com.sam_chordas.android.stockhawk.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.TaskParams;

/**
 * Created by sam_chordas on 10/1/15.
 * Modified by rohit
 */
public class StockIntentService extends IntentService {
    public static final String INTENT_EXTRA_TAG = "tag";
    public static final String INTENT_EXTRA_SYMBOL = "symbol";

    public StockIntentService() {
        super(StockIntentService.class.getName());
    }

    public StockIntentService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(StockIntentService.class.getSimpleName(), "Stock Intent Service");

        StockTaskService stockTaskService = new StockTaskService(this);

        Bundle args = new Bundle();
        if (intent.getStringExtra(INTENT_EXTRA_TAG).equals(StockTaskService.TAG_ADD) ||
                intent.getStringExtra(INTENT_EXTRA_TAG).equals(StockTaskService.TAG_PARTICULAR_HISTORY_UPDATE)) {
            args.putString(StockTaskService.ARGS_EXTRA_SYMBOL, intent.getStringExtra(INTENT_EXTRA_SYMBOL));
        }

        // We can call OnRunTask from the intent service to force it to run immediately instead of
        // scheduling a task.
        stockTaskService.onRunTask(new TaskParams(intent.getStringExtra(INTENT_EXTRA_TAG), args));
    }
}
