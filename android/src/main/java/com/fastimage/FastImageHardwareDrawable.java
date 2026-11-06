package com.fastimage;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// A hardware image may later be snapshotted by a sheet/transition into a
// software Canvas. Make one software copy only for that exceptional path;
// ordinary hardware draws have no copy or additional bitmap allocation.
final class FastImageHardwareDrawable extends BitmapDrawable {
    private final Resources resources;
    private BitmapDrawable software;
    private ColorStateList tint;
    private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;

    FastImageHardwareDrawable(Resources resources, BitmapDrawable original) {
        super(resources, original.getBitmap());
        this.resources = resources;
        getPaint().set(original.getPaint());
        setGravity(original.getGravity());
    }

    @Override public void setTintList(@Nullable ColorStateList tint) {
        super.setTintList(tint);
        this.tint = tint;
    }
    @Override public void setTintMode(@NonNull PorterDuff.Mode mode) {
        super.setTintMode(mode);
        tintMode = mode;
    }
    @Override public void draw(@NonNull Canvas canvas) {
        if (canvas.isHardwareAccelerated()) {
            super.draw(canvas);
            return;
        }
        if (software == null) {
            Bitmap copy = getBitmap().copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null) return;
            software = new BitmapDrawable(resources, copy);
        }
        software.getPaint().set(getPaint());
        software.setBounds(getBounds());
        software.setGravity(getGravity());
        software.setTileModeXY(getTileModeX(), getTileModeY());
        software.setTintList(tint);
        software.setTintMode(tintMode);
        software.setState(getState());
        software.setLevel(getLevel());
        software.draw(canvas);
    }
}
