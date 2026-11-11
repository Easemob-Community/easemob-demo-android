/**
 * Copyright (C) 2016 Hyphenate Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hyphenate.easeui.feature.chat.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import coil.load
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hyphenate.easeui.ChatUIKitClient
import com.hyphenate.easeui.R
import com.hyphenate.easeui.base.ChatUIKitBaseActivity
import com.hyphenate.easeui.common.ChatClient
import com.hyphenate.easeui.common.ChatError
import com.hyphenate.easeui.common.ChatImageMessageBody
import com.hyphenate.easeui.common.ChatLog
import com.hyphenate.easeui.common.ChatMessage
import com.hyphenate.easeui.common.ChatUIKitConstant
import com.hyphenate.easeui.common.bus.ChatUIKitFlowBus
import com.hyphenate.easeui.common.extensions.hasRoute
import com.hyphenate.easeui.common.extensions.mainScope
import com.hyphenate.easeui.common.extensions.showToast
import com.hyphenate.easeui.common.impl.CallbackImpl
import com.hyphenate.easeui.common.utils.ChatUIKitFileUtils
import com.hyphenate.easeui.databinding.UikitActivityShowBigImageBinding
import com.hyphenate.easeui.model.ChatUIKitEvent
import com.hyphenate.easeui.widget.photoview.OnPhotoTapListener
import com.hyphenate.easeui.widget.photoview.OnViewTapListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen image viewer, aligned with the iOS ImageDetailViewController:
 * shows the thumbnail first, offers a bottom "查看原图" button that downloads
 * and toggles the original image, supports long-press to save to the gallery,
 * and relies on the built-in PhotoView for smooth pinch/double-tap zoom.
 */
class ChatUIKitShowBigImageActivity : ChatUIKitBaseActivity<UikitActivityShowBigImageBinding>() {
    private var default_res: Int = R.drawable.uikit_default_image
    private var filename: String? = null
    private var isNeedDownload = false
    private var msgId: String? = null
    private var imageMessage: ChatMessage? = null
    private var showingOriginal = false
    private var downloading = false
    private var thumbnailUri: Uri? = null
    private var displayedUri: Uri? = null

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fade-in, the same cross dissolve effect as iOS.
        overridePendingTransition(R.anim.uikit_fade_in, 0)

        default_res = intent.getIntExtra("default_image", R.drawable.uikit_default_image)
        val uri: Uri? = intent.getParcelableExtra("uri")
        filename = intent.extras?.getString("filename")
        msgId = intent.extras?.getString("messageId")
        val emojiIconId: String? =
            intent.extras?.getString(ChatUIKitConstant.MESSAGE_ATTR_EXPRESSION_ID)
        ChatLog.d(TAG, "show big msgId:$msgId")

        when {
            // A local file is passed directly: show it, nothing to download.
            ChatUIKitFileUtils.isFileExistByUri(this, uri) -> display(uri)
            !TextUtils.isEmpty(emojiIconId) -> showBigExpression(emojiIconId)
            msgId != null -> setupImageMessage()
            else -> binding.image.setImageResource(default_res)
        }

