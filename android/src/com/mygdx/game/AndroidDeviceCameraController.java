package com.mygdx.game;

import com.mygdx.game.activities.AndroidLauncher;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

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
        // ARCore now handles the camera background, so this is a no-op.
        // The ARCoreBackgroundRenderer draws the camera feed directly as an OpenGL texture.
        Log.d("ingo", "prepareCamera called - ARCore handles camera background");
    }

}