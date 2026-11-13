package com.fastimage;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.CENTER_INSIDE;
import static android.widget.ImageView.ScaleType.FIT_CENTER;
import static android.widget.ImageView.ScaleType.FIT_XY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.ImageHeaderParser;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.DefaultImageHeaderParser;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.GifDrawableTransformation;
import com.bumptech.glide.manager.GlideConnectivity;
import com.bumptech.glide.request.RequestOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

// A cover image's still bitmap is no larger than the image's own size
// (FastImageSourceSize.COVER, FastImageCoverCrop, and FastImageHardware.COVER
// for hardware cover): an image smaller than the box isn't scaled up to cover
// it, as Glide's CENTER_OUTSIDE and CenterCrop would. The view scales it up
// as it draws it. A cover image larger than the box, other fits, animated
// images and SVGs are decoded as before. Images are decoded with Glide's
// Downsampler and cropped with the request's transformation, as Glide's
// DecodeJob does, with Robolectric's native graphics.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FastImageCoverOwnSizeTest {
    private static final String MODEL = "https://example.org/cover.png";

    private final Context context = RuntimeEnvironment.getApplication();
    private Object registeredBefore;
    private Glide glide;

    @Before
    public void setUp() {
        registeredBefore = ReflectionHelpers.getStaticField(FastImageGlide.class, "registered");
        glide = Glide.get(context);
    }

    @After
    public void tearDown() {
        // Glide's registry isn't built here (see FastImageHardwareAnimatedTest).
        assertSame(registeredBefore, ReflectionHelpers.getStaticField(FastImageGlide.class, "registered"));
        Glide.tearDown();
        GlideConnectivity.reset();
    }

    private static RequestOptions request(ImageView.ScaleType fit, boolean hardware, FastImageCrop crop,
            FastImageBlur blur) {
        return FastImageSourceSize.scaleTypeOptions(fit, FastImageSourceSize.capture(fit, MODEL, true, hardware),
                crop, blur, true, hardware).set(FastImageGlide.REQUEST, true);
    }

    // A PNG whose pixels are all different (see FastImageHardwarePositionTest).
    private static Bitmap pattern(int width, int height) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = Color.rgb(x & 0xff, y & 0xff, ((x >> 8) << 4) | (y >> 8));
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static byte[] png(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pattern(width, height).compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return pixels;
    }

    private static int[] size(Bitmap bitmap) {
        return new int[]{bitmap.getWidth(), bitmap.getHeight()};
    }

    // What Glide's Downsampler decodes an image (width x height) at for a
    // request and a box.
    private Bitmap decode(RequestOptions request, int width, int height, int boxWidth, int boxHeight)
            throws Exception {
        List<ImageHeaderParser> parsers = Collections.singletonList(new DefaultImageHeaderParser());
        Downsampler downsampler = new Downsampler(parsers, context.getResources().getDisplayMetrics(),
                glide.getBitmapPool(), glide.getArrayPool());
        return downsampler.decode(new ByteArrayInputStream(png(width, height)), boxWidth, boxHeight,
                request.getOptions()).get();
    }

    // The bitmap a request gives: decoded, then transformed with its
    // transformation.
    @SuppressWarnings("unchecked")
    private Bitmap load(RequestOptions request, int width, int height, int boxWidth, int boxHeight)
            throws Exception {
        Bitmap decoded = decode(request, width, height, boxWidth, boxHeight);
        Transformation<Bitmap> transformation =
                (Transformation<Bitmap>) request.getTransformations().get(Bitmap.class);
        if (transformation == null) return decoded;
        Resource<Bitmap> resource = BitmapResource.obtain(decoded, glide.getBitmapPool());
        return transformation.transform(context, resource, boxWidth, boxHeight).get();
    }

    // A 400x500 thumbnail behind a full-screen 1080x2340 view (blurred, so
    // without hardware cover): decoded at 400x500, not 1872x2340, and cropped
    // to the view's shape at that size, 231x500 (0.46 MB, where Glide's
    // CENTER_OUTSIDE and CenterCrop give 1080x2340, 10 MB, after a 17.5 MB
    // decode). Blurred at that size too.
    @Test
    public void aCoverImageSmallerThanTheBoxIsntScaledUp() throws Exception {
        FastImageBlur blur = new FastImageBlur(24, CENTER_CROP, 1);
        assertArrayEquals(new int[]{400, 500}, size(decode(request(CENTER_CROP, false, null, blur), 400, 500, 1080, 2340)));
        assertArrayEquals(new int[]{231, 500}, size(load(request(CENTER_CROP, false, null, blur), 400, 500, 1080, 2340)));
        assertArrayEquals(new int[]{231, 500}, size(load(request(CENTER_CROP, false, null, null), 400, 500, 1080, 2340)));
        // Small images in a card's box, by one side or both.
        assertArrayEquals(new int[]{200, 250}, size(load(request(CENTER_CROP, false, null, null), 200, 250, 288, 360)));
        assertArrayEquals(new int[]{400, 500}, size(load(request(CENTER_CROP, false, null, null), 400, 500, 480, 600)));
        assertArrayEquals(new int[]{80, 80}, size(load(request(CENTER_CROP, false, null, null), 80, 80, 126, 126)));
        assertArrayEquals(new int[]{300, 300}, size(load(request(CENTER_CROP, false, null, null), 600, 300, 420, 420)));
        // A hardware cover request's (the view crops it): the whole image.
        assertArrayEquals(new int[]{200, 250}, size(load(request(CENTER_CROP, true, null, null), 200, 250, 288, 360)));
        assertArrayEquals(new int[]{600, 300}, size(load(request(CENTER_CROP, true, null, null), 600, 300, 420, 420)));
    }

    // A cover image at least as large as what covers the box: as before, the
    // box's size, the same pixels Glide's CenterCrop gives.
    @Test
    public void aCoverImageLargerThanTheBoxIsCroppedAsBefore() throws Exception {
        for (int[] c : new int[][]{{800, 1000, 396, 495}, {1024, 1371, 264, 330}, {800, 1000, 720, 676},
                {400, 500, 400, 500}, {1600, 600, 300, 300}}) {
            String name = c[0] + "x" + c[1] + " for " + c[2] + "x" + c[3];
            Bitmap bitmap = load(request(CENTER_CROP, false, null, null), c[0], c[1], c[2], c[3]);
            assertArrayEquals(name, new int[]{c[2], c[3]}, size(bitmap));
            Bitmap decoded = decode(request(CENTER_CROP, false, null, null), c[0], c[1], c[2], c[3]);
            Bitmap glides = TransformationUtils.centerCrop(glide.getBitmapPool(), decoded, c[2], c[3]);
            assertArrayEquals(name, pixels(glides), pixels(bitmap));
        }
    }

    // The crop of a bitmap that doesn't cover the box: the box's shape, at the
    // bitmap's scale (the side it covers by is the bitmap's own), the pixels
    // at the center, not resampled.
    @Test
    public void theCropOfASmallerBitmapIsTheBoxsShapeAtItsScale() {
        assertArrayEquals(new int[]{231, 500}, FastImageCoverCrop.box(400, 500, 1080, 2340));
        assertArrayEquals(new int[]{400, 180}, FastImageCoverCrop.box(400, 500, 1080, 486));
        assertArrayEquals(new int[]{200, 250}, FastImageCoverCrop.box(200, 250, 288, 360));
        // Covered at its size or smaller: the box.
        assertArrayEquals(new int[]{1080, 2340}, FastImageCoverCrop.box(1080, 4000, 1080, 2340));
        assertArrayEquals(new int[]{396, 495}, FastImageCoverCrop.box(400, 500, 396, 495));
        // A pixel at least.
        assertArrayEquals(new int[]{1, 10}, FastImageCoverCrop.box(10, 10, 1, 100000));

        Bitmap bitmap = pattern(400, 500);
        Bitmap cropped = TransformationUtils.centerCrop(glide.getBitmapPool(), bitmap, 231, 500);
        Bitmap ours = new FastImageCoverCrop(null).transform(glide.getBitmapPool(), bitmap, 1080, 2340);
        assertArrayEquals(pixels(Bitmap.createBitmap(bitmap, 84, 0, 231, 500)), pixels(cropped));
        assertArrayEquals(pixels(cropped), pixels(ours));
    }

    // At an objectPosition, the same, with its offsets (in the box's pixels)
    // at the bitmap's scale.
    @Test
    public void aPositionedCropOfASmallerBitmapHasItsOffsetsScaled() {
        Bitmap bitmap = pattern(400, 500);
        FastImageCrop crop = new FastImageCrop(0, 46.8f, 1, -23.4f);
        Bitmap ours = new FastImageCoverCrop(crop).transform(glide.getBitmapPool(), bitmap, 1080, 2340);
        // 2340 / 500: the box at the bitmap's scale is 231x500, the offsets 10
        // and -5.
        Bitmap expected = new FastImageCrop(0, 10, 1, -5).transform(glide.getBitmapPool(), bitmap, 231, 500);
        assertArrayEquals(new int[]{231, 500}, size(ours));
        assertArrayEquals(pixels(expected), pixels(ours));
        // A bitmap that covers the box: FastImageCrop as it is.
        Bitmap large = pattern(1200, 2600);
        assertArrayEquals(pixels(crop.transform(glide.getBitmapPool(), large, 1080, 2340)),
                pixels(new FastImageCoverCrop(crop).transform(glide.getBitmapPool(), large, 1080, 2340)));
    }

    // Glide's memory and disk caches key a transformed image by its
    // transformation: this crop isn't CenterCrop's or FastImageCrop's.
    @Test
    public void theCropIsItsOwnInGlidesCacheKeys() throws Exception {
        assertEquals(new FastImageCoverCrop(null), new FastImageCoverCrop(null));
        assertEquals(new FastImageCoverCrop(null).hashCode(), new FastImageCoverCrop(null).hashCode());
        FastImageCrop crop = new FastImageCrop(0, 1, 1, 0);
        assertEquals(new FastImageCoverCrop(crop), new FastImageCoverCrop(new FastImageCrop(0, 1, 1, 0)));
        assertNotEquals(new FastImageCoverCrop(null), new FastImageCoverCrop(crop));
        assertNotEquals(new CenterCrop(), new FastImageCoverCrop(null));
        assertNotEquals(digest(new CenterCrop()), digest(new FastImageCoverCrop(null)));
        assertNotEquals(digest(crop), digest(new FastImageCoverCrop(crop)));
    }

    private static String digest(Transformation<Bitmap> transformation) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        transformation.updateDiskCacheKey(digest);
        return java.util.Arrays.toString(digest.digest());
    }

    // The request's strategies: a still cover image's no larger than its own
    // size; an SVG's (drawn for the box) and an animated image's (whose
    // frames' size doesn't change) CENTER_OUTSIDE, as before. A GIF's frames
    // are cropped to the box with Glide's CenterCrop, as before.
    @Test
    public void onlyAStillCoverImagesBitmapIsLimited() {
        RequestOptions software = request(CENTER_CROP, false, null, null);
        FastImageSourceSize.Capture capture =
                (FastImageSourceSize.Capture) software.getOptions().get(DownsampleStrategy.OPTION);
        assertEquals(1, capture.getScaleFactor(400, 500, 1080, 2340), 0);
        assertEquals(DownsampleStrategy.CENTER_OUTSIDE.getScaleFactor(400, 500, 1080, 2340),
                capture.getVectorScaleFactor(400, 500, 1080, 2340), 0);
        assertEquals(DownsampleStrategy.CENTER_OUTSIDE.getScaleFactor(400, 500, 1080, 2340),
                capture.forAnimation().getScaleFactor(400, 500, 1080, 2340), 0);
        assertEquals(new GifDrawableTransformation(new CenterCrop()),
                software.getTransformations().get(GifDrawable.class));
        FastImageCrop crop = new FastImageCrop(0, 0, 0, 0);
        assertEquals(new GifDrawableTransformation(crop),
                request(CENTER_CROP, false, crop, null).getTransformations().get(GifDrawable.class));
        assertEquals(new FastImageCoverCrop(crop),
                request(CENTER_CROP, false, crop, null).getTransformations().get(Bitmap.class));
        assertFalse(software.isTransformationRequired());
        assertTrue(software.isTransformationSet());
    }

    // Other fits are decoded as before: contain at the size that fits the box
    // (scaled up, as 8.x's FitCenter scaled it up after its decode), and
    // scale-down and fill no larger than the image (as always).
    @Test
    public void otherFitsAreDecodedAsBefore() throws Exception {
        assertArrayEquals(new int[]{1080, 270}, size(load(request(FIT_CENTER, false, null, null), 720, 180, 1080, 270)));
        assertArrayEquals(new int[]{973, 2115}, size(load(request(FIT_CENTER, false, null, null), 1206, 2622, 976, 2115)));
        assertArrayEquals(new int[]{720, 180}, size(load(request(CENTER_INSIDE, false, null, null), 720, 180, 1080, 270)));
        assertArrayEquals(new int[]{720, 180}, size(load(request(FIT_XY, false, null, null), 720, 180, 1080, 270)));
    }

    // The view draws the smaller bitmap covering itself, scaled up (a
    // software one with ImageView's CENTER_CROP).
    @Test
    public void theViewScalesTheSmallerBitmapUpToCoverIt() {
        FastImageViewWithUrl view = new FastImageViewWithUrl(context, null);
        view.setObjectFit(CENTER_CROP);
        view.layout(0, 0, 1080, 2340);
        view.setImageDrawable(new BitmapDrawable(context.getResources(), pattern(231, 500)));
        assertEquals(CENTER_CROP, view.getScaleType());
        float[] values = new float[9];
        view.getImageMatrix().getValues(values);
        assertEquals(2340 / 500f, values[0], .001f);
        assertTrue(231 * values[0] >= 1080);
    }
}