        binding.image.setOnViewTapListener(object : OnViewTapListener {
            override fun onViewTap(view: View?, x: Float, y: Float) {
                finish()
            }
        })
        binding.image.setOnPhotoTapListener(object : OnPhotoTapListener {
            override fun onPhotoTap(view: View?, x: Float, y: Float) {
                finish()
            }
        })
        binding.image.setOnLongClickListener {
            showSaveDialog()
            true
        }
        binding.btnOriginal.setOnClickListener { onOriginalButtonClicked() }
    }

    override fun setActivityTheme() {
        setFitSystemForTheme(true, ContextCompat.getColor(mContext, R.color.black), false)
    }

    override fun getViewBinding(inflater: LayoutInflater): UikitActivityShowBigImageBinding? {
        return UikitActivityShowBigImageBinding.inflate(inflater)
    }

    /**
     * Default to the thumbnail (requirement 1); the bottom button offers the
     * original image download like iOS.
     */
    private fun setupImageMessage() {
        var msg: ChatMessage? = ChatClient.getInstance().chatManager().getMessage(msgId)
        if (msg == null) {
            msg = intent.getParcelableExtra("msg")
            if (msg == null) {
                ChatLog.e(TAG, "message is null, messageId: $msgId")
                finish()
                return
            }
        }
        val body = msg.body as? ChatImageMessageBody ?: run {
            finish()
            return
        }
        imageMessage = msg
        thumbnailUri = body.thumbnailLocalUri()?.takeIf {
            ChatUIKitFileUtils.isFileExistByUri(this, it)
        }
        val originalExists = ChatUIKitFileUtils.isFileExistByUri(this, body.localUri)
        if (originalExists) {
            // The original is already on disk (sent image or downloaded before).
            showingOriginal = true
            display(body.localUri)
        } else {
            // Requirement 1: default to the thumbnail, and offer the original
            // download with the bottom button.
            if (thumbnailUri != null) {
                display(thumbnailUri)
            } else {
                binding.image.setImageResource(default_res)
            }
            showOriginalButton(R.string.uikit_view_original_image)
        }
    }

    private fun onOriginalButtonClicked() {
        if (showingOriginal) {
            showingOriginal = false
            if (thumbnailUri != null) {
                display(thumbnailUri)
            } else {
                binding.image.setImageResource(default_res)
            }
            showOriginalButton(R.string.uikit_view_original_image)
        } else {
            val body = imageMessage?.body as? ChatImageMessageBody
            val local = body?.localUri
            if (local != null && ChatUIKitFileUtils.isFileExistByUri(this, local)) {
                showingOriginal = true
                display(local)
                showOriginalButton(R.string.uikit_view_thumbnail_image)
            } else {
                downloadOriginalImage()
            }
        }
    }

    private fun downloadOriginalImage() {
        val msg = imageMessage
        if (msg == null || downloading) {
            return
        }
        downloading = true
        isNeedDownload = true
        binding.btnOriginal.isEnabled = false
        binding.pbLoadLocal.visibility = View.VISIBLE
        msg.setMessageStatusCallback(CallbackImpl(
            onSuccess = {
                mContext.mainScope().launch {
                    downloading = false
                    binding.pbLoadLocal.visibility = View.GONE
                    binding.btnOriginal.isEnabled = true
                    // Read a fresh message instance: the SDK updates its own copy
                    // during download while the one we hold may keep a stale or
                    // null localUri, which would leave the screen without image.
                    val fresh = ChatClient.getInstance().chatManager().getMessage(msg.msgId) ?: msg
                    val local = (fresh.body as? ChatImageMessageBody)?.localUri
                    val exists = local != null && ChatUIKitFileUtils.isFileExistByUri(mContext, local)
                    ChatLog.e(TAG, "download original success: local=$local exists=$exists")
                    if (exists && local != null) {
                        imageMessage = fresh
                        showingOriginal = true
                        display(local)
                        showOriginalButton(R.string.uikit_view_thumbnail_image)
                    } else {
                        onDownloadFailed()
                    }
                }
            },
            onError = { code, _ ->
                mContext.mainScope().launch {
                    downloading = false
                    binding.pbLoadLocal.visibility = View.GONE
                    binding.btnOriginal.isEnabled = true
                    if (code == ChatError.FILE_NOT_FOUND) {
                        showToast(R.string.uikit_image_expired)
                    } else {
                        onDownloadFailed()
                    }
                }
            },
            onProgress = { progress ->
                mContext.mainScope().launch {
                    binding.btnOriginal.text =
                        getString(R.string.uikit_downloading_image, progress)
                }
            }
        ))
        ChatLog.d(TAG, "download original image, messageId: ${msg.msgId}")
        ChatClient.getInstance().chatManager().downloadAttachment(msg)
        showOriginalButton(R.string.uikit_downloading_image, 0)
    }

    private fun onDownloadFailed() {
        showOriginalButton(R.string.uikit_view_original_image)
    }

    private fun display(uri: Uri?) {
        displayedUri = uri
        // Reset any pinch/double-tap zoom before swapping the image: the attacher
        // keeps the support matrix across drawables, and a stale zoom/pan can push
        // the freshly loaded image out of the visible area.
        binding.image.setZoomable(false)
        binding.image.setZoomable(true)
        binding.image.load(uri) {
            crossfade(true)
            error(default_res)
        }
    }

    private fun showOriginalButton(textRes: Int, progress: Int? = null) {
        binding.btnOriginal.visibility = View.VISIBLE
        binding.btnOriginal.text = progress?.let { getString(textRes, it) } ?: getString(textRes)
    }

    /**
     * Show custom emoji icon
     * @param emojiIconId emoji id
     */
    private fun showBigExpression(emojiIconId: String?) {
        ChatUIKitClient.getEmojiconInfoProvider()?.getEmojiconInfo(emojiIconId)?.let {
            if (it.bigIcon != -1) {
                binding.image.load(it.bigIcon) {
                    placeholder(R.drawable.uikit_default_expression)
                }
            } else if (!it.bigIconPath.isNullOrEmpty()) {
                binding.image.load(it.bigIconPath) {
                    placeholder(R.drawable.uikit_default_expression)
                }
            } else {
                binding.image.setImageResource(R.drawable.uikit_default_expression)
            }
        }
    }

    // ---------------- Long press: save to gallery ----------------

    private fun currentImageUri(): Uri? {
        // Whatever is on screen (thumbnail, original, or a directly passed file)
        // is what long-press should save.
        if (displayedUri != null) return displayedUri
        if (showingOriginal) {
            (imageMessage?.body as? ChatImageMessageBody)?.localUri?.takeIf {
                ChatUIKitFileUtils.isFileExistByUri(this, it)
            }?.let { return it }
        }
        return thumbnailUri
    }

    private fun showSaveDialog() {
        if (currentImageUri() == null) return
        AlertDialog.Builder(mContext)
            .setItems(arrayOf(getString(R.string.uikit_save_image))) { _, _ ->
                saveImageToGallery()
            }
            .show()
    }

    private fun saveImageToGallery() {
        val uri = currentImageUri() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val saved = runCatching { copyToGallery(uri) }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                showToast(
                    if (saved) R.string.uikit_save_image_success
                    else R.string.uikit_save_image_failed
                )
            }
        }
    }

    private fun copyToGallery(source: Uri): Boolean {
        val resolver = contentResolver
        val mime = resolver.getType(source) ?: "image/jpeg"
        val name = "uikit_${System.currentTimeMillis()}" +
                ".${android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/EaseIM")
            }
            val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(target)?.use { output ->
                resolver.openInputStream(source)?.use { input ->
                    input.copyTo(output)
                }
            } != null
        } else {
            // Legacy path: insert through the media provider, no permission needed.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return false
            MediaStore.Images.Media.insertImage(resolver, bitmap, name, null) != null
        }
    }

    override fun finish() {
        super.finish()
        // Fade-out to close, paired with the fade-in above.
        overridePendingTransition(0, R.anim.uikit_fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isNeedDownload && !msgId.isNullOrEmpty()) {
            ChatUIKitFlowBus.with<ChatUIKitEvent>(ChatUIKitEvent.EVENT.UPDATE.name).post(lifecycleScope,
                ChatUIKitEvent(ChatUIKitEvent.EVENT.UPDATE.name, ChatUIKitEvent.TYPE.MESSAGE, msgId)
            )
        }
    }

    companion object {
        private const val TAG = "ShowBigImage"
        fun actionStart(context: Context, imageUri: Uri?) {
            val intent = Intent(context, ChatUIKitShowBigImageActivity::class.java)
            intent.putExtra("uri", imageUri)
            ChatUIKitClient.getCustomActivityRoute()?.getActivityRoute(intent.clone() as Intent)?.let {
                if (it.hasRoute()) {
                    context.startActivity(it)
                    return
                }
            }
            context.startActivity(intent)
        }

        fun actionStart(context: Context, messageId: String?, filename: String?) {
            val intent = Intent(context, ChatUIKitShowBigImageActivity::class.java)
            intent.putExtra("messageId", messageId)
            intent.putExtra("filename", filename)
            ChatUIKitClient.getCustomActivityRoute()?.getActivityRoute(intent.clone() as Intent)?.let {
                if (it.hasRoute()) {
                    context.startActivity(it)
                    return
                }
            }
            context.startActivity(intent)
        }

        fun actionStart(context: Context, message: ChatMessage?) {
            val intent = Intent(context, ChatUIKitShowBigImageActivity::class.java)
            intent.putExtra("msg", message)
            ChatUIKitClient.getCustomActivityRoute()?.getActivityRoute(intent.clone() as Intent)?.let {
                if (it.hasRoute()) {
                    context.startActivity(it)
                    return
                }
            }
            context.startActivity(intent)
        }
    }
}
