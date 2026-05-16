package com.apktool.helper;

import android.app.Application;

public class ApkToolApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // apktool's OSDetection reads os.name at <clinit> time. On some Android
        // devices this property is null, causing NPE. Set it before any apktool
        // class loads.
        if (System.getProperty("os.name") == null) {
            System.setProperty("os.name", "Linux");
        }
    }
}
