package com.fastimage;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.CENTER_INSIDE;
import static android.widget.ImageView.ScaleType.FIT_CENTER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.SampleSizeRounding;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.request.RequestOptions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

// A centered cover image as a hardware bitmap (FastImageHardware): when it's
// allowed, the size it's decoded at, the options Glide gets for it, and the
// drawable the view shows it with (FastImageHardwareDrawable), which also
// draws on a canvas that isn't hardware accelerated. The tests don't decode
// or draw an image on a GPU, which the JVM doesn't have: they check what the
// view asks Glide for and what it draws with.
@RunWith(RobolectricTestRunner.class)
public class FastImageHardwareTest {
    private final Context context = RuntimeEnvironment.getApplication();

    // A view in a hardware accelerated window (Robolectric's views aren't in
    // a window, so they say they aren't).
    private View acceleratedView() {
        return new View(context) {
            @Override
            public boolean isHardwareAccelerated() {
                return true;
            }
        };
    }

    private FastImageViewWithUrl acceleratedImageView() {
        return new FastImageViewWithUrl(context, null) {
            @Override
            public boolean isHardwareAccelerated() {
                return true;
            }
        };
    }

    @Test
    public void aCenteredCoverImageInAnAcceleratedViewIsAllowed() {
        assertTrue(FastImageHardware.allowed(acceleratedView(), CENTER_CROP, null, null, true));
    }

    // Glide draws these into a bitmap of its own (a crop at a position, a
    // blur, a fit), or they're decoded at full size: a hardware bitmap can't
    // be drawn from on Glide's threads, and has nothing to save there.
    @Test
    public void otherImagesArentAllowed() {
        View view = acceleratedView();
        assertFalse(FastImageHardware.allowed(view, FIT_CENTER, null, null, true));
        assertFalse(FastImageHardware.allowed(view, CENTER_INSIDE, null, null, true));
        assertFalse(FastImageHardware.allowed(view, null, null, null, true));
        assertFalse(FastImageHardware.allowed(view, CENTER_CROP, new FastImageCrop(0, 0, 0, 0), null, true));
        assertFalse(FastImageHardware.allowed(view, CENTER_CROP, null, new FastImageBlur(4, CENTER_CROP, 1), true));
        assertFalse(FastImageHardware.allowed(view, CENTER_CROP, null, null, false));
    }

    // A view that isn't drawn with hardware acceleration (also one that
    // isn't in a window yet) can't draw a hardware bitmap.
    @Test
    public void aViewThatIsntAcceleratedIsntAllowed() {
        assertFalse(FastImageHardware.allowed(new View(context), CENTER_CROP, null, null, true));
    }

    // Hardware bitmaps are from API 26, and Glide only decodes them from 28.
    @Test
    @Config(sdk = 27)
    public void beforeApi28ItIsntAllowed() {
        assertFalse(FastImageHardware.allowed(acceleratedView(), CENTER_CROP, null, null, true));
    }

    // A software layer on the view or on any view above it draws the view
    // on a canvas that isn't hardware accelerated.
    @Test
    public void aSoftwareLayerOnTheViewOrAnAncestorIsntAllowed() {
        View view = acceleratedView();
        FrameLayout parent = new FrameLayout(context);
        FrameLayout grandparent = new FrameLayout(context);
        parent.addView(view);
        grandparent.addView(parent);
        assertTrue(FastImageHardware.allowed(view, CENTER_CROP, null, null, true));

        grandparent.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        assertFalse(FastImageHardware.allowed(view, CENTER_CROP, null, null, true));
        // A hardware layer draws with the GPU.
        grandparent.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        assertTrue(FastImageHardware.allowed(view, CENTER_CROP, null, null, true));

        parent.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        assertFalse(FastImageHardware.allowed(view, CENTER_CROP, null, null, true));
        parent.setLayerType(View.LAYER_TYPE_NONE, null);
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        assertFalse(FastImageHardware.allowed(view, CENTER_CROP, null, null, true));
    }

