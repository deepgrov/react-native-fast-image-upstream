package com.fastimage;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.CENTER_INSIDE;
import static android.widget.ImageView.ScaleType.FIT_CENTER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.widget.ImageView.ScaleType;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.manager.GlideConnectivity;
import com.bumptech.glide.request.RequestOptions;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayDeque;

// maxPixelDensity, in the requests a view makes (on a screen with 4 pixels
// per dp, so half the view's pixels with maxPixelDensity 2): what a request
// has in pixels on screen (a crop's offsets, the blur's radius, an SVG's
// density) is scaled to its box's pixels, and a view that's resized loads
// its image again once, for its new box at the same scale. The views are
// longer than the screen, with limitToScreen off for these. With it (the
// default), the box of a view longer than the screen is the screen's length,
// a scale that depends on the view's size (the last tests).
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "xxxhdpi")
public class FastImageScaleResizeTest {
    // Runs the callbacks the view posts (its reload after a resize) when the
    // test asks, and Glide's requests are paused: the tests look at the
    // requests the view makes, not at what they load.
    private static class LayoutView extends FastImageViewWithUrl {
        final ArrayDeque<Runnable> posts = new ArrayDeque<>();

        LayoutView(Context context, RequestManager manager) {
            super(context, manager);
        }

        @Override
        public boolean post(Runnable action) {
            // The constructor posts before the field is set.
            if (posts == null) return super.post(action);
            return posts.add(action);
        }

        void flush() {
            int count = 0;
            while (!posts.isEmpty()) {
                assertTrue("the view's posted callbacks settle", ++count < 20);
                posts.remove().run();
            }
        }
    }

    @After
    public void tearDown() {
        Glide.tearDown();
        GlideConnectivity.reset();
    }

    @Test
    public void aCropsOffsetIsScaledAndKeptWhenResized() {
        resize(2, CENTER_CROP, 60, 0, false);
    }

    @Test
    public void aBlursRadiusIsScaledAndKeptWhenResized() {
        resize(2, FIT_CENTER, 0, 60, false);
    }

    @Test
    public void anSvgsDensityIsScaledAndKeptWhenResized() {
        resize(2, CENTER_INSIDE, 0, 0, true);
    }

    @Test
    public void aContainedImageIsLoadedForItsScaledBoxWhenResized() {
        resize(2, FIT_CENTER, 0, 0, false);
    }

    @Test
    public void aCenteredCoverImageIsLoadedForItsScaledBoxWhenResized() {
        resize(2, CENTER_CROP, 0, 0, false);
    }

    // Without the prop, the same requests as before it existed: nothing is
    // scaled.
    @Test
    public void withoutThePropNothingIsScaled() {
        resize(0, CENTER_CROP, 60, 0, false);
        tearDown();
        resize(0, FIT_CENTER, 0, 60, false);
        tearDown();
        resize(0, CENTER_INSIDE, 0, 0, true);
    }

    // limitToScreen (on by default): a view longer than the screen gets a box
    // the screen's length. A view that grows to twice its width and length
    // keeps that box, at half the scale: a request with something in pixels
    // on screen (a crop's offsets, a blur's radius, an SVG's density at its
    // own size) is made again for the new scale, once, and other images
    // aren't loaded again.
    @Test
    public void aCropsOffsetIsScaledAgainForTheSameScreenLongBox() {
        resizeLimited(CENTER_CROP, 60, 0, false, 1000, true);
    }

    @Test
    public void aBlursRadiusIsScaledAgainForANewScreenLongBox() {
        resizeLimited(FIT_CENTER, 0, 60, false, 800, true);
    }

    @Test
    public void anSvgsDensityIsScaledAgainForTheSameScreenLongBox() {
        resizeLimited(CENTER_INSIDE, 0, 0, true, 1000, true);
    }

    @Test
    public void aContainedImageKeepsItsScreenLongBox() {
        resizeLimited(FIT_CENTER, 0, 0, false, 1000, false);
    }

    @Test
    public void aCenteredCoverImageKeepsItsScreenLongBox() {
        resizeLimited(CENTER_CROP, 0, 0, false, 1000, false);
    }

