package com.fastimage;

import android.os.Build;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;

// ImageView draws the centered crop, so Glide can keep its decoded hardware
// bitmap without reading it back into a software CenterCrop bitmap.
final class FastImageHardware {
    private FastImageHardware() {}
    // Reserve eight bytes/pixel for F16 hardware images. A no-transform cover
    // can otherwise decode a very long image far larger than its target box.
    static final long MAX_PIXELS = 8L * 1024 * 1024;
    static final DownsampleStrategy COVER = new BoundedCover();

    static boolean allowed(View view, @Nullable ImageView.ScaleType fit,
            @Nullable FastImageCrop crop, @Nullable FastImageBlur blur, boolean downsample) {
        if (Build.VERSION.SDK_INT < 28 || !view.isHardwareAccelerated()
                || fit != ImageView.ScaleType.CENTER_CROP || crop != null || blur != null || !downsample) return false;
        for (View current = view; current != null;) {
            if (current.getLayerType() == View.LAYER_TYPE_SOFTWARE) return false;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return true;
    }

    // Whether these are a hardware cover request's options (see
    // FastImageSourceSize.scaleTypeOptions). An animated image that turns up
    // for one (the request is made before the image's format is known) is
    // decoded as for the same request without it: an animated WebP or AVIF
    // with FastImageSourceSize.animatedOptions (FastImageAnimated), and a GIF
    // cropped as Glide crops one (FastImageGif.CoverDecoder).
    static boolean requested(Options options) {
        DownsampleStrategy strategy = options.get(DownsampleStrategy.OPTION);
        return strategy instanceof FastImageSourceSize.Capture
                && ((FastImageSourceSize.Capture) strategy).hardwareCover();
    }

    // At most the image's own size, as FastImageSourceSize.COVER: the view
    // scales a smaller image up as it draws it (updateImageMatrix).
    private static final class BoundedCover extends DownsampleStrategy {
        @Override public float getScaleFactor(int sourceWidth, int sourceHeight, int requestedWidth, int requestedHeight) {
            float scale = Math.min(1, CENTER_OUTSIDE.getScaleFactor(sourceWidth, sourceHeight, requestedWidth, requestedHeight));
            double area = (double) sourceWidth * sourceHeight;
            if (area <= 0) return scale;
            // Solve (width*s+1)*(height*s+1) <= MAX_PIXELS so rounded-up
            // dimensions fit too, without iterating for extreme aspect ratios.
            double sum = (double) sourceWidth + sourceHeight;
            double cap = 2 * (MAX_PIXELS - 1d)
                    / (sum + Math.sqrt(sum * sum + 4 * area * (MAX_PIXELS - 1d)));
            scale = Math.min(scale, (float) cap);
            if (Math.ceil(sourceWidth * (double) scale) * Math.ceil(sourceHeight * (double) scale) > MAX_PIXELS) scale = Math.nextDown(scale);
            return scale;
        }
        @Override public SampleSizeRounding getSampleSizeRounding(int sw, int sh, int rw, int rh) {
            return SampleSizeRounding.QUALITY;
        }
    }
}