    // An image no larger than MAX_PIXELS once it covers the box is decoded
    // as Glide's CENTER_OUTSIDE decodes one for CenterCrop.
    @Test
    public void anImageWithinTheLimitIsDecodedToCoverTheBox() {
        DownsampleStrategy cover = FastImageHardware.COVER;
        for (int[] c : new int[][]{
                // source width and height, box width and height
                {2000, 1000, 500, 500},
                {1000, 2000, 500, 500},
                {4000, 3000, 1080, 608},
                {1200, 4800, 1080, 2340},
                {400, 500, 400, 500},
        }) {
            float expected = DownsampleStrategy.CENTER_OUTSIDE.getScaleFactor(c[0], c[1], c[2], c[3]);
            assertTrue(expected <= 1);
            assertEquals(expected, cover.getScaleFactor(c[0], c[1], c[2], c[3]), 0);
        }
        assertEquals(SampleSizeRounding.QUALITY, cover.getSampleSizeRounding(2000, 1000, 500, 500));
    }

    // An image that would have to be scaled up to cover the box is decoded
    // at its own size, as FastImageSourceSize.COVER decodes one without
    // hardware cover: the view scales it up as it draws it.
    @Test
    public void anImageSmallerThanTheBoxIsDecodedAtItsOwnSize() {
        for (int[] c : new int[][]{
                // source width and height, box width and height
                {100, 50, 300, 300},
                // A thumbnail behind a full-screen view.
                {400, 500, 1080, 2340},
                // Covers it by one side only.
                {200, 250, 288, 360},
                {400, 500, 480, 600},
                {20, 20, 4095, 4095},
                {1080, 500, 1080, 2340},
        }) {
            String name = c[0] + "x" + c[1] + " for " + c[2] + "x" + c[3];
            assertTrue(name, DownsampleStrategy.CENTER_OUTSIDE.getScaleFactor(c[0], c[1], c[2], c[3]) > 1);
            assertEquals(name, 1, FastImageHardware.COVER.getScaleFactor(c[0], c[1], c[2], c[3]), 0);
            assertEquals(name, 1, FastImageSourceSize.COVER.getScaleFactor(c[0], c[1], c[2], c[3]), 0);
        }
    }

    // Without a crop the whole image is decoded, so one much longer than its
    // box would be far larger than the box: it's at most MAX_PIXELS (which
    // the decoder rounds each side up within), and close to it.
    @Test
    public void anImageMuchLongerThanItsBoxIsDecodedWithinTheLimit() {
        DownsampleStrategy cover = FastImageHardware.COVER;
        for (int[] c : new int[][]{
                // 1000 × 100000 in a square box: 100 megapixels to cover it.
                {1000, 100000, 1000, 1000},
                {100000, 1000, 1000, 1000},
                // A long illustration behind a full-screen view: 1080 × 8640
                // to cover it, over the limit.
                {1200, 9600, 1080, 2340},
                {1080, 86400, 1080, 2340},
                {1, 100000000, 1080, 1080},
                {100000000, 1, 1080, 1080},
                {Integer.MAX_VALUE, Integer.MAX_VALUE, 1080, 1080},
                {30000, 30000, 30000, 30000},
        }) {
            float scale = cover.getScaleFactor(c[0], c[1], c[2], c[3]);
            String image = c[0] + "x" + c[1];
            assertTrue(image, scale > 0);
            assertTrue(image, scale <= DownsampleStrategy.CENTER_OUTSIDE.getScaleFactor(c[0], c[1], c[2], c[3]));
            double pixels = Math.ceil(c[0] * (double) scale) * Math.ceil(c[1] * (double) scale);
            assertTrue(image + " is " + pixels + " pixels", pixels <= FastImageHardware.MAX_PIXELS);
        }
        // Not smaller than it needs to be: with a row and a column more
        // (for the rounding), about the limit.
        float scale = cover.getScaleFactor(1000, 100000, 1000, 1000);
        double width = 1000 * (double) scale, height = 100000 * (double) scale;
        assertTrue((width + 1) * (height + 1) >= FastImageHardware.MAX_PIXELS * .999);
    }

