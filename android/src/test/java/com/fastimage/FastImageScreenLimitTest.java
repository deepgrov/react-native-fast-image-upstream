package com.fastimage;

import static android.widget.ImageView.ScaleType.CENTER;
import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.FIT_CENTER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.res.Resources;
import android.net.Uri;
import android.util.DisplayMetrics;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

// limitToScreen (on by default): the box an image is requested for
// (decodeBox) is at most the screen's longer side long, keeping the view's
// shape, for a view longer than the screen. A screen of 1080 x 2340 pixels at
// 3 pixels per dp, like a Galaxy S23's.
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
public class FastImageScreenLimitTest {
    private static final String URL = "https://example.org/long.png";

    private static FastImageViewWithUrl view(Object model, int width, int height) {
        FastImageViewWithUrl view = new FastImageViewWithUrl(RuntimeEnvironment.getApplication(), null);
        ReflectionHelpers.setField(view, "mModel", model);
        view.setObjectFit(FIT_CENTER);
        view.layout(0, 0, width, height);
        return view;
    }

    private static int screen() {
        DisplayMetrics display = Resources.getSystem().getDisplayMetrics();
        return Math.max(display.widthPixels, display.heightPixels);
    }

    // The box for a view of that size, scaled to the screen's length.
    private static int[] limited(int width, int height) {
        float scale = (float) screen() / Math.max(width, height);
        return new int[]{Math.round(width * scale), Math.round(height * scale)};
    }

    @Test
    public void theScreenIsAPhonesInTheseTests() {
        assertEquals(2340, screen());
    }

    // A long image shown at the screen's width, 8 screens' widths tall: its
    // box is a screen tall (292 or 293 pixels wide), not 1080 x 8640 (37 MB
    // as a bitmap). For contain and cover (hardware cover's box) alike.
    @Test
    public void aViewLongerThanTheScreenGetsABoxTheScreensLength() {
        int[] box = view(URL, 1080, 8640).decodeBox(1080, 8640);
        assertArrayEquals(limited(1080, 8640), box);
        assertEquals(2340, box[1]);
        assertEquals(292.5, box[0], .5);

        FastImageViewWithUrl cover = view(URL, 1080, 8640);
        cover.setObjectFit(CENTER_CROP);
        assertArrayEquals(box, cover.decodeBox(1080, 8640));

        // A wide one, by its width.
        assertArrayEquals(limited(8640, 1080), view(URL, 8640, 1080).decodeBox(8640, 1080));
        assertEquals(2340, view(URL, 8640, 1080).decodeBox(8640, 1080)[0]);
    }

    // A view no longer than the screen (also the whole screen) keeps the box
    // it had.
    @Test
    public void aViewNoLongerThanTheScreenKeepsItsBox() {
        for (int[] size : new int[][]{{1080, 2340}, {2340, 1080}, {1080, 1350}, {288, 360}, {2340, 2340}}) {
            assertArrayEquals(size, view(URL, size[0], size[1]).decodeBox(size[0], size[1]));
        }
    }

    // With maxPixelDensity the box is the smaller of the two: here 2 of 3
    // pixels per dp.
    @Test
    public void withMaxPixelDensityTheBoxIsTheSmallerOfTheTwo() {
        FastImageViewWithUrl screenSized = view(URL, 1080, 2340);
        screenSized.setMaxPixelDensity(2);
        assertArrayEquals(new int[]{720, 1560}, screenSized.decodeBox(1080, 2340));

        FastImageViewWithUrl longer = view(URL, 1080, 8640);
        longer.setMaxPixelDensity(2);
        assertArrayEquals(limited(1080, 8640), longer.decodeBox(1080, 8640));

        // 1.2 screens long: the density's box (720 x 1872) is shorter than the
        // screen.
        FastImageViewWithUrl slightly = view(URL, 1080, 2808);
        slightly.setMaxPixelDensity(2);
        assertArrayEquals(new int[]{720, 1872}, slightly.decodeBox(1080, 2808));
    }

    // Turned off, only maxPixelDensity scales the box: a long view keeps its
    // pixels (where a long image's text or fine lines have to stay sharp, or
    // it's zoomed in on).
    @Test
    public void turnedOffTheBoxIsTheViewsPixels() {
        FastImageViewWithUrl view = view(URL, 1080, 8640);
        view.setLimitToScreen(false);
        assertArrayEquals(new int[]{1080, 8640}, view.decodeBox(1080, 8640));
        view.setMaxPixelDensity(2);
        assertArrayEquals(new int[]{720, 5760}, view.decodeBox(1080, 8640));
        view.setLimitToScreen(true);
        assertArrayEquals(limited(1080, 8640), view.decodeBox(1080, 8640));
    }

    // As Fabric sets and resets the prop (null is its default, true).
    @Test
    public void thePropIsOnByDefault() {
        FastImageViewWithUrl view = view(URL, 1080, 8640);
        FastImageViewManager manager = new FastImageViewManager();
        manager.getDelegate().setProperty(view, "limitToScreen", false);
        assertArrayEquals(new int[]{1080, 8640}, view.decodeBox(1080, 8640));
        manager.getDelegate().setProperty(view, "limitToScreen", null);
        assertArrayEquals(limited(1080, 8640), view.decodeBox(1080, 8640));
    }

    // A bundled image (a resource) keeps its box.
    @Test
    public void aBundledImageIsntLimited() {
        Uri resource = Uri.parse("android.resource://com.example/drawable/long");
        assertArrayEquals(new int[]{1080, 8640}, view(resource, 1080, 8640).decodeBox(1080, 8640));
    }

    // Nor do images that aren't fitted to the view (downsample false,
    // pixelated, objectFit none), and a view without a size on a side (the box
    // Glide gets then isn't the view's shape).
    @Test
    public void imagesThatArentFittedAndViewsWithoutASizeKeepTheirBox() {
        FastImageViewWithUrl full = view(URL, 1080, 8640);
        full.setDownsample(false);
        assertArrayEquals(new int[]{1080, 8640}, full.decodeBox(1080, 8640));
        FastImageViewWithUrl pixelated = view(URL, 1080, 8640);
        pixelated.setImageRendering("pixelated");
        assertArrayEquals(new int[]{1080, 8640}, pixelated.decodeBox(1080, 8640));
        FastImageViewWithUrl none = view(URL, 1080, 8640);
        none.setObjectFit(CENTER);
        assertArrayEquals(new int[]{1080, 8640}, none.decodeBox(1080, 8640));
        FastImageViewWithUrl unsized = view(URL, 1080, 0);
        assertArrayEquals(new int[]{1080, 8640}, unsized.decodeBox(1080, 8640));
    }
}
