package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.melnykov.fab.FloatingActionButton;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.receiver.StockTaskServiceBroadcastReceiver;
import com.sam_chordas.android.stockhawk.rest.QuoteCursorAdapter;
import com.sam_chordas.android.stockhawk.rest.RecyclerViewItemClickListener;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;
import com.sam_chordas.android.stockhawk.touch_helper.SimpleItemTouchHelperCallback;

import java.util.Calendar;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;

/*
    created by rohit
 */
public class MyStocksActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private CharSequence mTitle;
    private Intent mServiceIntent;
    private final int CURSOR_LOADER_ID = 0;
    private QuoteCursorAdapter mCursorAdapter;
    private Context mContext;
    private Cursor mCursor;
    private StockTaskServiceBroadcastReceiver mStockTaskServiceBroadcastReceiver;
    private Snackbar networkErrorSnackBar;

    @BindView(R.id.fab)
    protected FloatingActionButton mFab;
    @BindView(R.id.recycler_view)
    protected RecyclerView mRecyclerView;
    @BindView(R.id.activity_my_stocks_error_textview)
    protected TextView mNoStocksErrorTextView;
    @BindView(R.id.activity_my_stocks_parent)
    protected View mParentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;
        mCursor = null;
        mStockTaskServiceBroadcastReceiver = new StockTaskServiceBroadcastReceiver(this);

        setContentView(R.layout.activity_my_stocks);
        ButterKnife.bind(this);

        mFab.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.VISIBLE);
        mNoStocksErrorTextView.setVisibility(View.GONE);

        // The intent service is for executing immediate pulls from the Yahoo API
        // GCMTaskService can only schedule tasks, they cannot execute immediately
        mServiceIntent = new Intent(this, StockIntentService.class);
        if (Utils.isInternetConnected(this)) {
            mServiceIntent.putExtra(StockIntentService.INTENT_EXTRA_TAG, StockTaskService.TAG_INIT);
            startService(mServiceIntent);
        }

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mCursorAdapter = new QuoteCursorAdapter(this, null);
        mRecyclerView.setAdapter(mCursorAdapter);
        mRecyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(this,
                new RecyclerViewItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View v, int position) {
                        if (mCursor != null) {
                            int cursorOldPosition = mCursor.getPosition();
                            if (mCursor.moveToPosition(position)) {
                                Intent intent = new Intent(MyStocksActivity.this, TrackStockActivity.class);
                                intent.putExtra(TrackStockActivity.INTENT_EXTRA_SYMBOL, mCursor.getString(mCursor.getColumnIndex(QuoteColumns.SYMBOL)));

                                mCursor.moveToPosition(cursorOldPosition);  //move cursor back to where it was

                                MyStocksActivity.this.startActivity(intent);
                            } else {
                                Toast.makeText(MyStocksActivity.this, MyStocksActivity.this.getString(R.string.toast_some_problem), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MyStocksActivity.this, MyStocksActivity.this.getString(R.string.toast_some_problem), Toast.LENGTH_SHORT).show();
                        }
                    }
                }));

        getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
        new ItemTouchHelper(callback).attachToRecyclerView(mRecyclerView);

        mFab.attachToRecyclerView(mRecyclerView);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.isInternetConnected(MyStocksActivity.this)) {
                    new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
                            .content(R.string.content_test)
                            .inputType(InputType.TYPE_CLASS_TEXT)
                            .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                                @Override
                                public void onInput(MaterialDialog dialog, CharSequence input) {
                                    // On FAB click, receive user input. Make sure the stock doesn't already exist
                                    // in the DB and proceed accordingly
                                    String inputString = input.toString();
                                    if (TextUtils.isEmpty(inputString)) {
                                        Toast.makeText(MyStocksActivity.this, R.string.new_symbol_toast_empty,
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        Cursor c = getContentResolver().query(QuoteProvider.Quotes.withSymbol(input.toString().toUpperCase(Locale.US)),
                                                new String[]{QuoteColumns._ID}, null, null, null);
                                        if (c.getCount() != 0) {
                                            Toast toast =
                                                    Toast.makeText(MyStocksActivity.this, R.string.new_symbol_toast_already_existing,
                                                            Toast.LENGTH_LONG);
                                            toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                                            toast.show();
                                        } else {
                                            // Add the stock to DB
                                            mServiceIntent.putExtra(StockIntentService.INTENT_EXTRA_TAG, StockTaskService.TAG_ADD);
                                            mServiceIntent.putExtra(StockIntentService.INTENT_EXTRA_SYMBOL, input.toString());
                                            startService(mServiceIntent);
                                        }
                                    }
                                }
                            })
                            .show();
                } else {
                    Toast.makeText(MyStocksActivity.this, MyStocksActivity.this.getString(R.string.new_symbol_network_toast), Toast.LENGTH_LONG).show();
                }
            }
        });


        mTitle = getTitle();
        if (Utils.isInternetConnected(this)) {
            long period = 3600L;
            long flex = 10L;
            // create a periodic task to pull stocks once every hour after the app has been opened. This
            // is so Widget data stays up to date.
            PeriodicTask periodicTask = new PeriodicTask.Builder()
                    .setService(StockTaskService.class)
                    .setPeriod(period)
                    .setFlex(flex)
                    .setTag(StockTaskService.TAG_PERIODIC)
                    .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                    .setRequiresCharging(false)
                    .setUpdateCurrent(true)
                    .build();
            // Schedule task with tag "periodic." This ensure that only the stocks present in the DB
            // are updated.
            GcmNetworkManager.getInstance(this).schedule(periodicTask);

            Calendar tomorrow = Calendar.getInstance();
            Calendar current = Calendar.getInstance();

            tomorrow.add(Calendar.DATE, 1);
            tomorrow.set(Calendar.HOUR, 0);
            tomorrow.set(Calendar.MINUTE, 30);
            tomorrow.set(Calendar.SECOND, 0);
            long startDifferenceInMillis = tomorrow.getTimeInMillis() - current.getTimeInMillis();
            long startDifferenceInSeconds = startDifferenceInMillis / 1000;
            tomorrow.set(Calendar.HOUR, 1);
            long endDifferenceInMillis = tomorrow.getTimeInMillis() - current.getTimeInMillis();
            long endDifferenceInSeconds = endDifferenceInMillis / 1000;

            //schedule INVALIDATE_HISTORIES task for next day between 12:30 AM and 01:00 AM
            Task invalidateHistoryTask = new OneoffTask.Builder()
                    .setService(StockTaskService.class)
                    .setExecutionWindow(startDifferenceInSeconds, endDifferenceInSeconds)
                    .setTag(StockTaskService.TAG_INVALIDATE_HISTORIES)
                    .setUpdateCurrent(true)
                    .setRequiredNetwork(Task.NETWORK_STATE_ANY)
                    .setRequiresCharging(false)
                    .build();
            GcmNetworkManager.getInstance(this).schedule(invalidateHistoryTask);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mStockTaskServiceBroadcastReceiver, new IntentFilter(StockTaskService.INTENT_ACTION_TOAST));
        getLoaderManager().restartLoader(CURSOR_LOADER_ID, null, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mStockTaskServiceBroadcastReceiver);
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.my_stocks, menu);
        restoreActionBar();
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

        if (id == R.id.action_change_units) {
            // this is for changing stock changes from percent value to dollar value
            Utils.showPercent = !Utils.showPercent;
            this.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This narrows the return to only the stocks that are most current.
        return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
                new String[]{
                        QuoteColumns._ID,
                        QuoteColumns.SYMBOL,
                        QuoteColumns.BID_PRICE,
                        QuoteColumns.PERCENT_CHANGE,
                        QuoteColumns.CHANGE,
                        QuoteColumns.IS_UP},
                null,
                null,
                null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCursor = data;
        mCursorAdapter.swapCursor(data);

        if (data == null || data.getCount() == 0) {
            mRecyclerView.setVisibility(View.GONE);
            mNoStocksErrorTextView.setVisibility(View.VISIBLE);
        } else {
            mRecyclerView.setVisibility(View.VISIBLE);
            mNoStocksErrorTextView.setVisibility(View.GONE);
        }

        if (Utils.isInternetConnected(this)) {
            if (networkErrorSnackBar != null && networkErrorSnackBar.isShown()) {
                networkErrorSnackBar.dismiss();
            }
            mFab.setVisibility(View.VISIBLE);
        } else {
            if (networkErrorSnackBar == null || !networkErrorSnackBar.isShownOrQueued()) {
                if (data == null || data.getCount() == 0) {
                    networkErrorSnackBar = Snackbar.make(mParentView, getString(R.string.snackbar_can_not_add_stocks), Snackbar.LENGTH_INDEFINITE);
                } else {
                    networkErrorSnackBar = Snackbar.make(mParentView, getString(R.string.snackbar_out_of_date_stocks), Snackbar.LENGTH_INDEFINITE);
                }
                networkErrorSnackBar.show();
            }
            mFab.setVisibility(View.GONE);
        }

        invalidateOptionsMenu();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCursorAdapter.swapCursor(null);
        mCursor = null;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_change_units);
        if (menuItem != null) {
            if (mCursor == null || mCursor.getCount() == 0) {
                if (menuItem.isVisible()) {
                    menuItem.setVisible(false);
                }
            } else {
                if (!menuItem.isVisible()) {
                    menuItem.setVisible(true);
                }
            }
        }
        return true;
    }
}