    // What Glide gets for it: no transformation (with one, Glide turns
    // hardware bitmaps off again) and hardware bitmaps allowed, decoded with
    // COVER. Otherwise CenterCrop, as before, without the option.
    @Test
    public void theRequestHasNoTransformationAndAllowsHardwareBitmaps() {
        String model = "https://example.org/cover.jpg";
        RequestOptions hardware = FastImageSourceSize.scaleTypeOptions(CENTER_CROP,
                FastImageSourceSize.capture(CENTER_CROP, model, true, true), null, null, true, true);
        assertTrue(hardware.getOptions().get(Downsampler.ALLOW_HARDWARE_CONFIG));
        assertNull(hardware.getTransformations().get(Bitmap.class));
        assertFalse(hardware.isTransformationRequired());
        assertEquals(FastImageHardware.COVER.getScaleFactor(1000, 100000, 1000, 1000),
                hardware.getOptions().get(DownsampleStrategy.OPTION).getScaleFactor(1000, 100000, 1000, 1000), 0);

        RequestOptions software = FastImageSourceSize.scaleTypeOptions(CENTER_CROP,
                FastImageSourceSize.capture(CENTER_CROP, model, true, false), null, null, true, false);
        assertFalse(software.getOptions().get(Downsampler.ALLOW_HARDWARE_CONFIG));
        assertEquals(new FastImageCoverCrop(null), software.getTransformations().get(Bitmap.class));
        assertEquals(1f, software.getOptions().get(DownsampleStrategy.OPTION).getScaleFactor(1000, 100000, 1000, 1000),
                0);
        // Two requests for one image at one size, one of each kind, are
        // different images to Glide's memory cache.
        assertNotSame(hardware.getOptions().get(DownsampleStrategy.OPTION),
                software.getOptions().get(DownsampleStrategy.OPTION));
        assertFalse(hardware.getOptions().equals(software.getOptions()));
    }

    // The view's own request, from its props.
    @Test
    public void theViewAsksForAHardwareBitmapOnlyForACenteredCoverImage() {
        FastImageViewWithUrl view = acceleratedImageView();
        view.layout(0, 0, 400, 300);
        // The view's own default is contain (the component's is cover).
        assertFalse(allowsHardware(view));
        view.setObjectFit(CENTER_CROP);
        assertTrue(allowsHardware(view));
        view.setObjectPositionXOffset(10);
        assertFalse(allowsHardware(view));
        view.setObjectPositionXOffset(0);
        view.setObjectPositionX(0);
        assertFalse(allowsHardware(view));
        view.setObjectPositionX(.5f);
        view.setBlurRadius(4);
        assertFalse(allowsHardware(view));
        view.setBlurRadius(0);
        view.setObjectFit(FIT_CENTER);
        assertFalse(allowsHardware(view));
        view.setObjectFit(CENTER_CROP);
        view.setDownsample(false);
        assertFalse(allowsHardware(view));
        view.setDownsample(true);
        view.setImageRendering("pixelated");
        assertFalse(allowsHardware(view));
        view.setImageRendering(null);
        assertTrue(allowsHardware(view));

        FastImageViewWithUrl detached = new FastImageViewWithUrl(context, null);
        detached.layout(0, 0, 400, 300);
        detached.setObjectFit(CENTER_CROP);
        assertFalse(allowsHardware(detached));
    }

    @Test
    @Config(sdk = 27)
    public void theViewDoesntAskForAHardwareBitmapBeforeApi28() {
        FastImageViewWithUrl view = acceleratedImageView();
        view.layout(0, 0, 400, 300);
        view.setObjectFit(CENTER_CROP);
        assertFalse(allowsHardware(view));
    }

    private static boolean allowsHardware(FastImageViewWithUrl view) {
        RequestOptions options = ReflectionHelpers.callInstanceMethod(FastImageViewWithUrl.class, view,
                "renderingOptions", ClassParameter.from(Object.class, "https://example.org/cover.jpg"));
        return options.getOptions().get(Downsampler.ALLOW_HARDWARE_CONFIG);
    }

