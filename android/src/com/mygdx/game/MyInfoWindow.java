package com.mygdx.game;

import android.widget.TextView;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow;

public class MyInfoWindow extends MarkerInfoWindow {
    private OverlayItem overlayItem;

    public MyInfoWindow(int layoutResId, MapView mapView, OverlayItem item) {
        super(layoutResId, mapView);
        this.overlayItem = item;
    }

    @Override
    public void onOpen(Object item) {
        super.onOpen(item);
        TextView txtTitle = mView.findViewById(R.id.txtTitle);
        TextView txtDescription = mView.findViewById(R.id.txtDescription);

        txtTitle.setText(overlayItem.getTitle());
        txtDescription.setText(overlayItem.getSnippet());
    }
}
