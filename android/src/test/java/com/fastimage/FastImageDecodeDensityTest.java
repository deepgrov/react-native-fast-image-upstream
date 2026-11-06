package com.fastimage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.res.Resources;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "xxxhdpi")
public class FastImageDecodeDensityTest {
    @Test public void fabricResetRestoresPhysicalSizeWithoutChangingLayout() {
        FastImageViewWithUrl view = new FastImageViewWithUrl(RuntimeEnvironment.getApplication(), null);
        FastImageViewManager manager = new FastImageViewManager();
        view.layout(0, 0, 1000, 400);
        manager.getDelegate().setProperty(view, "maxPixelDensity", 2.0);
        assertArrayEquals(new int[]{500, 200}, view.decodeBox(1000, 400));
        assertEquals(1000, view.getWidth());
        assertEquals(400, view.getHeight());
        manager.getDelegate().setProperty(view, "maxPixelDensity", null);
        assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
        manager.getDelegate().setProperty(view, "maxPixelDensity", 5.0);
        assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
    }

    @Test public void longMeasuredImageKeepsItsLayoutAndOriginalModeBypassesTheScreenCap() {
        FastImageViewWithUrl view = new FastImageViewWithUrl(RuntimeEnvironment.getApplication(), null);
        DisplayMetrics display = Resources.getSystem().getDisplayMetrics();
        int screen = Math.max(display.widthPixels, display.heightPixels);
        int width = screen / 2, height = width * 8;
        view.layout(0, 0, width, height);
        // The model chosen for a file by the production load path. No network
        // or asynchronous request is needed to compare its target box.
        ReflectionHelpers.setField(view, "mModel", "file:///scroll-image.png");
        assertArrayEquals(new int[]{Math.round(screen / 8f), screen}, view.decodeBox(width, height));
        assertEquals(height, view.getHeight());
        view.setMaxPixelDensity(2);
        assertArrayEquals(new int[]{Math.round(screen / 8f), screen}, view.decodeBox(width, height));
        view.setDownsample(false);
        assertArrayEquals(FastImageSourceSize.box(width, height), view.decodeBox(width, height));
        view.setDownsample(true);
        view.setMaxPixelDensity(0);
        ReflectionHelpers.setField(view, "mModel", Uri.parse("android.resource://example/drawable/cover"));
        assertArrayEquals(FastImageSourceSize.box(width, height), view.decodeBox(width, height));
    }

    @Test public void originalPixelRequestsAndUnknownSizesKeepTheirContract() {
        FastImageViewWithUrl view = new FastImageViewWithUrl(RuntimeEnvironment.getApplication(), null);
        view.setMaxPixelDensity(2);
        assertArrayEquals(new int[]{1, 1}, view.decodeBox(1, 1));
        assertArrayEquals(new int[]{0, 120}, view.decodeBox(0, 120));
        view.setDownsample(false);
        assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
        view.setDownsample(true);
        view.setImageRendering("pixelated");
        assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
        view.setImageRendering(null);
        view.setObjectFit(ImageView.ScaleType.CENTER);
        assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
    }
}