    private static Bitmap hardwareBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);
        Bitmap hardware = bitmap.copy(Bitmap.Config.HARDWARE, false);
        assertNotNull(hardware);
        assertEquals(Bitmap.Config.HARDWARE, hardware.getConfig());
        return hardware;
    }

    private static BitmapDrawable softwareCopy(FastImageHardwareDrawable drawable) {
        return ReflectionHelpers.getField(drawable, "software");
    }

    // The view wraps a hardware bitmap's drawable, and no other.
    @Test
    public void theViewShowsAHardwareBitmapWithItsOwnDrawable() {
        FastImageViewWithUrl view = new FastImageViewWithUrl(context, null);
        Bitmap hardware = hardwareBitmap();
        view.setImageDrawable(new BitmapDrawable(context.getResources(), hardware));
        assertTrue(view.getDrawable() instanceof FastImageHardwareDrawable);
        assertSame(hardware, ((BitmapDrawable) view.getDrawable()).getBitmap());
        // Set again (e.g. the view's own drawable back after a fade): not
        // wrapped twice.
        FastImageHardwareDrawable wrapped = (FastImageHardwareDrawable) view.getDrawable();
        view.setImageDrawable(wrapped);
        assertSame(wrapped, view.getDrawable());

        BitmapDrawable software = new BitmapDrawable(context.getResources(),
                Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888));
        view.setImageDrawable(software);
        assertSame(software, view.getDrawable());
        ColorDrawable color = new ColorDrawable(Color.RED);
        view.setImageDrawable(color);
        assertSame(color, view.getDrawable());
        view.setImageDrawable(null);
        assertNull(view.getDrawable());
    }

    @Test
    @Config(sdk = 27)
    public void beforeApi28TheViewShowsABitmapsDrawableAsItIs() {
        FastImageViewWithUrl view = new FastImageViewWithUrl(context, null);
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(),
                Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888));
        view.setImageDrawable(drawable);
        assertSame(drawable, view.getDrawable());
    }

    // On a hardware accelerated canvas (how a view in a window is drawn) the
    // hardware bitmap is drawn as it is: no copy is made.
    @Test
    public void anAcceleratedCanvasDrawsTheHardwareBitmapWithoutACopy() {
        FastImageHardwareDrawable drawable = new FastImageHardwareDrawable(context.getResources(),
                new BitmapDrawable(context.getResources(), hardwareBitmap()));
        drawable.setBounds(0, 0, 4, 2);
        drawable.draw(new Canvas() {
            @Override
            public boolean isHardwareAccelerated() {
                return true;
            }
        });
        assertNull(softwareCopy(drawable));
    }

    // A canvas that isn't (a snapshot of the view into a bitmap, as a sheet
    // or a transition takes) can't draw a hardware bitmap: Android throws.
    // The drawable draws a software copy there, made once and drawn as the
    // drawable is set up at that time.
    @Test
    public void aSoftwareCanvasDrawsOneSoftwareCopyAsTheDrawableIsSetUp() {
        Bitmap hardware = hardwareBitmap();
        BitmapDrawable original = new BitmapDrawable(context.getResources(), hardware);
        original.setGravity(Gravity.CENTER);
        FastImageHardwareDrawable drawable = new FastImageHardwareDrawable(context.getResources(), original);
        assertEquals(Gravity.CENTER, drawable.getGravity());
        drawable.setBounds(1, 2, 31, 42);
        drawable.setLevel(3);
        Canvas canvas = new Canvas(Bitmap.createBitmap(40, 50, Bitmap.Config.ARGB_8888));
        assertFalse(canvas.isHardwareAccelerated());

        drawable.draw(canvas);
        BitmapDrawable copy = softwareCopy(drawable);
        assertNotNull(copy);
        assertNotSame(hardware, copy.getBitmap());
        assertEquals(Bitmap.Config.ARGB_8888, copy.getBitmap().getConfig());
        assertFalse(copy.getBitmap().isMutable());
        assertEquals(hardware.getWidth(), copy.getBitmap().getWidth());
        assertEquals(hardware.getHeight(), copy.getBitmap().getHeight());
        assertEquals(new Rect(1, 2, 31, 42), copy.getBounds());
        assertEquals(Gravity.CENTER, copy.getGravity());
        assertEquals(3, copy.getLevel());
        // The drawable still has the hardware bitmap for accelerated draws.
        assertSame(hardware, drawable.getBitmap());

        // Drawn again after a change: the same copy, set up again.
        drawable.setBounds(0, 0, 8, 4);
        drawable.setGravity(Gravity.FILL);
        drawable.draw(canvas);
        assertSame(copy, softwareCopy(drawable));
        assertEquals(new Rect(0, 0, 8, 4), copy.getBounds());
        assertEquals(Gravity.FILL, copy.getGravity());
    }
}
