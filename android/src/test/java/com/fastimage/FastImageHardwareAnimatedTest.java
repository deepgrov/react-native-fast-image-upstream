package com.fastimage;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.load.ImageHeaderParser;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.data.InputStreamRewinder;
import com.bumptech.glide.load.engine.GlideLoadPaths;
import com.bumptech.glide.load.engine.LoadPath;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.DefaultImageHeaderParser;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.load.resource.bytes.ByteBufferRewinder;
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.StreamGifDecoder;
import com.bumptech.glide.manager.GlideConnectivity;
import com.bumptech.glide.request.RequestOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// An animated image that turns up for a hardware cover request
// (FastImageHardware) is decoded as for the same request without hardware
// cover: a hardware bitmap is for a still image, and the request is made
// before the image's format is known. A GIF's frames are cropped to the
// view's box, as Glide crops them for any other cover image, and an animated
// WebP or AVIF is decoded with the same options.
// The tests decode with the decoders and a registry of their own, not with
// Glide's registry: building that one makes React Native's OkHttp client,
// which FastImageGlideNetworkTest has to be the first to do.
@RunWith(RobolectricTestRunner.class)
public class FastImageHardwareAnimatedTest {
    // A 12x6 GIF of two frames (red, then blue).
    private static final byte[] GIF = bytes("4749463839610c000600810000ff000000000000000000000021ff0b4e4554534341"
            + "5045322e30030100000021f904000a0000002c000000000c0006000008100001081c48b0a0c18308132a5c6830200021f9"
            + "04010a0001002c000000000c000600810000ff00000000000000000008100001081c48b0a0c18308132a5c683020003b");
    // A PNG's first bytes: not a GIF.
    private static final byte[] PNG = bytes("89504e470d0a1a0a0000000d49484452");
    private static final String MODEL = "https://example.org/cover.gif";

    private final Context context = RuntimeEnvironment.getApplication();
    private Glide glide;
    // Glide's own GIF decoder, as its registry has it.
    private ByteBufferGifDecoder glideDecoder;
    private Object registeredBefore;

    @Before
    public void setUp() {
        registeredBefore = ReflectionHelpers.getStaticField(FastImageGlide.class, "registered");
        glide = Glide.get(context);
        glideDecoder = new ByteBufferGifDecoder(context, parsers(), glide.getBitmapPool(), glide.getArrayPool());
    }

    @After
    public void tearDown() {
        // Glide's registry wasn't built here (see above).
        assertSame(registeredBefore, ReflectionHelpers.getStaticField(FastImageGlide.class, "registered"));
        Glide.tearDown();
        GlideConnectivity.reset();
    }

    private static List<ImageHeaderParser> parsers() {
        return Collections.<ImageHeaderParser>singletonList(new DefaultImageHeaderParser());
    }

