package com.hyphenate.easeui.feature.chat.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hyphenate.easeui.ChatUIKitClient
import com.hyphenate.easeui.R
import com.hyphenate.easeui.base.ChatUIKitBaseActivity
import com.hyphenate.easeui.common.ChatClient
import com.hyphenate.easeui.common.ChatCursorResult
import com.hyphenate.easeui.common.ChatFileMessageBody
import com.hyphenate.easeui.common.ChatGroupMemberInfo
import com.hyphenate.easeui.common.ChatGroupReadReceipt
import com.hyphenate.easeui.common.ChatLog
import com.hyphenate.easeui.common.ChatMessage
import com.hyphenate.easeui.common.ChatMessageType
import com.hyphenate.easeui.common.ChatTextMessageBody
import com.hyphenate.easeui.common.ChatType
import com.hyphenate.easeui.common.ChatValueCallback
import com.hyphenate.easeui.common.extensions.hasRoute
import com.hyphenate.easeui.databinding.UikitActivityGroupReadDetailBinding
import com.hyphenate.easeui.model.ChatUIKitProfile
import com.hyphenate.easeui.widget.ChatUIKitImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Group message read-receipt details, opened by tapping the read-status mark
 * beside an outgoing group message. Shows who has read and who has not, with
 * the same behavior as iOS: paginated readers, full member roster, pull to
 * refresh semantics via the retry button.
 */
