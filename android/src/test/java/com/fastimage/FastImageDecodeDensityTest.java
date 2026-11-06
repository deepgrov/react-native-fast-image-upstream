package com.fastimage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

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
