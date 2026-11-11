package com.hyphenate.easeui.widget.chatrow

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView
import coil.dispose
import com.hyphenate.easeui.R
import com.hyphenate.easeui.common.ChatClient
import com.hyphenate.easeui.common.ChatDownloadStatus
import com.hyphenate.easeui.common.ChatImageMessageBody
import com.hyphenate.easeui.common.ChatLog
import com.hyphenate.easeui.common.ChatMessage
import com.hyphenate.easeui.common.ChatMessageStatus
import com.hyphenate.easeui.common.extensions.getImageShowSize
import com.hyphenate.easeui.common.extensions.getPlaceholderShowSize
import com.hyphenate.easeui.common.extensions.isFileExist
import com.hyphenate.easeui.common.extensions.isSend
import com.hyphenate.easeui.common.extensions.isSuccess
import com.hyphenate.easeui.common.extensions.loadImageFromMessage
import com.hyphenate.easeui.common.extensions.mainScope
import com.hyphenate.easeui.common.extensions.setLayoutSize
import com.hyphenate.easeui.common.utils.ChatUIKitFileUtils
import com.hyphenate.easeui.feature.chat.UIKitChatFragment.Companion.retryTimes
import com.hyphenate.util.EMLog
import kotlinx.coroutines.launch

/**
 * image for row
 */
