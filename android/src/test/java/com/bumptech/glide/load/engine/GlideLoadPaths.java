package com.bumptech.glide.load.engine;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataRewinder;

// Decodes with a registry's load path, as Glide's DecodeJob does, without
// the transformation it applies after: the decode callback is package-private
// (so this is in Glide's package). For tests of which decoder a registry
// gives an image to.
public final class GlideLoadPaths {
    private GlideLoadPaths() {}

    public static <Data, ResourceType, Transcode> Resource<Transcode> decode(
            LoadPath<Data, ResourceType, Transcode> path, DataRewinder<Data> data, Options options,
            int width, int height) throws GlideException {
        return path.load(data, options, width, height, resource -> resource);
    }
}