open class ChatUIKitGroupReadDetailActivity :
    ChatUIKitBaseActivity<UikitActivityGroupReadDetailBinding>() {

    data class Member(
        val id: String,
        var name: String,
        var avatar: String,
        val joinTime: Long,
    )

    private lateinit var message: ChatMessage
    private lateinit var groupId: String
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val adapter = MembersAdapter()

    private var showingUnread = false
    private var generation = 0
    private val readers = LinkedHashMap<String, Member>()
    private val members = LinkedHashMap<String, Member>()
    private var readersLoading = false
    private var membersLoading = false
    private var readersError: String? = null
    private var membersError: String? = null
    private var readCount = 0
    private var recipientTotal: Int? = null

    override fun getViewBinding(inflater: LayoutInflater): UikitActivityGroupReadDetailBinding {
        return UikitActivityGroupReadDetailBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val msgId = intent.getStringExtra(EXTRA_MSG_ID).orEmpty()
        val msg = ChatClient.getInstance().chatManager().getMessage(msgId)
        if (msg == null || msg.chatType !== ChatType.GroupChat) {
            finish()
            return
        }
        message = msg
        groupId = msg.conversationId()
        readCount = msg.readReceiptCount()

        binding.titleBar.setDisplayHomeAsUpEnabled(true)
        binding.titleBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = adapter

        binding.tvTabRead.setOnClickListener {
            showingUnread = false
            refresh()
        }
        binding.tvTabUnread.setOnClickListener {
            showingUnread = true
            refresh()
        }
        binding.btnRetry.setOnClickListener { reload() }

        setupHeader()
        reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    private fun setupHeader() {
        val profile = ChatUIKitProfile.getGroupMember(groupId, message.from)
        binding.ivSenderAvatar.setShapeType(ChatUIKitImageView.ShapeType.ROUND)
        binding.ivSenderAvatar.setRadius(resources.getDimensionPixelSize(R.dimen.ease_size_14))
        binding.ivSenderAvatar.load(profile?.avatar) {
            placeholder(R.drawable.uikit_default_avatar)
            error(R.drawable.uikit_default_avatar)
        }
        binding.tvSenderName.text = profile?.getRemarkOrName() ?: message.from
        binding.tvSendTime.text = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(message.msgTime))
        binding.tvMessageSummary.text = summaryOf(message)
    }

    private fun summaryOf(msg: ChatMessage): String {
        return when (msg.type) {
            ChatMessageType.TXT -> (msg.body as? ChatTextMessageBody)?.message ?: ""
            ChatMessageType.FILE -> (msg.body as? ChatFileMessageBody)?.fileName ?: ""
            ChatMessageType.IMAGE -> getString(R.string.uikit_group_read_summary_photo)
            ChatMessageType.VIDEO -> getString(R.string.uikit_group_read_summary_video)
            ChatMessageType.VOICE -> getString(R.string.uikit_group_read_summary_voice)
            ChatMessageType.LOCATION -> getString(R.string.uikit_group_read_summary_location)
            ChatMessageType.CMD -> getString(R.string.uikit_group_read_summary_cmd)
            else -> getString(R.string.uikit_group_read_summary_custom)
        }
    }

    private fun reload() {
        val version = ++generation
        readersLoading = true
        membersLoading = true
        readersError = null
        membersError = null
        readers.clear()
        members.clear()
        refresh()
        uiScope.launch {
            refreshGroup(version)
            loadReaders(version)
            loadMembers(version)
        }
    }

    private suspend fun refreshGroup(version: Int) {
        runCatching {
            suspendCancellableCoroutine { cont ->
                ChatClient.getInstance().groupManager()
                    .asyncGetGroupFromServer(groupId, object : ChatValueCallback<com.hyphenate.chat.EMGroup> {
                        override fun onSuccess(value: com.hyphenate.chat.EMGroup) {
                            cont.resume(value)
                        }

                        override fun onError(error: Int, errorMsg: String?) {
                            cont.resumeWithException(Exception("fetch group failed: $error $errorMsg"))
                        }
                    })
            }
        }.onSuccess { group ->
            if (generation != version) return
            // Members excluding the sender are the potential recipients.
            val occupants = group.memberCount
            recipientTotal = if (occupants > 0) occupants - 1 else null
            refresh()
        }
    }

    private suspend fun loadReaders(version: Int) {
        var cursor = ""
        val visited = mutableSetOf<String>()
        while (true) {
            try {
                val page = fetchReadReceipts(message.msgId, cursor, 20)
                if (generation != version) return
                page.data?.forEach { receipt ->
                    val info = receipt.from
                    val id = info?.userId.orEmpty()
                    if (id.isNotEmpty() && id != message.from) {
                        readers[id] = resolveMember(id, info?.namecard, info?.avatarUrl, info?.nickname, 0)
                    }
                }
                readCount = maxOf(readCount, readers.size, message.readReceiptCount())
                val next = page.cursor.orEmpty()
                if (next.isEmpty()) {
                    readersLoading = false
                    break
                } else if (next == cursor || !visited.add(next)) {
                    readersError = getString(R.string.uikit_group_read_load_failed)
                    readersLoading = false
                    break
                }
                cursor = next
                refresh()
            } catch (e: Exception) {
                if (generation != version) return
                ChatLog.e(TAG, "load readers failed: ${e.message}")
                readersError = e.message
                readersLoading = false
                break
            }
        }
        refresh()
    }

    private suspend fun loadMembers(version: Int) {
        var cursor = ""
        val visited = mutableSetOf<String>()
        while (true) {
            try {
                val page = fetchMembers(groupId, cursor, 100)
                if (generation != version) return
                page.data?.forEach { info ->
                    val id = info.userId.orEmpty()
                    if (id.isNotEmpty()) {
                        members[id] = resolveMember(id, info.namecard, info.avatarUrl, info.nickname, info.joinTime)
                    }
                }
                val next = page.cursor.orEmpty()
                if (next.isEmpty()) {
                    membersLoading = false
                    break
                } else if (next == cursor || !visited.add(next)) {
                    membersError = getString(R.string.uikit_group_read_load_failed)
                    membersLoading = false
                    break
                }
                cursor = next
            } catch (e: Exception) {
                if (generation != version) return
                ChatLog.e(TAG, "load members failed: ${e.message}")
                membersError = e.message
                membersLoading = false
                break
            }
        }
        refresh()
    }

    /**
     * Resolve the display name and avatar for a member, aligned with iOS:
     * namecard > uikit profile (remark/name) > member nickname > id.
     */
    private fun resolveMember(
        id: String,
        namecard: String?,
        avatarUrl: String?,
        nickname: String?,
        joinTime: Long,
    ): Member {
        val profile = ChatUIKitProfile.getGroupMember(groupId, id)
        val name = namecard?.takeIf { it.isNotEmpty() }
            ?: profile?.getRemarkOrName()?.takeIf { it.isNotEmpty() && it != id }
            ?: nickname?.takeIf { it.isNotEmpty() }
            ?: profile?.name?.takeIf { it.isNotEmpty() }
            ?: id
        val avatar = avatarUrl?.takeIf { it.isNotEmpty() } ?: profile?.avatar.orEmpty()
        return Member(id, name, avatar, joinTime)
    }

    private fun currentPeople(): List<Member> {
        return if (showingUnread) unreadPeople() else sorted(readers)
    }

    private fun unreadPeople(): List<Member> {
        if (readersLoading || membersLoading || readersError != null || membersError != null) {
            return emptyList()
        }
        val targets = message.receiverList().orEmpty().toSet()
        val sent = message.msgTime
        return sorted(members.filter { (id, member) ->
            id != message.from && !readers.containsKey(id)
                    && (targets.isEmpty() || targets.contains(id))
                    && (member.joinTime <= 0L || member.joinTime <= sent)
        })
    }

    private fun sorted(map: Map<String, Member>): List<Member> =
        map.values.sortedWith(compareBy({ it.name }, { it.id }))

    private fun refresh() {
        binding.tvTabRead.text = getString(R.string.uikit_group_read_tab_read, readCount)
        val total = recipientTotal
        binding.tvTabUnread.text = if (total != null) {
            getString(R.string.uikit_group_read_tab_unread, maxOf(0, total - readCount))
        } else {
            getString(R.string.uikit_group_read_tab_unread_unknown)
        }
        val active = getColor(R.color.ease_color_on_background)
        val inactive = getColor(R.color.ease_color_on_background_low)
        binding.tvTabRead.setTextColor(if (showingUnread) inactive else active)
        binding.tvTabUnread.setTextColor(if (showingUnread) active else inactive)
        positionIndicator()

        val loading = if (showingUnread) readersLoading || membersLoading else readersLoading
        val error = if (showingUnread) readersError ?: membersError else readersError
        val people = currentPeople()
        binding.loading.visibility = if (loading && people.isEmpty()) View.VISIBLE else View.GONE
        binding.btnRetry.visibility = if (!loading && error != null) View.VISIBLE else View.GONE
        val showEmpty = !loading && error == null && people.isEmpty()
        binding.tvState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        if (error != null) {
            binding.tvState.visibility = View.VISIBLE
            binding.tvState.text = getString(R.string.uikit_group_read_load_failed)
        } else if (showEmpty) {
            binding.tvState.text = getString(
                if (showingUnread) R.string.uikit_group_read_empty_unread
                else R.string.uikit_group_read_empty_read
            )
        }
        adapter.submit(people)
    }

    /**
     * Keep the indicator line horizontally centered under the active tab.
     */
    private fun positionIndicator() {
        binding.viewIndicator.post {
            val readWidth = binding.tvTabRead.width
            val activeWidth = if (showingUnread) binding.tvTabUnread.width else readWidth
            val tabLeft = if (showingUnread) readWidth.toFloat() else 0f
            binding.viewIndicator.translationX = tabLeft + (activeWidth - binding.viewIndicator.width) / 2f
        }
    }

    private suspend fun fetchReadReceipts(
        msgId: String,
        cursor: String,
        pageSize: Int,
    ): ChatCursorResult<ChatGroupReadReceipt> = suspendCancellableCoroutine { cont ->
        ChatClient.getInstance().chatManager()
            .asyncFetchGroupMessageReadReceipts(msgId, pageSize, cursor,
                object : ChatValueCallback<ChatCursorResult<ChatGroupReadReceipt>> {
                    override fun onSuccess(value: ChatCursorResult<ChatGroupReadReceipt>) {
                        cont.resume(value)
                    }

                    override fun onError(error: Int, errorMsg: String?) {
                        cont.resumeWithException(Exception("fetch readers failed: $error $errorMsg"))
                    }
                })
    }

    private suspend fun fetchMembers(
        groupId: String,
        cursor: String,
        pageSize: Int,
    ): ChatCursorResult<ChatGroupMemberInfo> = suspendCancellableCoroutine { cont ->
        ChatClient.getInstance().groupManager()
            .asyncFetchGroupMembersInfo(groupId, cursor, pageSize,
                object : ChatValueCallback<ChatCursorResult<ChatGroupMemberInfo>> {
                    override fun onSuccess(value: ChatCursorResult<ChatGroupMemberInfo>) {
                        cont.resume(value)
                    }

                    override fun onError(error: Int, errorMsg: String?) {
                        cont.resumeWithException(Exception("fetch members failed: $error $errorMsg"))
                    }
                })
    }

    private inner class MembersAdapter : RecyclerView.Adapter<MemberHolder>() {
        private val data = mutableListOf<Member>()

        fun submit(list: List<Member>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberHolder {
            return MemberHolder(layoutInflater.inflate(R.layout.uikit_row_group_read_member, parent, false))
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: MemberHolder, position: Int) {
            holder.bind(data[position])
        }
    }

    private inner class MemberHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ChatUIKitImageView = itemView.findViewById(R.id.iv_avatar)
        private val name: TextView = itemView.findViewById(R.id.tv_name)

        fun bind(member: Member) {
            avatar.setShapeType(ChatUIKitImageView.ShapeType.ROUND)
            avatar.setRadius(itemView.resources.getDimensionPixelSize(R.dimen.ease_size_20))
            avatar.load(member.avatar) {
                placeholder(R.drawable.uikit_default_avatar)
                error(R.drawable.uikit_default_avatar)
            }
            name.text = member.name
        }
    }

    companion object {
        private const val TAG = "GroupReadDetail"
        private const val EXTRA_MSG_ID = "msg_id"

        @JvmStatic
        fun actionStart(context: Context, msgId: String?) {
            if (msgId.isNullOrEmpty()) return
            Intent(context, ChatUIKitGroupReadDetailActivity::class.java).apply {
                putExtra(EXTRA_MSG_ID, msgId)
                ChatUIKitClient.getCustomActivityRoute()?.getActivityRoute(this.clone() as Intent)?.let {
                    if (it.hasRoute()) {
                        context.startActivity(it)
                        return
                    }
                }
                context.startActivity(this)
            }
        }
    }
}
