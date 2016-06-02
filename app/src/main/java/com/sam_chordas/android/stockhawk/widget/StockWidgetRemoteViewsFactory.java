package com.sam_chordas.android.stockhawk.widget;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.ui.TrackStockActivity;

/*
    created by rohit
    help taken from https://github.com/udacity/Advanced_Android_Development/
 */
public class StockWidgetRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory{
    private Context mContext;
    private Cursor mCursor = null;

    private final String[] QUOTE_COLUMNS = {
            QuoteColumns._ID,
            QuoteColumns.SYMBOL,
            QuoteColumns.BID_PRICE,
            QuoteColumns.CHANGE,
            QuoteColumns.PERCENT_CHANGE,
            QuoteColumns.IS_UP
    };

    private final int INDEX_QOUTE_ID = 0;
    private final int INDEX_QUOTE_SYMBOL = 1;
    private final int INDEX_QUOTE_BID_PRICE = 2;
    private final int INDEX_QUOTE_CHANGE = 3;
    private final int INDEX_QUOTE_PERCENT_CHANGE = 4;
    private final int INDEX_QUOTE_IS_UP = 5;

    public StockWidgetRemoteViewsFactory(Context context){
        mContext = context;
    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onDataSetChanged() {
        // This method is called by the app hosting the widget (e.g., the launcher)
        // However, our ContentProvider is not exported so it doesn't have access to the
        // data. Therefore we need to clear (and finally restore) the calling identity so
        // that calls use our process and permission
        final long identityToken = Binder.clearCallingIdentity();

        mCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                QUOTE_COLUMNS,
                null,
                null,
                null);

        Binder.restoreCallingIdentity(identityToken);
    }

    @Override
    public void onDestroy() {
        if(mCursor != null){
            mCursor.close();
            mCursor = null;
        }
    }

    @Override
    public int getCount() {
        return mCursor != null ? mCursor.getCount() : 0;
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if(position == AdapterView.INVALID_POSITION || mCursor == null || !mCursor.moveToPosition(position)){
            return null;
        }

        RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.list_item_quote);

        String symbol = mCursor.getString(INDEX_QUOTE_SYMBOL);
        String bidPrice = mCursor.getString(INDEX_QUOTE_BID_PRICE);
        String change = mCursor.getString(INDEX_QUOTE_CHANGE);
        String percentChange =  mCursor.getString(INDEX_QUOTE_PERCENT_CHANGE);
        String totalContentDescription = "";

        remoteViews.setTextViewText(R.id.stock_symbol, symbol);
        totalContentDescription += mContext.getString(R.string.list_item_quote_contentdescription_symbol_template) + " " + symbol + ".";

        remoteViews.setTextViewText(R.id.bid_price, bidPrice);
        totalContentDescription += mContext.getString(R.string.list_item_quote_contentdescription_bidprice_template) + " " + bidPrice + ".";

        totalContentDescription += mContext.getString(R.string.list_item_quote_contentdescription_change_template) + " ";
        if(mCursor.getInt(INDEX_QUOTE_IS_UP) == 1){
            remoteViews.setInt(R.id.change, "setBackgroundResource", R.drawable.percent_change_pill_green);
        }else{
            remoteViews.setInt(R.id.change, "setBackgroundResource", R.drawable.percent_change_pill_red);
        }

        if (Utils.showPercent) {
            remoteViews.setTextViewText(R.id.change,percentChange);
            totalContentDescription += percentChange + ".";
        } else {
            remoteViews.setTextViewText(R.id.change, change);
            totalContentDescription += change + ".";
        }

        final Intent fillInIntent = new Intent();
        fillInIntent.putExtra(TrackStockActivity.INTENT_EXTRA_SYMBOL, symbol);
        remoteViews.setOnClickFillInIntent(R.id.list_item_quote_parent, fillInIntent);
        remoteViews.setContentDescription(R.id.list_item_quote_parent, totalContentDescription);

        return remoteViews;
    }

    @Override
    public RemoteViews getLoadingView() {
        return new RemoteViews(mContext.getPackageName(), R.layout.list_item_quote);
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        if(mCursor.moveToPosition(position)){
            return mCursor.getLong(INDEX_QOUTE_ID);
        }
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
