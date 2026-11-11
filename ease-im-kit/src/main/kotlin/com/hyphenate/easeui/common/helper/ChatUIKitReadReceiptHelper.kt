package com.hyphenate.easeui.common.helper

import com.hyphenate.easeui.common.ChatCallback
import com.hyphenate.easeui.common.ChatClient
import com.hyphenate.easeui.common.ChatLog
import com.hyphenate.easeui.common.ChatMessage
import com.hyphenate.easeui.common.ChatMessageDirection
import com.hyphenate.easeui.common.ChatType

/**
 * Single choke point for sending per-message read receipts, aligned with iOS
 * `ChatServiceImplement.markMessageAsRead`: an in-flight set and a sent set
 * guard every send, so rebinds, duplicated receive events and the multiple
 * ack call sites can never ack the same message twice.
 */
object ChatUIKitReadReceiptHelper {
    private const val TAG = "ChatUIKitReadReceiptHelper"
    private val lock = Any()
    private val sendingReadReceipts = mutableSetOf<String>()
    private val sentReadReceipts = mutableSetOf<String>()

    /**
     * Send a read receipt for a received single/group message. Returns true when
     * the send was initiated, false when the message is ineligible or an ack is
     * already in flight or has been sent. A failed send is removed from the
     * in-flight set so the next call may retry.
     */
    fun markMessageAsRead(message: ChatMessage?, callback: ChatCallback? = null): Boolean {
        val msg = message ?: return false
        val msgId = msg.msgId
        if (msgId.isNullOrEmpty()) return false
        if (msg.direct() !== ChatMessageDirection.RECEIVE) return false
        if (msg.chatType !== ChatType.Chat && msg.chatType !== ChatType.GroupChat) return false
        if (!msg.isNeedReadReceipt) return false
        synchronized(lock) {
            if (sendingReadReceipts.contains(msgId) || sentReadReceipts.contains(msgId)) {
                return false
            }
            sendingReadReceipts.add(msgId)
        }
        ChatClient.getInstance().chatManager().asyncSendMessageReadReceipts(
            listOf(msg), object : ChatCallback {
                override fun onSuccess() {
                    synchronized(lock) {
                        sendingReadReceipts.remove(msgId)
                        sentReadReceipts.add(msgId)
                    }
                    callback?.onSuccess()
                }

                override fun onError(code: Int, error: String?) {
                    ChatLog.e(TAG, "send read receipt failed: $code, $error")
                    synchronized(lock) {
                        sendingReadReceipts.remove(msgId)
                    }
                    callback?.onError(code, error)
                }

                override fun onProgress(progress: Int, status: String?) {
                    callback?.onProgress(progress, status)
                }
            })
        return true
    }
}
