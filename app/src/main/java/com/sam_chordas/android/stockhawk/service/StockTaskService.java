package com.sam_chordas.android.stockhawk.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.widget.StockAppWidgetProvider;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by sam_chordas on 9/30/15.
 * Modified by rohit
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService {
    private String LOG_TAG = StockTaskService.class.getSimpleName();

    private OkHttpClient client = new OkHttpClient();
    private Context mContext;
    private StringBuilder mStoredSymbols = new StringBuilder();
    private boolean isUpdate;

    //for other services or activities to pass on information to this service
    public static final String TAG_INIT = "init";
    public static final String TAG_PERIODIC = "periodic";
    public static final String TAG_ADD = "add";
    public static final String TAG_PARTICULAR_HISTORY_UPDATE = "particular_history_update";
    public static final String TAG_INVALIDATE_HISTORIES = "invalidate_histories";
    public static final String ARGS_EXTRA_SYMBOL = "symbol";

    //for passing information to activities
    public static final String INTENT_ACTION_TOAST = "com.sam_chordas.android.stockhawk.toast";
    public static final String INTENT_EXTRA_MESSAGE = "message";

    public StockTaskService() {
    }

    public StockTaskService(Context context) {
        mContext = context;
    }

    String fetchData(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    @Override
    public int onRunTask(TaskParams params) {

        Cursor initQueryCursor;
        StringBuilder urlStringBuilder = new StringBuilder();
        String symbol = null;
        //Base URL for Yahoo query
        urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");

        if (mContext == null) {
            mContext = this;
        }

        if (params.getExtras() != null) {
            symbol = params.getExtras().getString(ARGS_EXTRA_SYMBOL);
        }

        switch (params.getTag()) {
            case TAG_INVALIDATE_HISTORIES:
                ContentValues contentValues = new ContentValues();
                contentValues.put(QuoteColumns.IS_HISTORY_LATEST, 0);
                mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues, null, null);

                return GcmNetworkManager.RESULT_SUCCESS;

            case TAG_PARTICULAR_HISTORY_UPDATE:
                Calendar yesterday = Calendar.getInstance();
                Calendar twentyDaysBack = Calendar.getInstance();

                yesterday.add(Calendar.DATE, -1);
                twentyDaysBack.add(Calendar.DATE, -20);

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                String yesterdayString = simpleDateFormat.format(yesterday.getTime());
                String twentyDaysBackString = simpleDateFormat.format(twentyDaysBack.getTime());

                try {
                    urlStringBuilder.append(URLEncoder.encode(
                            "select * from yahoo.finance.historicaldata where symbol = \""
                                    + symbol
                                    + "\""
                                    + " and startDate = \""
                                    + twentyDaysBackString
                                    + "\""
                                    + " and endDate = \""
                                    + yesterdayString
                                    + "\"", "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                break;

            case TAG_INIT:
            case TAG_PERIODIC:
            case TAG_ADD:
                try {
                    urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol "
                            + "in (", "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
        }

        switch (params.getTag()) {
            case TAG_INIT:
            case TAG_PERIODIC:
                initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                        null, null,
                        null, null);
                if (initQueryCursor == null || initQueryCursor.getCount() == 0) {
                    isUpdate = false;
                    // Init task. Populates DB with quotes for the symbols seen below
                    try {
                        urlStringBuilder.append(
                                URLEncoder.encode("\"GOOG\")", "UTF-8"));

                        //notify activity that database is empty, so fetching GOOG stock for sample
                        Intent intent = new Intent(INTENT_ACTION_TOAST);
                        intent.putExtra(INTENT_EXTRA_MESSAGE, mContext.getString(R.string.toast_service_fetching_GOOG_default));
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else {
                    isUpdate = true;
                    DatabaseUtils.dumpCursor(initQueryCursor);
                    initQueryCursor.moveToFirst();
                    for (int i = 0; i < initQueryCursor.getCount(); i++) {
                        mStoredSymbols.append("\"");
                        mStoredSymbols.append(initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol")));
                        mStoredSymbols.append("\",");
                        initQueryCursor.moveToNext();
                    }
                    mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
                    try {
                        urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case TAG_ADD:
                isUpdate = false;
                try {
                    urlStringBuilder.append(URLEncoder.encode("\"" + symbol + "\")", "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
        }

        // finalize the URL for the API query.
        urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
                + "org%2Falltableswithkeys&callback=");

        String urlString;
        String getResponse;
        int result = GcmNetworkManager.RESULT_FAILURE;

        urlString = urlStringBuilder.toString();
        try {
            getResponse = fetchData(urlString);
            result = GcmNetworkManager.RESULT_SUCCESS;

            if (params.getTag().equals(TAG_PARTICULAR_HISTORY_UPDATE)) {
                ContentValues contentValues = new ContentValues();
                String history = Utils.historyResponseToString(getResponse);
                if (!TextUtils.isEmpty(history)) {
                    contentValues.put(QuoteColumns.HISTORY, history);
                    contentValues.put(QuoteColumns.IS_HISTORY_LATEST, 1);
                    mContext.getContentResolver().update(
                            QuoteProvider.Quotes.withSymbol(symbol),
                            contentValues,
                            QuoteColumns.SYMBOL + "=?",
                            new String[]{symbol}
                    );
                }
            } else {
                try {
                    if (isUpdate) {
                        mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                                Utils.quoteJsonToContentVals(getResponse, true));
                    } else {
                        if (Utils.isStockValid(getResponse)) {
                            mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                                    Utils.quoteJsonToContentVals(getResponse, false));
                        } else {
                            Intent intent = new Intent(INTENT_ACTION_TOAST);
                            intent.putExtra(INTENT_EXTRA_MESSAGE, symbol + " " + mContext.getString(R.string.toast_service_invalid_stock));
                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                        }
                    }

                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(LOG_TAG, "Error applying batch insert", e);
                }
            }
        } catch (IOException e) {
            if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
                Intent intent = new Intent(INTENT_ACTION_TOAST);
                intent.putExtra(INTENT_EXTRA_MESSAGE, mContext.getString(R.string.toast_service_connection_timeout));
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
            } else {
                e.printStackTrace();
            }
        }

        //in case of ADD, INIT or PERIODIC, widget should also be updated
        Intent intent = new Intent(StockAppWidgetProvider.ACTION_DATA_UPDATED);
        mContext.sendBroadcast(intent);

        return result;
    }

}
