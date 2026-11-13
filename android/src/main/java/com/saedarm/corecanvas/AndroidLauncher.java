package com.saedarm.corecanvas;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import java.io.OutputStream;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = true;
        config.useCompass = false;
        config.useGyroscope = false;
        config.useWakelock = true;      // the screen must stay on while you plank
        config.useImmersiveMode = true;
        initialize(new CoreCanvas(this::saveToGallery), config);
    }

    /** Puts a finished painting in Pictures/Core Canvas so it shows up in the Gallery app. */
    private String saveToGallery(com.badlogic.gdx.files.FileHandle png, String name) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Core Canvas");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return null;
        try (OutputStream out = cr.openOutputStream(uri)) {
            if (out == null) return null;
            out.write(png.readBytes());
        } catch (Exception e) {
            cr.delete(uri, null, null);
            return null;
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        return "Gallery › Pictures › Core Canvas";
    }
}
