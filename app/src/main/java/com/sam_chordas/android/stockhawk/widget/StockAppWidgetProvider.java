package com.sam_chordas.android.stockhawk.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.TaskStackBuilder;
import android.widget.RemoteViews;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.ui.MyStocksActivity;
import com.sam_chordas.android.stockhawk.ui.TrackStockActivity;

/*
    created by rohit
 */
public class StockAppWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_DATA_UPDATED = "com.sam_chordas.android.stockhawk.widget.action_data_updated";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            Intent launchMyStocksActivityIntent = new Intent(context, MyStocksActivity.class);
            PendingIntent launchMyStocksActivityPendingIntent = PendingIntent.getActivity(context, 0, launchMyStocksActivityIntent, 0);
            remoteViews.setOnClickPendingIntent(R.id.widget_header, launchMyStocksActivityPendingIntent);

            remoteViews.setRemoteAdapter(R.id.widget_listview, new Intent(context, StockWidgetService.class));

            Intent clickStockIntent = new Intent(context, TrackStockActivity.class);
            PendingIntent clickStockTemplate = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(clickStockIntent)
                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setPendingIntentTemplate(R.id.widget_listview, clickStockTemplate);
            remoteViews.setEmptyView(R.id.widget_listview, R.id.widget_empty_textview);

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_DATA_UPDATED.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetManager.getAppWidgetIds(new ComponentName(context, getClass())), R.id.widget_listview);
        }
    }
}
