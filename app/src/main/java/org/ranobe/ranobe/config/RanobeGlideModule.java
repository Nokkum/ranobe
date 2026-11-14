package org.ranobe.ranobe.config;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * How the app's pictures are loaded.
 * <p>
 * Covers are small and mostly flat colour, so 16 bits a pixel looks the same as 32 and takes half the memory, which
 * is what keeps lists of covers from filling a weak phone's memory. A picture with transparency is still decoded in
 * full, and a screen that needs every shade, like the reader's chapter images, asks for it (see PageAdapter).
 */
@GlideModule
public final class RanobeGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDefaultRequestOptions(new RequestOptions().format(DecodeFormat.PREFER_RGB_565));
    }

    // no old-style modules are declared in the manifest, so there is nothing to look for at start-up
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
