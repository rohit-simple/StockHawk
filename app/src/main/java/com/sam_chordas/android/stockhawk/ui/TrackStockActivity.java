package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.db.chart.model.LineSet;
import com.db.chart.view.ChartView;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.receiver.StockTaskServiceBroadcastReceiver;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;

/*
    created by rohit
 */

public class TrackStockActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private final String LOG_TAG = TrackStockActivity.class.getSimpleName();
    private final int LOADER_READ_SYMBOL_DETAILS = 0;
    private String mSymbol;
    private String[] mDates;
    private float[] mBidValues;
    private StockTaskServiceBroadcastReceiver mStockTaskServiceBroadcastReceiver;

    @BindView(R.id.linechart) protected ChartView mChartView;
    @BindView(R.id.trackstockactivity_error_view) protected TextView mErrorView;

    public static final String INTENT_EXTRA_SYMBOL = "symbol";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_stock);
        ButterKnife.bind(this);

        mStockTaskServiceBroadcastReceiver = new StockTaskServiceBroadcastReceiver(this);
        mChartView.setVisibility(View.VISIBLE);
        mErrorView.setVisibility(View.INVISIBLE);

        Intent receivedIntent = getIntent();
        if (receivedIntent == null || !receivedIntent.hasExtra(INTENT_EXTRA_SYMBOL)) {
            Toast.makeText(this, getString(R.string.toast_some_problem), Toast.LENGTH_SHORT).show();
        } else {
            mSymbol = receivedIntent.getStringExtra(INTENT_EXTRA_SYMBOL);
            setTitle(mSymbol.toUpperCase(Locale.US) + " " + getTitle());
            if (TextUtils.isEmpty(mSymbol)) {
                Toast.makeText(this, getString(R.string.toast_some_problem), Toast.LENGTH_SHORT).show();
            } else {
                getLoaderManager().initLoader(LOADER_READ_SYMBOL_DETAILS, null, this);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mStockTaskServiceBroadcastReceiver, new IntentFilter(StockTaskService.INTENT_ACTION_TOAST));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mStockTaskServiceBroadcastReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_track_stock, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public android.content.Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(this,
                QuoteProvider.Quotes.withSymbol(mSymbol),
                new String[]{
                        QuoteColumns.BID_PRICE,
                        QuoteColumns.IS_HISTORY_LATEST,
                        QuoteColumns.HISTORY
                },
                null,
                null,
                null);
    }

    @Override
    public void onLoadFinished(android.content.Loader<Cursor> loader, Cursor cursor) {
        if (cursor.moveToFirst()) {
            String bidPrice = cursor.getString(cursor.getColumnIndex(QuoteColumns.BID_PRICE));
            String history = cursor.getString(cursor.getColumnIndex(QuoteColumns.HISTORY));
            int isHistoryLatest = cursor.getInt(cursor.getColumnIndex(QuoteColumns.IS_HISTORY_LATEST));
            boolean isHistoryUpdateRequired = false;

            if (TextUtils.isEmpty(bidPrice)) {
                mChartView.setVisibility(View.INVISIBLE);
                mErrorView.setVisibility(View.VISIBLE);
                mErrorView.setText(getString(R.string.trackstock_error_current_bidprice_unavailable));
            } else if (TextUtils.isEmpty(history)) {
                mChartView.setVisibility(View.INVISIBLE);
                mErrorView.setVisibility(View.VISIBLE);
                mErrorView.setText(getString(R.string.trackstock_error_history_unavailable));
                isHistoryUpdateRequired = true;
            } else if (isHistoryLatest == 0) {
                mChartView.setVisibility(View.INVISIBLE);
                mErrorView.setVisibility(View.VISIBLE);
                mErrorView.setText(getString(R.string.trackstock_error_history_outdated));
                isHistoryUpdateRequired = true;
            } else {
                mChartView.setVisibility(View.VISIBLE);
                mErrorView.setVisibility(View.INVISIBLE);

                if(fillUpDatesAndValuesArray(history, bidPrice)){
                    if (mDates.length != mBidValues.length) {
                        Log.e(LOG_TAG, "can't proceed with chart drawing as number of dates don't match with number of values");
                    } else {
                        //only showing first date, mid date and today
                        int mid = mDates.length / 2;
                        for (int i = 1; i < mDates.length - 1; i++) {
                            if (i != mid) {
                                mDates[i] = "";
                            }
                        }
                        mDates[mDates.length - 1] = getString(R.string.trackstock_label_at_moment);

                        //calculating minimum and maximum value - to be used later
                        float minValue = 0, maxValue = 0;
                        for (int i = 0; i < mBidValues.length; i++) {
                            float value = mBidValues[i];
                            if (i == 0) {
                                minValue = value;
                                maxValue = value;
                            } else {
                                if (value < minValue) {
                                    minValue = value;
                                } else if (value > maxValue) {
                                    maxValue = value;
                                }
                            }
                        }

                        //determining minimum and maximum value for the chart as well as step
                        int minValueForChart = (int) minValue;
                        int maxValueForChart = (int) maxValue;

                        //to make sure some space is left below the line
                        if (minValueForChart - 50 < 0) {
                            minValueForChart = 0;
                        } else {
                            minValueForChart -= 50;
                        }

                        //we like to show only 3 labels on y axis too (to keep symmetry with x axis)
                        //step has to be a divisor of difference between minimum and maximum values, so making required tweak
                        maxValueForChart = maxValueForChart + (3 - ((maxValueForChart - minValueForChart) % 3));
                        int step = (maxValueForChart - minValueForChart) / 3;

                        //preparing and customizing dataSet
                        LineSet dataSet = new LineSet(mDates, mBidValues);
                        dataSet.setColor(getResources().getColor(R.color.material_blue_500));

                        mChartView.addData(dataSet);
                        mChartView.setAxisColor(getResources().getColor(R.color.material_blue_500));
                        mChartView.setAxisBorderValues(minValueForChart, maxValueForChart, step);
                        mChartView.setLabelsColor(getResources().getColor(R.color.line_chart_labels_color));
                        mChartView.show();
                    }
                }
            }

            if(isHistoryUpdateRequired){
                if(Utils.isInternetConnected(this)){
                    Intent serviceIntent = new Intent(this, StockIntentService.class);
                    serviceIntent.putExtra(StockIntentService.INTENT_EXTRA_TAG, StockTaskService.TAG_PARTICULAR_HISTORY_UPDATE);
                    serviceIntent.putExtra(StockIntentService.INTENT_EXTRA_SYMBOL, mSymbol);
                    startService(serviceIntent);
                }else{
                    mErrorView.setText(mErrorView.getText() + "\n" + getString(R.string.trackstock_error_cant_update));
                }
            }
        }
    }

    private boolean fillUpDatesAndValuesArray(String history, String currentValue) {
        boolean result = false;
        JSONObject jsonObject;
        JSONArray jsonArray;

        try{
            jsonObject = new JSONObject(history);
            jsonArray = jsonObject.getJSONArray("history_array");
            mDates = new String[jsonArray.length() + 1];
            mBidValues = new float[jsonArray.length() + 1];
            for(int i = 0; i < jsonArray.length(); i++){
                jsonObject = jsonArray.getJSONObject(i);
                mDates[i] = jsonObject.getString("date");
                mBidValues[i] = Float.valueOf(jsonObject.getString("close"));
            }
            mDates[jsonArray.length()] = "";    //this will be set by calling function itself so empty here
            mBidValues[jsonArray.length()] = Float.valueOf(currentValue);
            result = true;
        }catch(JSONException e){
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public void onLoaderReset(android.content.Loader<Cursor> loader) {
        mChartView.dismiss();
        getLoaderManager().restartLoader(LOADER_READ_SYMBOL_DETAILS, null, this);
    }
}
