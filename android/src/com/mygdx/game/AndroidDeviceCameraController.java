package com.mygdx.game;

import android.hardware.Camera;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import androidx.camera.view.PreviewView;

public class AndroidDeviceCameraController implements DeviceCameraControl {

    private static final int ONE_SECOND_IN_MILI = 1000;
    private final AndroidLauncher activity;
    public PreviewView previewView;
    private byte[] pictureData;

    public AndroidDeviceCameraController(AndroidLauncher activity) {
        this.activity = activity;
    }

    @Override
    public synchronized void prepareCamera() {
        //activity.setFixedSize(960,640);
        if (previewView == null) {
            previewView = new PreviewView(activity);
            previewView.setId(R.id.previewview);
            Log.d("ingo", "creating previewView");
        }
        FrameLayout frameLayout = activity.frameLayout;
        previewView.setLayoutParams(new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT ));
        Log.d("ingo", "before " + frameLayout.getChildCount());
        frameLayout.addView(previewView, 0);

        Log.d("ingo", "after " + frameLayout.getChildCount());
        for(int index = 0; index < frameLayout.getChildCount(); index++) {
            View nextChild = frameLayout.getChildAt(index);
            Log.d("ingo", String.valueOf(nextChild.getId()));
        }
        Log.d("ingo", "previewview " + R.id.previewview + ", graphicsview " + R.id.graphicsview);
        //activity.addContentView(previewView, new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );
        //previewView.setVisibility(View.INVISIBLE);
    }

}