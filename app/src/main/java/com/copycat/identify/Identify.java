package com.copycat.identify;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class Identify {
    public native boolean Init(AssetManager mgr);

    public native float[] Detect(Bitmap bitmap, boolean use_gpu);

    static {
        System.loadLibrary("identify");
    }
}
