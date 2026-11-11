package com.hyphenate.easeui.widget.chatrow

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hyphenate.easeui.R
import com.hyphenate.easeui.common.ChatClient
import com.hyphenate.easeui.common.ChatMessage
import com.hyphenate.easeui.common.ChatMessageStatus
import com.hyphenate.easeui.common.ChatType
import com.hyphenate.easeui.common.extensions.addChildView
import com.hyphenate.easeui.common.extensions.getTheSameTypeChild
import com.hyphenate.easeui.common.extensions.isFail
import com.hyphenate.easeui.common.extensions.isSend
import com.hyphenate.easeui.common.extensions.isSuccess
import com.hyphenate.easeui.common.extensions.loadAvatar
import com.hyphenate.easeui.common.extensions.loadNickname
import com.hyphenate.easeui.common.helper.DateFormatHelper
import com.hyphenate.easeui.feature.chat.config.ChatUIKitMessageItemConfig
import com.hyphenate.easeui.feature.chat.config.resetBubbleBackground
import com.hyphenate.easeui.feature.chat.config.setAvatarConfig
import com.hyphenate.easeui.feature.chat.config.setNicknameConfig
import com.hyphenate.easeui.feature.chat.config.setTextMessageMinHeight
import com.hyphenate.easeui.feature.chat.config.setTextMessageTextConfigs
import com.hyphenate.easeui.feature.chat.config.setTimeTextConfig
import com.hyphenate.easeui.feature.chat.forward.helper.ChatUIKitMessageMultiSelectHelper
import com.hyphenate.easeui.feature.chat.interfaces.OnItemBubbleClickListener
import com.hyphenate.easeui.feature.chat.interfaces.OnMessageListItemClickListener