open class ChatUIKitRowImage @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyleAttr: Int = 0,
    isSender: Boolean = false
) : ChatUIKitRowFile(context, attrs, defStyleAttr, isSender) {
    protected val imageView: ImageView? by lazy { findViewById(R.id.image) }

    override fun onInflateView() {
        inflater.inflate(
            if (!isSender) R.layout.uikit_row_received_picture else R.layout.uikit_row_sent_picture,
            this
        )
    }

    override fun onSetUpView() {
        bubbleLayout?.background = null
        message?.run {
            (body as? ChatImageMessageBody)?.let {
                if (isSuccess() && it.thumbnailDownloadStatus() == ChatDownloadStatus.DOWNLOADING) {
                    setMessageDownloadCallback(true)
                }
                if (ChatUIKitFileUtils.isFileExistByUri(context, it.localUri)
                    || ChatUIKitFileUtils.isFileExistByUri(context, it.thumbnailLocalUri())) {
                    showImageView(this, position)
                    return
                }
                imageView?.dispose()
                checkAttachmentStatus(position)
            }
        }
    }

    private fun checkAttachmentStatus(position: Int) {
        message?.run {
            showPlaceholderSize(this)
            // If auto transfer message attachments to Chat Server, then download attachments
            if (ChatClient.getInstance().options.autoTransferMessageAttachments) {
                val imgBody = body as ChatImageMessageBody
                // received messages
                if (!isSend()) {
                    if (ChatClient.getInstance().options.autodownloadThumbnail) {
                        if (imgBody.thumbnailDownloadStatus() === ChatDownloadStatus.SUCCESSED) {
                            if (imgBody.thumbnailLocalUri()?.isFileExist(context) == true) {
                                showSuccessStatus()
                                showImageView(this, position)
                                return
                            }
                        } else if (imgBody.thumbnailDownloadStatus() === ChatDownloadStatus.DOWNLOADING) {
                            // Listener to ChatMessageListener#onMessageChanged, then update the UI.
                            // Note: If you set the method ChatMessage#setMessageStatusCallback,
                            // ChatMessageListener#onMessageChanged will not be called.
                            showInProgressStatus()
                            return
                        }
                    }
                }
                if (status() != ChatMessageStatus.SUCCESS) {
                    return
                }
                if (imgBody.downloadStatus() == ChatDownloadStatus.SUCCESSED
                    || imgBody.thumbnailDownloadStatus() == ChatDownloadStatus.SUCCESSED
                ) {
                    if (ChatUIKitFileUtils.isFileExistByUri(context, imgBody.localUri)
                        || ChatUIKitFileUtils.isFileExistByUri(context, imgBody.thumbnailLocalUri())) {
                        showSuccessStatus()
                        showImageView(this, position)
                        return
                    }
                } else if (imgBody.downloadStatus() == ChatDownloadStatus.DOWNLOADING
                    || imgBody.thumbnailDownloadStatus() == ChatDownloadStatus.DOWNLOADING
                ) {
                    return
                }
                if (isInRetrying) {
                    downloadAttachment(!ChatUIKitFileUtils.isFileExistByUri(context, imgBody.thumbnailLocalUri()) && !isSend())
                }
            } else {
                showImageView(this, position)
            }
        }
    }

    private fun showPlaceholderSize(message: ChatMessage) {
        imageView?.setImageResource(R.drawable.uikit_default_image)
        message.getPlaceholderShowSize(context)?.let {
            imageView?.setLayoutSize(it.width, it.height)
        } ?: run {
            imageView?.setLayoutSize(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private val isInRetrying: Boolean
        get() {
            val msgId = message?.msgId ?: return false
            val times = retryTimes.getOrPut(msgId) { 3 }
            retryTimes[msgId] = times - 1
            EMLog.d("ChatUIKitRowImage", "isInRetrying: $times")
            return times > 0
        }

    override fun onMessageSuccess() {
        super.onMessageSuccess()
        //Even if it's the sender, it needs to be executed after
        // it's successfully sent to prevent the image size from being wrong
        message?.body?.let {
            val imgBody = it as ChatImageMessageBody
            if (imgBody.thumbnailDownloadStatus() == ChatDownloadStatus.SUCCESSED
                || imgBody.thumbnailDownloadStatus() == ChatDownloadStatus.FAILED) {
                showImageView(message, position)
            }
        }

    }

    override fun onMessageInProgress() {
        message?.run {
            if (isSend()) {
                super.onMessageInProgress()
            } else {
                if (ChatClient.getInstance().options.autodownloadThumbnail) {
                    //imageView.setImageResource(R.drawable.uikit_default_image);
                } else {
                    progressBar?.visibility = INVISIBLE
                }
            }
        }
    }

    override fun onDownloadAttachmentSuccess() {
        showSuccessStatus()
        showImageView(message, position)
    }

    override fun onDownloadAttachmentError(code: Int, error: String?) {
        ChatLog.e(
            ChatUIKitRowImage::class.java.simpleName,
            "onDownloadAttachmentError:$code, error:$error"
        )
        context.mainScope().launch {
            imageLoadInFlight = false
            imageLoadedMessage = null
            imageView?.setLayoutSize(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            imageView?.setImageResource(R.drawable.uikit_default_image)
        }
    }

    override fun onDownloadAttachmentProgress(progress: Int) {
        showInProgressStatus()
    }

    /**
     * The message whose image is currently displayed (or loading) in this row
     * instance. Compared by object identity first; on send success the SDK may
     * hand out a NEW message instance whose msgId/localUri/remoteUrl all changed
     * (the file is moved to the attachment directory), so [sameLogicalMessage]
     * (localTime + from + msgId) is used as the fallback identity: the image
     * content of a logical message never changes, and reloading it would only
     * flash the view.
     */
    @SuppressLint("StaticFieldLeak")
    private var imageLoadedMessage: ChatMessage? = null
    private var imageLoadInFlight = false

    private fun sameLogicalMessage(a: ChatMessage?, b: ChatMessage?): Boolean {
        if (a == null || b == null) return false
        return a === b || (a.localTime() == b.localTime()
                && a.from == b.from) || a.msgId == b.msgId
    }

    protected fun showImageView(message: ChatMessage?, position: Int) {
        if (position == this.position && TextUtils.equals(
                message?.msgId,
                this.message?.msgId
            )
        ) {
            val drawableShown = imageView?.drawable != null
            if (message != null && sameLogicalMessage(imageLoadedMessage, message)
                && (imageLoadInFlight || drawableShown)
            ) {
                // Already showing (or loading) this logical message's image:
                // keep it, just track the newest instance.
                imageLoadedMessage = message
                return
            }
            if (!sameLogicalMessage(imageLoadedMessage, message)) {
                // The row was recycled and bound to another message: reset to the
                // placeholder first, otherwise the previous message's image stays
                // on the view (Coil also uses the current drawable as placeholder)
                // and the wrong cover is visible while the new image decodes.
                imageLoadedMessage = null
                imageLoadInFlight = false
                imageView?.dispose()
                imageView?.setImageResource(R.drawable.uikit_default_image)
            }
            imageLoadInFlight = true
            imageLoadedMessage = message
            imageView?.loadImageFromMessage(message, onSuccess = {
                imageLoadInFlight = false
                imageLoadedMessage = message
            }, onError = {
                imageLoadInFlight = false
                imageLoadedMessage = null
            })
        }
    }
}