    private static byte[] bytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }

    // A centered cover image's request, as the view makes it
    // (FastImageViewWithUrl.renderingOptions), with hardware cover or without.
    private static RequestOptions cover(boolean hardware) {
        return FastImageSourceSize.scaleTypeOptions(CENTER_CROP,
                FastImageSourceSize.capture(CENTER_CROP, MODEL, true, hardware), null, null, true, hardware)
                .set(FastImageGlide.REQUEST, true);
    }

    // The GIF a request gets: decoded by the decoder, then transformed with
    // the request's transformation for a GIF if it has one, as Glide's
    // DecodeJob does with what a decoder gives it.
    @SuppressWarnings("unchecked")
    private GifDrawable decode(ResourceDecoder<ByteBuffer, GifDrawable> decoder, RequestOptions request,
            int width, int height) throws Exception {
        ByteBuffer data = ByteBuffer.wrap(GIF);
        assertTrue(decoder.handles(data, request.getOptions()));
        Resource<GifDrawable> decoded = decoder.decode(data, width, height, request.getOptions());
        assertNotNull(decoded);
        Transformation<GifDrawable> transformation =
                (Transformation<GifDrawable>) request.getTransformations().get(GifDrawable.class);
        if (transformation == null) assertFalse(request.isTransformationRequired());
        else decoded = transformation.transform(context, decoded, width, height);
        return decoded.get();
    }

    // The frames' size: the view's box, whatever the GIF's size, as without
    // hardware cover (not the GIF's own 12x6).
    @Test
    public void aGifsFramesAreCroppedToTheBoxAsWithoutHardwareCover() throws Exception {
        ResourceDecoder<ByteBuffer, GifDrawable> coverDecoder = new FastImageGif.CoverDecoder<>(context, glideDecoder);
        for (int[] box : new int[][]{{4, 4}, {3, 6}, {12, 6}, {24, 24}, {30, 10}}) {
            // Glide's decoder and the request's CenterCrop.
            GifDrawable expected = decode(glideDecoder, cover(false), box[0], box[1]);
            GifDrawable gif = decode(coverDecoder, cover(true), box[0], box[1]);
            String name = box[0] + "x" + box[1];
            assertEquals(name, box[0], expected.getIntrinsicWidth());
            assertEquals(name, box[1], expected.getIntrinsicHeight());
            assertEquals(name, expected.getIntrinsicWidth(), gif.getIntrinsicWidth());
            assertEquals(name, expected.getIntrinsicHeight(), gif.getIntrinsicHeight());
            assertEquals(name, expected.getFirstFrame().getWidth(), gif.getFirstFrame().getWidth());
            assertEquals(name, expected.getFirstFrame().getHeight(), gif.getFirstFrame().getHeight());
            assertEquals(name, expected.getFrameCount(), gif.getFrameCount());
            assertEquals(name, expected.getFrameTransformation(), gif.getFrameTransformation());
            assertEquals(name, new CenterCrop(), gif.getFrameTransformation());
        }
        // What it's for: Glide's decoder alone leaves a hardware cover
        // request's GIF at its own size, as that request has no
        // transformation.
        GifDrawable uncropped = decode(glideDecoder, cover(true), 4, 4);
        assertEquals(12, uncropped.getIntrinsicWidth());
        assertEquals(6, uncropped.getIntrinsicHeight());
    }

    // Each view plays a copy of its own (FastImageGif.copy), with the
    // transformation of the GIF Glide gave it: cropped to the box too.
    @Test
    public void aViewsOwnCopyOfTheGifIsCroppedToTheBox() throws Exception {
        GifDrawable gif = decode(new FastImageGif.CoverDecoder<>(context, glideDecoder), cover(true), 4, 4);
        GifDrawable copy = FastImageGif.copy(context, gif, 4, 4, false);
        assertNotNull(copy);
        assertEquals(4, copy.getIntrinsicWidth());
        assertEquals(4, copy.getIntrinsicHeight());
        assertEquals(new CenterCrop(), copy.getFrameTransformation());
    }

    // Only FastImage's hardware cover requests' GIFs: other requests' GIFs,
    // other libraries' requests, and images that aren't GIFs are left to the
    // decoders after this one (Glide's own for a GIF).
    @Test
    public void onlyAHardwareCoverRequestsGifIsDecodedHere() throws Exception {
        ResourceDecoder<ByteBuffer, GifDrawable> decoder = new FastImageGif.CoverDecoder<>(context, glideDecoder);
        Options hardware = cover(true).getOptions();
        assertTrue(FastImageHardware.requested(hardware));
        assertTrue(decoder.handles(ByteBuffer.wrap(GIF), hardware));
        assertFalse(decoder.handles(ByteBuffer.wrap(PNG), hardware));

        Options software = cover(false).getOptions();
        assertFalse(FastImageHardware.requested(software));
        assertFalse(decoder.handles(ByteBuffer.wrap(GIF), software));

        // Without FastImage's mark: another library's request.
        Options other = FastImageSourceSize.scaleTypeOptions(CENTER_CROP,
                FastImageSourceSize.capture(CENTER_CROP, MODEL, true, true), null, null, true, true).getOptions();
        assertFalse(decoder.handles(ByteBuffer.wrap(GIF), other));
        assertFalse(FastImageHardware.requested(new Options()));
    }

    // In a registry with Glide's GIF decoders, as Glide registers them,
    // FastImage's are ahead of them (FastImageGif.register): a hardware cover
    // request's GIF comes cropped from the registry's decoders, from a buffer
    // and from a stream, and another request's as Glide's decoder gives it
    // (Glide's DecodeJob crops that one after).
    @Test
    public void theRegistryGivesAHardwareCoverRequestsGifToFastImagesDecoder() throws Exception {
        Registry registry = new Registry();
        registry.register(new DefaultImageHeaderParser());
        registry.register(new ByteBufferRewinder.Factory());
        registry.register(new InputStreamRewinder.Factory(glide.getArrayPool()));
        registry.append(Registry.BUCKET_ANIMATION, InputStream.class, GifDrawable.class,
                new StreamGifDecoder(parsers(), glideDecoder, glide.getArrayPool()));
        registry.append(Registry.BUCKET_ANIMATION, ByteBuffer.class, GifDrawable.class, glideDecoder);
        FastImageGif.register(context, glide, registry);

        LoadPath<ByteBuffer, Object, Drawable> buffers =
                registry.getLoadPath(ByteBuffer.class, Object.class, Drawable.class);
        LoadPath<InputStream, Object, Drawable> streams =
                registry.getLoadPath(InputStream.class, Object.class, Drawable.class);
        Options hardware = cover(true).getOptions();
        Drawable fromBuffer = GlideLoadPaths.decode(
                buffers, registry.getRewinder(ByteBuffer.wrap(GIF)), hardware, 4, 4).get();
        assertTrue(fromBuffer instanceof GifDrawable);
        assertEquals(4, fromBuffer.getIntrinsicWidth());
        assertEquals(4, fromBuffer.getIntrinsicHeight());
        Drawable fromStream = GlideLoadPaths.decode(streams,
                registry.getRewinder((InputStream) new ByteArrayInputStream(GIF)), hardware, 4, 4).get();
        assertTrue(fromStream instanceof GifDrawable);
        assertEquals(4, fromStream.getIntrinsicWidth());
        assertEquals(4, fromStream.getIntrinsicHeight());

        Drawable other = GlideLoadPaths.decode(
                buffers, registry.getRewinder(ByteBuffer.wrap(GIF)), cover(false).getOptions(), 4, 4).get();
        assertTrue(other instanceof GifDrawable);
        assertEquals(12, other.getIntrinsicWidth());
        assertEquals(6, other.getIntrinsicHeight());
    }

    // An animated WebP or AVIF is decoded at the size its options' downsample
    // strategy gives (Glide's AnimatedImageDecoder): for a hardware cover
    // request, the strategy and the hardware setting of the same request
    // without it, so also for an image hardware cover would decode smaller
    // (FastImageHardware.MAX_PIXELS).
    @Test
    public void anAnimatedImagesOptionsAreTheRequestsWithoutHardwareCover() {
        Options hardware = cover(true).getOptions();
        Options software = cover(false).getOptions();
        Options animated = FastImageHardware.animatedOptions(hardware);
        assertEquals(software.get(DownsampleStrategy.OPTION), animated.get(DownsampleStrategy.OPTION));
        assertFalse(animated.get(Downsampler.ALLOW_HARDWARE_CONFIG));
        assertEquals(software.get(Downsampler.ALLOW_HARDWARE_CONFIG), animated.get(Downsampler.ALLOW_HARDWARE_CONFIG));
        assertTrue(FastImageGlide.isRequest(animated));
        assertFalse(FastImageHardware.requested(animated));
        // The request's own options aren't changed: a still image is decoded
        // with them.
        assertTrue(hardware.get(Downsampler.ALLOW_HARDWARE_CONFIG));
        assertTrue(FastImageHardware.requested(hardware));
        for (int[] c : new int[][]{
                // source width and height, box width and height
                {640, 360, 262, 263},
                {80, 80, 126, 126},
                // Over hardware cover's limit once it covers the box.
                {1000, 100000, 1000, 1000},
                {20, 20, 4095, 4095},
        }) {
            float expected = software.get(DownsampleStrategy.OPTION).getScaleFactor(c[0], c[1], c[2], c[3]);
            assertEquals(DownsampleStrategy.CENTER_OUTSIDE.getScaleFactor(c[0], c[1], c[2], c[3]), expected, 0);
            assertEquals(expected, animated.get(DownsampleStrategy.OPTION).getScaleFactor(c[0], c[1], c[2], c[3]), 0);
        }
        // Other requests' options are used as they are.
        assertSame(software, FastImageHardware.animatedOptions(software));
    }

    // FastImage's decoder for animated WebP and AVIF (FastImageAnimated) hands
    // Glide's decoder those options, and keeps them for the copies it decodes
    // for other views.
    @Test
    public void theAnimatedDecoderDecodesWithThoseOptions() throws Exception {
        final List<Options> decodedWith = new ArrayList<>();
        ResourceDecoder<ByteBuffer, Drawable> glide = new ResourceDecoder<ByteBuffer, Drawable>() {
            @Override
            public boolean handles(ByteBuffer source, Options options) {
                return true;
            }

            @Override
            public Resource<Drawable> decode(ByteBuffer source, int width, int height, Options options) {
                decodedWith.add(options);
                return null;
            }
        };
        ResourceDecoder<ByteBuffer, BitmapDrawable> still = new ResourceDecoder<ByteBuffer, BitmapDrawable>() {
            @Override
            public boolean handles(ByteBuffer source, Options options) {
                return true;
            }

            @Override
            public Resource<BitmapDrawable> decode(ByteBuffer source, int width, int height, Options options) {
                throw new AssertionError("not decoded as a still image");
            }
        };
        ResourceDecoder<ByteBuffer, Drawable> decoder = ReflectionHelpers.callConstructor(
                Class.forName("com.fastimage.FastImageAnimated$ByteBufferDecoder").asSubclass(ResourceDecoder.class),
                ClassParameter.from(ResourceDecoder.class, glide), ClassParameter.from(ResourceDecoder.class, still));
        Options hardware = cover(true).getOptions();
        assertNull(decoder.decode(ByteBuffer.wrap(new byte[0]), 1000, 1000, hardware));
        assertEquals(1, decodedWith.size());
        assertEquals(cover(false).getOptions().get(DownsampleStrategy.OPTION),
                decodedWith.get(0).get(DownsampleStrategy.OPTION));
        assertFalse(decodedWith.get(0).get(Downsampler.ALLOW_HARDWARE_CONFIG));

        Options software = cover(false).getOptions();
        decoder.decode(ByteBuffer.wrap(new byte[0]), 1000, 1000, software);
        assertSame(software, decodedWith.get(1));
    }

    // A still image's hardware cover request is as it was: no transformation
    // and hardware bitmaps allowed (the still path isn't these tests').
    @Test
    public void aStillImagesHardwareCoverRequestIsUnchanged() {
        RequestOptions hardware = cover(true);
        assertTrue(hardware.getOptions().get(Downsampler.ALLOW_HARDWARE_CONFIG));
        assertNull(hardware.getTransformations().get(Bitmap.class));
        assertNull(hardware.getTransformations().get(GifDrawable.class));
        assertEquals(FastImageHardware.COVER.getScaleFactor(1000, 100000, 1000, 1000),
                hardware.getOptions().get(DownsampleStrategy.OPTION).getScaleFactor(1000, 100000, 1000, 1000), 0);
    }
}
