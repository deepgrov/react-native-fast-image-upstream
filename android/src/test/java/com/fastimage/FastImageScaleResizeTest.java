package com.fastimage;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.CENTER_INSIDE;
import static android.widget.ImageView.ScaleType.FIT_CENTER;
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
import java.util.ArrayDeque;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
public class FastImageScaleResizeTest {
    // Deliver the view's posted layout callbacks deterministically. Pause
    // Glide's byte loading; these tests inspect the actual requests it gets.
    private static class LayoutView extends FastImageViewWithUrl {
        final ArrayDeque<Runnable> posts = new ArrayDeque<>();
        LayoutView(Context context, RequestManager manager) { super(context, manager); }
        @Override public boolean post(Runnable action) {
            if (posts == null) return super.post(action);
            return posts.add(action);
        }
        void flush() {
            int count = 0;
            while (!posts.isEmpty()) {
                assertTrue("layout callbacks settle", ++count < 20);
                posts.remove().run();
            }
        }
    }

    @After public void tearDown() { Glide.tearDown(); GlideConnectivity.reset(); }

    @Test public void cappedOffsetRebuildsForTheSameBox() { resize(CENTER_CROP, 60, 0, false, 1000, true); }
    @Test public void cappedBlurRebuildsForAChangedBox() { resize(FIT_CENTER, 0, 60, false, 800, true); }
    @Test public void ownSizeSvgRebuildsForTheSameBox() { resize(CENTER_INSIDE, 0, 0, true, 1000, true); }
    @Test public void plainContainReusesItsCappedBox() { resize(FIT_CENTER, 0, 0, false, 1000, false); }
    @Test public void centeredCoverReusesItsCappedBox() { resize(CENTER_CROP, 0, 0, false, 1000, false); }

    private void resize(ScaleType fit, int offset, int blur, boolean vector, int newWidth, boolean rebuild) {
        Context context = RuntimeEnvironment.getApplication();
        RequestManager manager = Glide.with(context);
        manager.pauseRequests();
        LayoutView view = new LayoutView(context, manager);
        DisplayMetrics display = Resources.getSystem().getDisplayMetrics();
        int screen = Math.max(display.widthPixels, display.heightPixels);
        // Use heights above the device's actual screen length for both layouts.
        int height = Math.max(3000, screen * 2);
        view.layout(0, 0, 500, height);
        view.setMaxPixelDensity(2);
        view.onAfterUpdate(new FastImageViewManager(), manager);
        view.setObjectFit(fit);
        view.setObjectPositionXOffset(offset);
        view.setBlurRadius(blur);
        String model = "file:///resize-" + fit + (vector ? ".svg" : ".png");
        RequestBuilder<Drawable> base = manager.asDrawable().load(model);
        ReflectionHelpers.setField(view, "mModel", model);
        ReflectionHelpers.setField(view, "mBaseRequest", base);
        RequestOptions options = ReflectionHelpers.callInstanceMethod(FastImageViewWithUrl.class, view,
                "renderingOptions", ClassParameter.from(Object.class, model));
        RequestBuilder<Drawable> first = base.clone().apply(options);
        ReflectionHelpers.setField(view, "mLoadingRequest", first);
        int[] box = view.decodeBox(500, height);
        ReflectionHelpers.setField(view, "mLoadingWidth", box[0]);
        ReflectionHelpers.setField(view, "mLoadingHeight", box[1]);
        if (vector) FastImageSourceSize.capture(fit, model, true, false).getVectorScaleFactor(80, 80, box[0], box[1]);
        view.onImageLoaded(new ColorDrawable());
        view.flush();
        int generation = ReflectionHelpers.getField(view, "mLoadCount");
        view.layout(0, 0, newWidth, 2 * height);
        view.flush();
        assertEquals(generation + (rebuild ? 1 : 0), (int) ReflectionHelpers.getField(view, "mLoadCount"));
        RequestBuilder<Drawable> current = ReflectionHelpers.getField(view, "mLoadingRequest");
        float scale = (float) screen / (2 * height);
        if (offset > 0) assertEquals(new FastImageCrop(.5f, offset * scale, .5f, 0), current.getTransformations().get(Bitmap.class));
        if (blur > 0) assertEquals(new MultiTransformation<>(new FitCenter(), new FastImageBlur(blur * scale, fit, 1)),
                current.getTransformations().get(Bitmap.class));
        if (vector) assertEquals(context.getResources().getDisplayMetrics().density * scale,
                current.getOptions().get(FastImageSvg.DENSITY), .0001f);
        if (rebuild) {
            view.onImageLoaded(new ColorDrawable());
            view.flush();
            assertEquals(generation + 1, (int) ReflectionHelpers.getField(view, "mLoadCount"));
            // A pending resize of the old image must not replace a source
            // removal from a newer props transaction.
            view.layout(0, 0, 2 * newWidth, 4 * height);
            view.setSource(null);
            view.flush();
            assertEquals(generation + 1, (int) ReflectionHelpers.getField(view, "mLoadCount"));
        } else assertSame(first, current);
        view.clearView(manager);
    }
}
