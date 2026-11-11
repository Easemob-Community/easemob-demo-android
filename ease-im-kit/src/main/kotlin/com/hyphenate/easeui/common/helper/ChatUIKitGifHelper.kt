package com.hyphenate.easeui.common.helper

import android.content.Context
import com.hyphenate.easeui.R
import com.hyphenate.easeui.model.ChatUIKitEmojicon
import com.hyphenate.easeui.model.ChatUIKitEmojiconGroupEntity
import java.io.File

/**
 * Helper for the built-in gif emojicons aligned with iOS:
 * gifs live in assets/gifs/N.gif with covers in assets/gifs/covers/gif_cover_N.png.
 */
object ChatUIKitGifHelper {
    const val GIF_COUNT = 13

    /**
     * Captions under each gif cover, aligned with gif index 1..13.
     * Empty string means no caption.
     */
    val captions: List<String> = listOf(
        "亮个相吧", "辛苦啦", "Bug终结者", "你说神嘛", "报错了", "真棒", "笑死",
        "厉害", "真棒棒", "好的收到", "上线成功", "成功啦", "谢谢老板"
    )

    private const val ASSET_GIF_DIR = "gifs"
    private const val ASSET_COVER_DIR = "gifs/covers"
    // Versioned directory: gif assets were re-ordered once (covers/captions are the
    // source of truth for the order), so previously cached sandbox files must not
    // be reused. Bump the suffix when the asset order changes again.
    private const val SANDBOX_DIR = "ChatGifs_v2"

    fun coverAssetPath(index: Int): String {
        return "file:///android_asset/$ASSET_COVER_DIR/gif_cover_$index.png"
    }

    fun gifAssetName(index: Int): String {
        return "$ASSET_GIF_DIR/$index.gif"
    }

    /**
     * Build the default gif emojicon group shown in the emoji panel.
     */
    fun buildGifGroup(context: Context): ChatUIKitEmojiconGroupEntity {
        val packageName = context.packageName
        val list = mutableListOf<ChatUIKitEmojicon>()
        for (i in 1..GIF_COUNT) {
            val coverId = context.resources.getIdentifier(
                "gif_cover_$i", "drawable", packageName
            )
            val emojicon = ChatUIKitEmojicon(
                icon = coverId,
                type = ChatUIKitEmojicon.Type.GIF,
                identityCode = i.toString(),
                name = captions.getOrElse(i - 1) { "" },
                iconPath = coverAssetPath(i)
            )
            list.add(emojicon)
        }
        return ChatUIKitEmojiconGroupEntity(
            icon = R.drawable.message_bar_gif,
            emojiconList = list,
            name = "GIF",
            type = ChatUIKitEmojicon.Type.GIF
        )
    }

    /**
     * Whether the gif file name (e.g. "3.gif") is one of the built-in gifs,
     * used to display them at a fixed size like iOS.
     */
    fun isBuiltInGif(fileName: String?): Boolean {
        val index = fileName?.substringBeforeLast('.')?.toIntOrNull() ?: return false
        return index in 1..GIF_COUNT
    }

    /**
     * Copy the built-in gif from assets into the app sandbox (asset paths are not
     * stable across reinstalls/upgrades and should not be referenced by a message
     * body), and return the sandbox path. Returns null when the copy fails.
     */
    fun sandboxGifPath(context: Context, index: String?): String? {
        val i = index?.toIntOrNull() ?: return null
        if (i !in 1..GIF_COUNT) return null
        val dir = File(context.filesDir, SANDBOX_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val target = File(dir, "$i.gif")
        if (target.exists() && target.length() > 0) {
            return target.absolutePath
        }
        return try {
            context.assets.open(gifAssetName(i)).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