    private void resizeLimited(ScaleType fit, int offset, int blur, boolean vector, int newWidth, boolean again) {
        Context context = RuntimeEnvironment.getApplication();
        float density = context.getResources().getDisplayMetrics().density;
        DisplayMetrics display = Resources.getSystem().getDisplayMetrics();
        int screen = Math.max(display.widthPixels, display.heightPixels);
        int height = Math.max(3000, screen * 2);
        RequestManager manager = Glide.with(context);
        manager.pauseRequests();
        LayoutView view = new LayoutView(context, manager);
        view.layout(0, 0, 500, height);
        view.onAfterUpdate(new FastImageViewManager(), manager);
        view.setObjectFit(fit);
        view.setObjectPositionXOffset(offset);
        view.setBlurRadius(blur);

        String model = "file:///resize-limited-" + fit + (vector ? ".svg" : ".png");
        RequestBuilder<Drawable> base = manager.asDrawable().load(model);
        ReflectionHelpers.setField(view, "mModel", model);
        ReflectionHelpers.setField(view, "mBaseRequest", base);
        RequestOptions options = ReflectionHelpers.callInstanceMethod(FastImageViewWithUrl.class, view,
                "renderingOptions", ClassParameter.from(Object.class, model));
        RequestBuilder<Drawable> first = base.clone().apply(options);
        ReflectionHelpers.setField(view, "mLoadingRequest", first);
        int[] box = view.decodeBox(500, height);
        assertArrayEquals(new int[]{Math.round(500 * (float) screen / height), screen}, box);
        ReflectionHelpers.setField(view, "mLoadingWidth", box[0]);
        ReflectionHelpers.setField(view, "mLoadingHeight", box[1]);
        if (vector) FastImageSourceSize.capture(fit, model, true, false).getVectorScaleFactor(80, 80, box[0], box[1]);
        view.onImageLoaded(new ColorDrawable());
        view.flush();
        int loads = ReflectionHelpers.getField(view, "mLoadCount");

        view.layout(0, 0, newWidth, 2 * height);
        view.flush();
        assertEquals(loads + (again ? 1 : 0), (int) ReflectionHelpers.getField(view, "mLoadCount"));
        RequestBuilder<Drawable> current = ReflectionHelpers.getField(view, "mLoadingRequest");
        float scale = (float) screen / (2 * height);
        if (offset > 0) {
            assertEquals(new FastImageCoverCrop(new FastImageCrop(.5f, offset * scale, .5f, 0)),
                    current.getTransformations().get(Bitmap.class));
        }
        if (blur > 0) {
            assertEquals(new MultiTransformation<>(new FitCenter(), new FastImageBlur(blur * scale, fit, 1)),
                    current.getTransformations().get(Bitmap.class));
        }
        if (vector) assertEquals(density * scale, current.getOptions().get(FastImageSvg.DENSITY), .0001f);
        if (again) {
            // Loaded for the new scale: not again.
            view.onImageLoaded(new ColorDrawable());
            view.flush();
            assertEquals(loads + 1, (int) ReflectionHelpers.getField(view, "mLoadCount"));
        } else {
            assertSame(first, current);
        }
        view.clearView(manager);
    }

    private void resize(float maxPixelDensity, ScaleType fit, int offset, int blur, boolean vector) {
        Context context = RuntimeEnvironment.getApplication();
        float density = context.getResources().getDisplayMetrics().density;
        float scale = maxPixelDensity > 0 ? maxPixelDensity / density : 1;
        RequestManager manager = Glide.with(context);
        manager.pauseRequests();
        LayoutView view = new LayoutView(context, manager);
        view.layout(0, 0, 500, 3000);
        view.setMaxPixelDensity(maxPixelDensity);
        view.setLimitToScreen(false);
        view.onAfterUpdate(new FastImageViewManager(), manager);
        view.setObjectFit(fit);
        view.setObjectPositionXOffset(offset);
        view.setBlurRadius(blur);

        // The view's first load, as onAfterUpdate makes it for a source.
        String model = "file:///resize-" + fit + (vector ? ".svg" : ".png");
        RequestBuilder<Drawable> base = manager.asDrawable().load(model);
        ReflectionHelpers.setField(view, "mModel", model);
        ReflectionHelpers.setField(view, "mBaseRequest", base);
        RequestOptions options = ReflectionHelpers.callInstanceMethod(FastImageViewWithUrl.class, view,
                "renderingOptions", ClassParameter.from(Object.class, model));
        RequestBuilder<Drawable> first = base.clone().apply(options);
        if (offset > 0) {
            assertEquals(new FastImageCoverCrop(new FastImageCrop(.5f, offset * scale, .5f, 0)),
                    first.getTransformations().get(Bitmap.class));
        } else if (blur > 0) {
            // (FastImageBlur only keeps a density for fits that show an image
            // at its own size.)
            assertEquals(new MultiTransformation<>(new FitCenter(), new FastImageBlur(blur * scale, fit, 1)),
                    first.getTransformations().get(Bitmap.class));
        }
        if (vector) assertEquals(density * scale, first.getOptions().get(FastImageSvg.DENSITY), .0001f);
        ReflectionHelpers.setField(view, "mLoadingRequest", first);
        int[] box = view.decodeBox(500, 3000);
        assertArrayEquals(new int[]{Math.round(500 * scale), Math.round(3000 * scale)}, box);
        ReflectionHelpers.setField(view, "mLoadingWidth", box[0]);
        ReflectionHelpers.setField(view, "mLoadingHeight", box[1]);
        // An SVG's decode says it's a vector.
        if (vector) FastImageSourceSize.capture(fit, model, true, false).getVectorScaleFactor(80, 80, box[0], box[1]);
        view.onImageLoaded(new ColorDrawable());
        view.flush();
        int loads = ReflectionHelpers.getField(view, "mLoadCount");

        // The view grows: its image is loaded again once, with the request it
        // has (the scale doesn't depend on the view's size), for the new box.
        view.layout(0, 0, 1000, 6000);
        view.flush();
        assertEquals(loads + 1, (int) ReflectionHelpers.getField(view, "mLoadCount"));
        assertSame(first, ReflectionHelpers.getField(view, "mLoadingRequest"));
        assertEquals(Math.round(1000 * scale), (int) ReflectionHelpers.getField(view, "mLoadingWidth"));
        assertEquals(Math.round(6000 * scale), (int) ReflectionHelpers.getField(view, "mLoadingHeight"));

        // Once that's loaded the view shows the image for its box, and
        // doesn't load it again.
        view.onImageLoaded(new ColorDrawable());
        view.flush();
        assertEquals(loads + 1, (int) ReflectionHelpers.getField(view, "mLoadCount"));
        view.clearView(manager);
    }
}
