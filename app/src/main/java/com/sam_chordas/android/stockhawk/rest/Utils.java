package com.sam_chordas.android.stockhawk.rest;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by sam_chordas on 10/8/15.
 * Modified by rohit
 */
public class Utils {

    private static String LOG_TAG = Utils.class.getSimpleName();

    public static boolean showPercent = true;

    public static ArrayList quoteJsonToContentVals(String JSON, boolean isUpdate) {
        ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
        JSONObject jsonObject;
        JSONArray resultsArray;
        try {
            jsonObject = new JSONObject(JSON);
            if (jsonObject.length() != 0) {
                jsonObject = jsonObject.getJSONObject("query");
                int count = Integer.parseInt(jsonObject.getString("count"));
                if (count == 1) {
                    jsonObject = jsonObject.getJSONObject("results")
                            .getJSONObject("quote");
                    batchOperations.add(buildBatchOperation(jsonObject, isUpdate));
                } else {
                    resultsArray = jsonObject.getJSONObject("results").getJSONArray("quote");

                    if (resultsArray != null && resultsArray.length() != 0) {
                        for (int i = 0; i < resultsArray.length(); i++) {
                            jsonObject = resultsArray.getJSONObject(i);
                            batchOperations.add(buildBatchOperation(jsonObject, isUpdate));
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "String to JSON failed: " + e);
        }
        return batchOperations;
    }

    /*
    history retrieved from server is simplified as json array of json objects having date and close values
     */
    public static String historyResponseToString(String JSON){
        JSONObject jsonObject;
        try{
            jsonObject = new JSONObject(JSON);
            jsonObject = jsonObject.getJSONObject("query");
            int count = Integer.parseInt(jsonObject.getString("count"));
            if(count == 0){
                return null;
            }else{
                JSONArray array = jsonObject.getJSONObject("results")
                        .getJSONArray("quote");
                if(array != null && array.length() != 0){
                    JSONObject result = new JSONObject();
                    JSONArray resultArray = new JSONArray();
                    for(int i = 0; i < array.length(); i++){
                        jsonObject = array.getJSONObject(i);
                        String date = jsonObject.getString("Date");
                        String close = jsonObject.getString("Close");
                        if(!TextUtils.isEmpty(date) && !TextUtils.isEmpty(close)){
                            JSONObject oneDayResult = new JSONObject();
                            oneDayResult.put("date", date);
                            oneDayResult.put("close", close);
                            resultArray.put(oneDayResult);
                        }
                    }
                    result.put("history_array", resultArray);
                    return result.toString();
                }
            }
        }catch(JSONException e){
            Log.e(LOG_TAG, "String to JSON failed: " + e);
        }
        return null;
    }

    public static String truncateBidPrice(String bidPrice) {
        bidPrice = String.format(Locale.US, "%.2f", Float.parseFloat(bidPrice));    //important so that database is independent of locale
        return bidPrice;
    }

    public static String truncateChange(String change, boolean isPercentChange) {
        String weight = change.substring(0, 1);
        String ampersand = "";
        if (isPercentChange) {
            ampersand = change.substring(change.length() - 1, change.length());
            change = change.substring(0, change.length() - 1);
        }
        change = change.substring(1, change.length());
        double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
        change = String.format(Locale.US, "%.2f", round);   //important so that database is independent of locale
        StringBuilder changeBuilder = new StringBuilder(change);
        changeBuilder.insert(0, weight);
        changeBuilder.append(ampersand);
        change = changeBuilder.toString();
        return change;
    }

    public static ContentProviderOperation buildBatchOperation(JSONObject jsonObject, boolean isUpdate) {
        ContentProviderOperation.Builder builder;

        if(isUpdate){
            builder = ContentProviderOperation.newUpdate(QuoteProvider.Quotes.CONTENT_URI);
            try{
                builder.withSelection(QuoteColumns.SYMBOL + "=?", new String[]{jsonObject.getString("symbol")});
            }catch(JSONException e){
                e.printStackTrace();
                return null;
            }
        }else{
            builder = ContentProviderOperation.newInsert(QuoteProvider.Quotes.CONTENT_URI);
            try{
                builder.withValue(QuoteColumns.SYMBOL, jsonObject.getString("symbol"));
            }catch(JSONException e){
                e.printStackTrace();
            }
        }

        try {
            String change = jsonObject.getString("Change");

            builder.withValue(QuoteColumns.BID_PRICE, truncateBidPrice(jsonObject.getString("Bid")));
            builder.withValue(QuoteColumns.PERCENT_CHANGE, truncateChange(
                    jsonObject.getString("ChangeinPercent"), true));
            builder.withValue(QuoteColumns.CHANGE, truncateChange(change, false));
            if (change.charAt(0) == '-') {
                builder.withValue(QuoteColumns.IS_UP, 0);
            } else {
                builder.withValue(QuoteColumns.IS_UP, 1);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(!isUpdate){
            builder.withValue(QuoteColumns.IS_HISTORY_LATEST, 0);
            builder.withValue(QuoteColumns.HISTORY, null);
        }
        return builder.build();
    }

    public static boolean isStockValid(String JSON) {
        try {
            JSONObject jsonObject = new JSONObject(JSON);
            if (jsonObject.length() != 0) {
                jsonObject = jsonObject.getJSONObject("query");
                int count = Integer.parseInt(jsonObject.getString("count"));
                if (count == 1) {
                    jsonObject = jsonObject.getJSONObject("results")
                            .getJSONObject("quote");
                    if (jsonObject.has("Bid")) {
                        String bid = jsonObject.getString("Bid");
                        return (!TextUtils.isEmpty(bid) && !bid.equals("null"));
                    }
                } else {
                    return false;
                }
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "String to JSON failed: " + e);
            return false;
        }
        return false;
    }

    public static boolean isInternetConnected(Context context){
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return (activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting());
    }
}
