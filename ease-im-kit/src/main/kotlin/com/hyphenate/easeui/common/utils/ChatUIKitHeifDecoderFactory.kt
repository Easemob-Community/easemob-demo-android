package com.hyphenate.easeui.common.utils

import android.os.Build
import coil.ImageLoader
import coil.decode.DecodeUtils
import coil.decode.Decoder
import coil.decode.ImageDecoderDecoder
import coil.decode.isHeif
import coil.fetch.SourceResult
import coil.request.Options

/**
 * Static HEIC/HEIF photos are not covered by Coil's [ImageDecoderDecoder.Factory],
 * which only accepts animated media (GIF/animated WebP/animated HEIF). They fall
 * through to BitmapFactoryDecoder and fail on devices whose BitmapFactory has no
 * HEIC support, showing the error placeholder instead of the photo. Route them
 * through [ImageDecoder], which supports static HEIF/HEIC from API 28.
 */
class ChatUIKitHeifDecoderFactory : Decoder.Factory {

    override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
        if (Build.VERSION.SDK_INT < 28) return null
        return if (DecodeUtils.isHeif(result.source.source())) {
            ImageDecoderDecoder(result.source, options)
        } else null
    }
}
