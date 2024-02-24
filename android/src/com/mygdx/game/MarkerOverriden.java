package com.mygdx.game;

import android.view.MotionEvent;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MarkerOverriden extends Marker {

    public MarkerOverriden(MapView mapView) {
        super(mapView);
    }

    @Override public boolean onLongPress(final MotionEvent event, final MapView mapView) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event, MapView mapView) {
        return false;
    }
}
