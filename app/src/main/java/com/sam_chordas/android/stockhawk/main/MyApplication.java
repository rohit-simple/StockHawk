package com.sam_chordas.android.stockhawk.main;

import android.app.Application;

import com.facebook.stetho.Stetho;

/*
    created by rohit
    purpose- for one time initialization of Stetho
 */
public class MyApplication extends Application{
    @Override
    public void onCreate() {
        super.onCreate();
        Stetho.initializeWithDefaults(this);
    }
}
