package com.fastimage;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.MATRIX;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import com.bumptech.glide.manager.GlideConnectivity;
import com.bumptech.glide.request.RequestOptions;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

// Where the view draws a hardware cover request's bitmap (FastImageHardware).
// Glide doesn't crop that bitmap, so the view crops it as it draws it: at the
// place Glide's CenterCrop (TransformationUtils.centerCrop) would have cropped
// it, so the view shows the same pixels as without hardware cover. ImageView's
// own CENTER_CROP rounds the offset another way, a pixel off for half of the
// sizes.
// The pixels are compared with what Glide's own centerCrop gives, drawn with
// Robolectric's native graphics.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FastImageHardwarePositionTest {
    private static final String MODEL = "https://example.org/cover.jpg";

    private final Context context = RuntimeEnvironment.getApplication();

    @After
    public void tearDown() {
        Glide.tearDown();
        GlideConnectivity.reset();
    }

    // A centered cover image's request, as the view makes it
    // (FastImageViewWithUrl.renderingOptions), with hardware cover or without.
    private static RequestOptions cover(boolean hardware) {
        return FastImageSourceSize.scaleTypeOptions(CENTER_CROP,
                FastImageSourceSize.capture(CENTER_CROP, MODEL, true, hardware), null, null, true, hardware)
                .set(FastImageGlide.REQUEST, true);
    }

    // A bitmap whose pixels are all different (up to 256 x 256 of them, then
    // by the blue channel), so a crop a pixel off isn't the same picture.
    private static Bitmap pattern(int width, int height) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = Color.rgb(x & 0xff, y & 0xff, ((x >> 8) << 4) | (y >> 8));
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    // A cover view of a size whose shown image is the request's.
    private FastImageViewWithUrl view(int width, int height, boolean hardware) {
        RequestManager manager = Glide.with(context);
        FastImageViewWithUrl view = new FastImageViewWithUrl(context, manager);
        view.setObjectFit(CENTER_CROP);
        view.layout(0, 0, width, height);
        RequestBuilder<Drawable> request = manager.asDrawable().load(MODEL).apply(cover(hardware));
        ReflectionHelpers.setField(view, "mShownRequest", request);
        return view;
    }

    private Drawable drawable(Bitmap bitmap) {
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    // The image's offset in the view (the matrix's translation) and its scale.
    private static float[] placement(FastImageViewWithUrl view) {
        float[] values = new float[9];
        view.getImageMatrix().getValues(values);
        assertEquals(values[0], values[4], 0);
        return new float[]{values[2], values[5], values[0]};
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return pixels;
    }

    // What the view draws, and what Glide's CenterCrop makes of the bitmap for
    // the view's size (what the view shows without hardware cover): the same
    // pixels.
    private void assertDrawnAsGlideCrops(int bitmapWidth, int bitmapHeight, int width, int height,
            float offsetX, float offsetY) {
        String name = bitmapWidth + "x" + bitmapHeight + " in " + width + "x" + height;
        Bitmap bitmap = pattern(bitmapWidth, bitmapHeight);
        FastImageViewWithUrl view = view(width, height, true);
        view.setImageDrawable(drawable(bitmap));
        assertEquals(name, MATRIX, view.getScaleType());
        float[] placement = placement(view);
        assertEquals(name, offsetX, placement[0], 0);
        assertEquals(name, offsetY, placement[1], 0);

        Bitmap cropped = TransformationUtils.centerCrop(Glide.get(context).getBitmapPool(), bitmap, width, height);
        assertEquals(name, width, cropped.getWidth());
        assertEquals(name, height, cropped.getHeight());
        Bitmap drawn = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(drawn));
        assertArrayEquals(name, pixels(cropped), pixels(drawn));
    }

    // The image is wider than the view by an even number of pixels: Glide's
    // offset is a pixel short of half of it ((int) (-149 + 0.5f) is -148),
    // where ImageView's CENTER_CROP has -149.
    @Test
    public void aBitmapWiderByAnEvenNumberOfPixelsIsDrawnAsGlideCropsIt() {
        assertDrawnAsGlideCrops(403, 252, 105, 252, -148, 0);
        assertDrawnAsGlideCrops(300, 100, 100, 100, -99, 0);
        assertDrawnAsGlideCrops(102, 50, 100, 50, 0, 0);
    }

    // By an odd number: half of it is a half pixel, which both round the
    // same way.
    @Test
    public void aBitmapWiderByAnOddNumberOfPixelsIsDrawnAsGlideCropsIt() {
        assertDrawnAsGlideCrops(404, 252, 105, 252, -149, 0);
        assertDrawnAsGlideCrops(301, 100, 100, 100, -100, 0);
        assertDrawnAsGlideCrops(101, 50, 100, 50, 0, 0);
    }

    // The same for an image taller than the view.
    @Test
    public void aTallerBitmapIsDrawnAsGlideCropsIt() {
        assertDrawnAsGlideCrops(630, 394, 630, 168, 0, -112);
        assertDrawnAsGlideCrops(630, 393, 630, 168, 0, -112);
        assertDrawnAsGlideCrops(100, 300, 100, 100, 0, -99);
        assertDrawnAsGlideCrops(100, 301, 100, 100, 0, -100);
    }

    // The view's size, or the image's shape: nothing is cropped, and there's
    // no offset.
    @Test
    public void aBitmapOfTheViewsShapeHasNoOffset() {
        assertDrawnAsGlideCrops(100, 50, 100, 50, 0, 0);
        FastImageViewWithUrl view = view(200, 100, true);
        view.setImageDrawable(drawable(pattern(100, 50)));
        assertArrayEquals(new float[]{0, 0, 2}, placement(view), 0);
    }

    // A bitmap that isn't the size that covers the view (the view's size
    // changed, or maxPixelDensity has it decoded smaller) is scaled to cover
    // it, with the offset rounded the same way.
    @Test
    public void aBitmapOfAnotherScaleIsPlacedTheSameWay() {
        // 150x60 scaled by 5/6 to 125x50: 12.5 pixels over on each side.
        FastImageViewWithUrl view = view(100, 50, true);
        view.setImageDrawable(drawable(pattern(150, 60)));
        float[] placement = placement(view);
        assertEquals(50 / 60f, placement[2], 0);
        assertEquals((int) ((100 - 150 * (50 / 60f)) * 0.5f + 0.5f), placement[0], 0);
        assertEquals(-12, placement[0], 0);
        assertEquals(0, placement[1], 0);

        // 720x450 scaled by 1.5 to 1080x675 in a 1080x608 view: 33.5 pixels
        // over at the top and the bottom.
        view = view(1080, 608, true);
        view.setImageDrawable(drawable(pattern(720, 450)));
        assertArrayEquals(new float[]{0, -33, 1.5f}, placement(view), 0);
        // 720x452: 35 pixels over, a whole number.
        view.setImageDrawable(drawable(pattern(720, 452)));
        assertArrayEquals(new float[]{0, -34, 1.5f}, placement(view), 0);
    }

    // A right-to-left layout doesn't mirror an image: the same place.
    @Test
    public void aRightToLeftLayoutHasTheSameOffset() {
        Bitmap bitmap = pattern(403, 252);
        FastImageViewWithUrl view = view(105, 252, true);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        view.setImageDrawable(drawable(bitmap));
        assertArrayEquals(new float[]{-148, 0, 1}, placement(view), 0);
        Bitmap drawn = Bitmap.createBitmap(105, 252, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(drawn));
        assertArrayEquals(pixels(TransformationUtils.centerCrop(
                Glide.get(context).getBitmapPool(), bitmap, 105, 252)), pixels(drawn));
    }

    // The view's size changes (its layout, before the image loads again for
    // it): the image is placed for the new size.
    @Test
    public void aResizedViewPlacesTheBitmapAgain() {
        FastImageViewWithUrl view = view(105, 252, true);
        view.setImageDrawable(drawable(pattern(403, 252)));
        assertArrayEquals(new float[]{-148, 0, 1}, placement(view), 0);
        view.layout(0, 0, 103, 252);
        assertArrayEquals(new float[]{-149, 0, 1}, placement(view), 0);
    }

    // Only a hardware cover request's bitmap. Without hardware cover Glide
    // has cropped the bitmap, and ImageView lays it out as before; so it does
    // an image that isn't a bitmap (an animated WebP or an APNG, which Glide
    // never crops), the placeholder, and nothing.
    @Test
    public void otherImagesAreLaidOutByImageViewAsBefore() {
        FastImageViewWithUrl software = view(105, 252, false);
        software.setImageDrawable(drawable(pattern(105, 252)));
        assertEquals(CENTER_CROP, software.getScaleType());
        // Also a bitmap that isn't the view's size (cropped for its previous
        // size).
        software.setImageDrawable(drawable(pattern(403, 252)));
        assertEquals(CENTER_CROP, software.getScaleType());

        FastImageViewWithUrl view = view(105, 252, true);
        view.setImageDrawable(new ColorDrawable(Color.RED));
        assertEquals(CENTER_CROP, view.getScaleType());
        view.setImageDrawable(drawable(pattern(403, 252)));
        assertEquals(MATRIX, view.getScaleType());
        view.setImageDrawable(null);
        assertEquals(CENTER_CROP, view.getScaleType());

        // No shown request (nothing loaded yet, or the load failed).
        FastImageViewWithUrl empty = view(105, 252, true);
        ReflectionHelpers.setField(empty, "mShownRequest", null);
        empty.setImageDrawable(drawable(pattern(403, 252)));
        assertEquals(CENTER_CROP, empty.getScaleType());
    }

    // objectFit or objectPosition changed while the bitmap is on screen (the
    // image loads again for it): laid out as before for those.
    @Test
    public void anotherFitOrPositionIsLaidOutAsBefore() {
        FastImageViewWithUrl view = view(105, 252, true);
        view.setImageDrawable(drawable(pattern(403, 252)));
        assertEquals(MATRIX, view.getScaleType());
        assertTrue(placement(view)[0] == -148);
        // Not centered: the view's own layout for a position, which rounds
        // its offset with Math.round.
        view.setObjectPositionX(0);
        assertEquals(MATRIX, view.getScaleType());
        assertFalse(placement(view)[0] == -148);
        assertEquals(0, placement(view)[0], 0);
        view.setObjectPositionX(.5f);
        assertArrayEquals(new float[]{-148, 0, 1}, placement(view), 0);
    }
}
