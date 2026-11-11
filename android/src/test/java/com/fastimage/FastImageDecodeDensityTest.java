package com.fastimage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

// maxPixelDensity: the box an image is requested for (decodeBox) is the
// view's pixels scaled down to that many pixels per dp, on a screen denser
// than it (here 4 pixels per dp). Without the prop, and where it doesn't
// apply, the box is FastImageSourceSize.box's, as before the prop existed.
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "xxxhdpi")
public class FastImageDecodeDensityTest {
    private FastImageViewWithUrl view() {
        return new FastImageViewWithUrl(RuntimeEnvironment.getApplication(), null);
    }

    // As Fabric sets and resets the prop (null is its default, 0), on a view
    // that's laid out: only the box changes, not the view's size.
    @Test
    public void thePropScalesTheBoxAndItsDefaultRestoresIt() {
        FastImageViewWithUrl view = view();
        FastImageViewManager manager = new FastImageViewManager();
        view.layout(0, 0, 1000, 400);
        manager.getDelegate().setProperty(view, "maxPixelDensity", 2.0);
        assertArrayEquals(new int[]{500, 200}, view.decodeBox(1000, 400));
        assertEquals(1000, view.getWidth());
        assertEquals(400, view.getHeight());
        manager.getDelegate().setProperty(view, "maxPixelDensity", null);
        assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
        // At least the screen's density: nothing to scale.
        manager.getDelegate().setProperty(view, "maxPixelDensity", 5.0);
        assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
        manager.getDelegate().setProperty(view, "maxPixelDensity", 4.0);
        assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
    }

    // Values that aren't a density are the default.
    @Test
    public void invalidValuesAreTheDefault() {
        FastImageViewWithUrl view = view();
        for (float invalid : new float[]{0, -2, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            view.setMaxPixelDensity(2);
            assertArrayEquals(new int[]{500, 200}, view.decodeBox(1000, 400));
            view.setMaxPixelDensity(invalid);
            assertArrayEquals(new int[]{1000, 400}, view.decodeBox(1000, 400));
        }
    }

    // A view much longer than the screen (a long illustration at the screen's
    // width): only the density scales its box, whatever its length, so the
    // prop can be used for it too. Its long side isn't brought down to the
    // screen's, which would leave text in it unreadable.
    @Test
    public void aViewLongerThanTheScreenIsOnlyScaledForTheDensity() {
        FastImageViewWithUrl view = view();
        view.setObjectFit(ImageView.ScaleType.FIT_CENTER);
        DisplayMetrics display = Resources.getSystem().getDisplayMetrics();
        int screen = Math.max(display.widthPixels, display.heightPixels);
        int width = screen / 2, height = width * 8;
        view.layout(0, 0, width, height);
        assertArrayEquals(FastImageSourceSize.box(width, height), view.decodeBox(width, height));
        view.setMaxPixelDensity(2);
        int[] box = view.decodeBox(width, height);
        assertArrayEquals(new int[]{width / 2, height / 2}, box);
        assertTrue(box[1] > screen);
        view.layout(0, 0, 1080, 8640);
        assertArrayEquals(new int[]{540, 4320}, view.decodeBox(1080, 8640));
    }

    // Without the prop: FastImageSourceSize.box's box (the view's pixels, at
    // most MAX_PIXELS of them), for a view of any length.
    @Test
    public void withoutThePropTheBoxIsTheViewsPixelsWithinThePixelBudget() {
        FastImageViewWithUrl view = view();
        view.setObjectFit(ImageView.ScaleType.FIT_CENTER);
        view.layout(0, 0, 1080, 8640);
        assertArrayEquals(new int[]{1080, 8640}, view.decodeBox(1080, 8640));
        view.layout(0, 0, 1000, 100000);
        assertArrayEquals(new int[]{409, 40960}, view.decodeBox(1000, 100000));
        assertArrayEquals(FastImageSourceSize.box(1000, 100000), view.decodeBox(1000, 100000));
    }

    // The scaled box is capped like any other.
    @Test
    public void theScaledBoxIsWithinThePixelBudget() {
        FastImageViewWithUrl view = view();
        view.setMaxPixelDensity(2);
        assertArrayEquals(FastImageSourceSize.box(500, 50000), view.decodeBox(1000, 100000));
    }

    // Images that aren't fitted to the view keep their box (downsample false,
    // pixelated, objectFit none), and so do a side that's still open (0) and
    // a box that can't get smaller.
    @Test
    public void imagesThatArentFittedAndOpenSidesKeepTheirBox() {
        FastImageViewWithUrl view = view();
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
        view.setObjectFit(ImageView.ScaleType.CENTER_CROP);
        assertArrayEquals(new int[]{500, 200}, view.decodeBox(1000, 400));
    }
}
