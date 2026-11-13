package com.fastimage;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;

import java.nio.charset.Charset;
import java.security.MessageDigest;

// A centered cover image's crop of a still image, no larger than the image's
// own size: Glide's CenterCrop for a bitmap that covers the box (as one
// decoded for it does). A bitmap that would have to be scaled up to cover the
// box (an image smaller than the view, which FastImageSourceSize.COVER
// decodes at its own size) is cropped to the box's shape at its own scale
// instead, and the view scales it up as it draws it. Not for a GIF's frames,
// which are cropped to the box as before (see
// FastImageSourceSize.scaleTypeOptions).
final class FastImageCoverCrop extends BitmapTransformation {
    private static final String ID = "com.fastimage.FastImageCoverCrop";
    private static final byte[] ID_BYTES = ID.getBytes(Charset.forName("UTF-8"));

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        int[] box = box(toTransform.getWidth(), toTransform.getHeight(), outWidth, outHeight);
        return TransformationUtils.centerCrop(pool, toTransform, box[0], box[1]);
    }

    // The box a bitmap (width x height) is cropped to: the request's
    // (outWidth x outHeight) if the bitmap covers it at its own size or
    // smaller, or else that box scaled down by as much as the bitmap would
    // have been scaled up: the box's shape, with the bitmap's own length on
    // the side it covers the box by.
    static int[] box(int width, int height, int outWidth, int outHeight) {
        if (width <= 0 || height <= 0 || outWidth <= 0 || outHeight <= 0) return new int[]{outWidth, outHeight};
        if ((long) outWidth * height >= (long) outHeight * width) {
            // By its width (the box is wider than the bitmap's shape).
            if (outWidth <= width) return new int[]{outWidth, outHeight};
            return new int[]{width, clamp(Math.round(outHeight * (double) width / outWidth), height)};
        }
        if (outHeight <= height) return new int[]{outWidth, outHeight};
        return new int[]{clamp(Math.round(outWidth * (double) height / outHeight), width), height};
    }

    private static int clamp(long length, int max) {
        return (int) Math.max(1, Math.min(max, length));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FastImageCoverCrop;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID_BYTES);
    }
}