abstract class ChatUIKitRow @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyle: Int = 0,
    val isSender: Boolean
): FrameLayout(context, attrs, defStyle) {

    val inflater by lazy { LayoutInflater.from(context) }

    val timeStampView: TextView? by lazy { findViewById(R.id.timestamp) }
    val userAvatarView: ImageView? by lazy { findViewById(R.id.iv_userhead) }
    val bubbleLayout: ViewGroup? by lazy { findViewById(R.id.bubble) }
    val usernickView: TextView? by lazy { findViewById(R.id.tv_userid) }
    val progressBar: ProgressBar? by lazy { findViewById(R.id.progress_bar) }
    val statusView: ImageView? by lazy { findViewById(R.id.msg_status) }
    val ackedView: TextView? by lazy { findViewById(R.id.tv_ack) }
    val deliveredView: TextView? by lazy { findViewById(R.id.tv_delivered) }
    val selectRadio: RadioButton? by lazy { findViewById(R.id.rb_select) }
    val editView: TextView? by lazy { findViewById(R.id.tv_edit) }
    val llBottomBubble: LinearLayout? by lazy { findViewById(R.id.ll_bottom_bubble) }
    protected val llTopBubble: LinearLayout? by lazy { findViewById(R.id.ll_top_bubble) }
    protected val llBubbleBottom: LinearLayout? by lazy { findViewById(R.id.ll_bubble_bottom) }

    var message: ChatMessage? = null
    var position: Int = -1

    var itemClickListener: OnMessageListItemClickListener? = null
    private var onItemBubbleClickListener: OnItemBubbleClickListener? = null

    private var messageItemConfig: ChatUIKitMessageItemConfig? = null

    init {
        onInflateView()
        onFindViewById()
    }

    /**
     * Bind data to view.
     * The method is called by ViewHolder.
     */
    fun setUpView(message: ChatMessage?, position: Int) {
        this.message = message
        this.position = position
        setUpBaseView()
        onSetUpView()
    }

    fun updateView() {
        updateMessageByStatus()
    }

    /**
     * Calls by ViewHolder.
     * Show a centered time divider above the message according to the gap
     * between this message and the previous one.
     */
    fun setTimestamp(preMessage: ChatMessage?) {
        if (preMessage == null) {
            showTimeDivider()
            return
        }
        message?.let { msg ->
            val isCloseEnough = if (ChatClient.getInstance().options.isSortMessageByServerTime) {
                DateFormatHelper.isCloseEnough(msg.msgTime, preMessage.msgTime)
            } else {
                DateFormatHelper.isCloseEnough(msg.localTime(), preMessage.localTime())
            }
            if (isCloseEnough) {
                timeStampView?.visibility = View.GONE
            } else {
                setOtherTimestamp(preMessage)
            }
        } ?: run {
            timeStampView?.visibility = View.GONE
        }
    }

    /**
     * Add child view to bottom bubble layout.
     */
    fun addChildToBottomBubbleLayout(child: View?,index:Int? = null) {
        llBottomBubble?.let {
            if (index != null){
                it.addChildView(child,index)
            }else{
                it.addChildView(child)
            }
        }
    }

    /**
     * Get the same type child view.
     */
    fun <T: View> getTargetTypeChildView(type: Class<T>): View? {
        var view = llBottomBubble?.getTheSameTypeChild(type)
        if (view == null) {
            view = llBubbleBottom?.getTheSameTypeChild(type)
        }
        if (view == null) {
            view = llTopBubble?.getTheSameTypeChild(type)
        }
        return view
    }

    private fun setUpBaseView() {
        setTimestamp(message)
        setAvatarAndNickname()
        setChatRowConfig()
        updateMessageStatus()
        initView()
        initListener()
    }

    private fun initView() {
        selectRadio?.let {
            it.visibility = if (ChatUIKitMessageMultiSelectHelper.getInstance().isMultiStyle(context, message?.conversationId())) VISIBLE else GONE
            it.isChecked = ChatUIKitMessageMultiSelectHelper.getInstance().isContainsMessage(context, message)
        }
        message?.run {
            editView?.let {
                it.visibility = if (body != null && body.operationCount() > 0) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateMessageStatus() {
        updateMessageByStatus()
        updateSendMessageStatus()
    }

    private fun updateMessageByStatus() {
        message?.run {
            when(status()) {
                ChatMessageStatus.CREATE -> {
                    // When get local messages and check it's status, change the create status to fail.
                }
                ChatMessageStatus.INPROGRESS -> {
                    onMessageInProgress()
                }
                ChatMessageStatus.SUCCESS -> {
                    onMessageSuccess()
                }
                ChatMessageStatus.FAIL -> {
                    onMessageError()
                }
            }
        }
    }

    private fun updateSendMessageStatus() {
        message?.run {
            if (isSend()) {
                // The delivered tick is no longer shown, read status is displayed instead.
                deliveredView?.visibility = View.GONE
                ackedView?.let { ack ->
                    if (!isSuccess()) {
                        ack.visibility = View.INVISIBLE
                        return@let
                    }
                    if (chatType === ChatType.GroupChat) {
                        showGroupReadStatus(this, ack)
                    } else {
                        showSingleReadStatus(this, ack)
                    }
                }
                if (isSuccess()) {
                    showSuccessStatus()
                }
                // update error status
                setSendMessageFailStatus()
            }
        }
    }

    /**
     * Show the read status of a single chat message with the same capsule
     * style as group chat, but without a number:
     * hollow blue capsule for unread, gray circle with a check for read.
     */
    private fun showSingleReadStatus(msg: ChatMessage, ack: TextView) {
        if (msg.isPeerRead) {
            ack.setBackgroundResource(R.drawable.uikit_msg_status_all_read)
        } else {
            val color = context.resources.getColor(R.color.ease_chat_read_receipt_color, context.theme)
            val pill = ContextCompat.getDrawable(context, R.drawable.uikit_msg_status_read_circle)
                ?.mutate() as? GradientDrawable
            pill?.setStroke(context.resources.getDimensionPixelSize(R.dimen.ease_size_1), color)
            ack.background = pill
        }
        ack.text = ""
        ack.visibility = View.VISIBLE
    }

    /**
     * Show the read status of a group chat message with a capsule style:
     * hollow capsule for 0 read, number for partial read, check mark for all read.
     * All read is shown in gray, otherwise in blue.
     */
    private fun showGroupReadStatus(msg: ChatMessage, ack: TextView) {
        if (!msg.isNeedReadReceipt) {
            ack.visibility = View.INVISIBLE
            return
        }
        val group = runCatching {
            ChatClient.getInstance().groupManager().getGroup(msg.conversationId())
        }.getOrNull()
        // Total readers = group members excluding the sender.
        val total = (group?.memberCount ?: 0) - 1
        if (total <= 0) {
            ack.visibility = View.INVISIBLE
            return
        }
        val count = msg.readReceiptCount()
        val allRead = count >= total && total > 0
        if (allRead) {
            // All read: thin gray circle with a check mark inside, same as iOS.
            ack.setBackgroundResource(R.drawable.uikit_msg_status_all_read)
            ack.text = ""
            ack.visibility = View.VISIBLE
            return
        }
        val color = context.resources.getColor(R.color.ease_chat_read_receipt_color, context.theme)
        val pill = ContextCompat.getDrawable(context, R.drawable.uikit_msg_status_read_circle)
            ?.mutate() as? GradientDrawable
        pill?.setStroke(context.resources.getDimensionPixelSize(R.dimen.ease_size_1), color)
        ack.background = pill
        ack.setTextColor(color)
        ack.text = if (count > 0) count.toString() else ""
        ack.visibility = View.VISIBLE
    }

    private fun updateMessageErrorStatus() {
        ackedView?.visibility = View.INVISIBLE
        deliveredView?.visibility = View.INVISIBLE
        editView?.visibility = View.GONE
        showErrorStatus()
    }

    fun setSendMessageFailStatus() {
        message?.run {
            if (isSend() && isFail()) {
                statusView?.visibility = View.VISIBLE
            }
        }
    }

    fun setItemConfig(messageItemConfig: ChatUIKitMessageItemConfig?) {
        this.messageItemConfig = messageItemConfig
    }

    private fun setChatRowConfig() {
        messageItemConfig?.let { config ->
            config.resetBubbleBackground(bubbleLayout, message?.isSend() == true)
            config.setTimeTextConfig(timeStampView)
            config.setTextMessageTextConfigs(findViewById(R.id.tv_chatcontent))
            config.setAvatarConfig(userAvatarView, message?.isSend() == true)
            config.setNicknameConfig(usernickView, message?.isSend() == true)
            config.setTextMessageMinHeight(bubbleLayout)
        }
    }

    /**
     * All user info is from message ext.
     */
    private fun setAvatarAndNickname() {
        message?.run {
            userAvatarView?.loadAvatar(this)
            usernickView?.loadNickname(this)
        }
    }

    open fun setOtherTimestamp(preMessage: ChatMessage?) {
        showTimeDivider()
    }

    private fun showTimeDivider() {
        message?.let { msg ->
            val time = if (ChatClient.getInstance().options.isSortMessageByServerTime) {
                msg.msgTime
            } else {
                msg.localTime()
            }
            timeStampView?.text = DateFormatHelper.getTimeDividerString(context, time)
            timeStampView?.visibility = VISIBLE
        } ?: run {
            timeStampView?.visibility = View.GONE
        }
    }

    private fun initListener() {
        bubbleLayout?.let {
            it.setOnClickListener {
                if (itemClickListener?.onBubbleClick(message) == true) {
                    return@setOnClickListener
                }
                onItemBubbleClickListener?.onBubbleClick(message)
            }
            it.setOnLongClickListener { view ->
                return@setOnLongClickListener itemClickListener?.onBubbleLongClick(view, message) == true
            }
        }
        statusView?.let {
            it.setOnClickListener {
                itemClickListener?.onResendClick(message)
            }
        }
        ackedView?.let {
            it.setOnClickListener {
                message?.let { msg ->
                    // Only sent group messages show the read-receipt entry.
                    if (isSend() && msg.chatType === ChatType.GroupChat
                        && msg.status() == ChatMessageStatus.SUCCESS) {
                        itemClickListener?.onReadReceiptClick(msg)
                    }
                }
            }
        }
        userAvatarView?.let {
            it.setOnClickListener {
                message?.run {
                    itemClickListener?.onUserAvatarClick(if (isSend()) ChatClient.getInstance().currentUser else from)
                }
            }
            it.setOnLongClickListener {
                message?.run {
                    if (itemClickListener != null) {
                        itemClickListener?.onUserAvatarLongClick(if (isSend()) ChatClient.getInstance().currentUser else from)
                        return@setOnLongClickListener true
                    }
                }
                return@setOnLongClickListener false
            }
        }
        selectRadio?.let { radio ->
            if (ChatUIKitMessageMultiSelectHelper.getInstance().isMultiStyle(context, message?.conversationId())) {
                setOnClickListener {
                    val checked = radio.isChecked
                    radio.isChecked = !checked
                    if (radio.isChecked) {
                        ChatUIKitMessageMultiSelectHelper.getInstance().addChatMessage(context, message)
                    } else {
                        ChatUIKitMessageMultiSelectHelper.getInstance().removeChatMessage(context, message)
                    }
                }
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ChatUIKitMessageMultiSelectHelper.getInstance().isMultiStyle(context, message?.conversationId())) {
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * Show success status.
     */
    protected open fun showSuccessStatus() {
        progressBar?.visibility = INVISIBLE
        statusView?.visibility = INVISIBLE
    }

    protected open fun showErrorStatus() {
        progressBar?.visibility = INVISIBLE
        statusView?.visibility = VISIBLE
    }

    protected open fun showInProgressStatus() {
        progressBar?.visibility = VISIBLE
        statusView?.visibility = INVISIBLE
    }
    protected open fun onMessageSuccess() {
        updateSendMessageStatus()
    }

    protected open fun onMessageError() {
        updateMessageErrorStatus()
    }

    protected open fun onMessageInProgress() {
        showInProgressStatus()
    }

    protected open fun isSend():Boolean {
        return isSender
    }

    /**
     * Set message item click listeners.
     * @param listener
     */
    fun setOnMessageListItemClickListener(listener: OnMessageListItemClickListener?) {
        itemClickListener = listener
    }

    fun setOnItemBubbleClickListener(listener: OnItemBubbleClickListener?) {
        this.onItemBubbleClickListener = listener
    }

    /**
     * Override it and inflate your view in this method.
     */
    abstract fun onInflateView()

    /**
     * Override it and find view by id in this method.
     */
    open fun onFindViewById() {}

    /**
     * Override it and set data or listener in this method.
     */
    abstract fun onSetUpView()

    companion object {
        const val TAG = "ChatUIKitRow"
    }

}