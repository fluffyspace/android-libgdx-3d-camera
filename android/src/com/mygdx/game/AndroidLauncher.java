package com.mygdx.game;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.math.Matrix4;

public class AndroidLauncher extends AndroidApplication implements OnDrawFrame {

	private HeadTracker mHeadTracker;
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mHeadTracker = new HeadTracker(this);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useAccelerometer = false;
		config.useCompass = false;
		config.useGyroscope = false;
		initialize(new MyGdxGame(this), config);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mHeadTracker.startTracking();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mHeadTracker.stopTracking();
	}

	@Override
	public float[] getLastHeadView() {
		float[] floats1 = new float[16];
		mHeadTracker.getLastHeadView(floats1, 0);
		return floats1;
	}
}
