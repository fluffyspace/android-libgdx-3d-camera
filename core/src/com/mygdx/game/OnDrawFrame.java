package com.mygdx.game;

import com.badlogic.gdx.math.Matrix4;

public interface OnDrawFrame {
    float[] getLastHeadView();
    float[] getProjectionMatrix();
    float[] getCameraTranslation();
}
