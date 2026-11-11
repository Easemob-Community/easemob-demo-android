package com.hyphenate.easeui.common.extensions

import com.hyphenate.easeui.ChatUIKitClient
import com.hyphenate.easeui.R
import com.hyphenate.easeui.common.ChatClient
import com.hyphenate.easeui.common.ChatCustomMessageBody
import com.hyphenate.easeui.common.ChatGroup
import com.hyphenate.easeui.common.ChatMessage
import com.hyphenate.easeui.common.ChatMessageStatus
import com.hyphenate.easeui.common.ChatMessageType
import com.hyphenate.easeui.common.ChatType
import com.hyphenate.easeui.common.ChatUIKitConstant
import com.hyphenate.easeui.model.ChatUIKitProfile
import com.hyphenate.easeui.model.ChatUIKitUser

/**
 * Check if the current user is the owner of the group.
 */
fun ChatGroup.isOwner() = owner == ChatClient.getInstance().currentUser

/**
 * Check if the current user is the admin of the group.
 */
fun ChatGroup.isAdmin(): Boolean {
   return adminList?.firstOrNull { it == ChatClient.getInstance().currentUser } != null
}

fun ChatGroup.getOwnerInfo():ChatUIKitUser{
   return ChatUIKitProfile.getGroupMember(groupId, owner)?.toUser() ?: ChatUIKitUser(owner)
}

/**
 * Create local group message.
 * @param content The alert text shown in the gray bar, e.g. "群聊已创建\nxxx 加入群聊".
 */
internal fun ChatGroup.createNewGroupMessage(content: String): ChatMessage? {
   ChatUIKitClient.getContext()?.let { context ->
      return ChatMessage.createSendMessage(ChatMessageType.CUSTOM).let {
         it.from = ChatClient.getInstance().currentUser
         it.to = groupId
         it.chatType = ChatType.GroupChat
         val body = ChatCustomMessageBody(ChatUIKitConstant.MESSAGE_CUSTOM_ALERT)
         mutableMapOf(
            ChatUIKitConstant.MESSAGE_CUSTOM_ALERT_TYPE to ChatUIKitConstant.GROUP_WELCOME_MESSAGE,
            ChatUIKitConstant.MESSAGE_CUSTOM_ALERT_CONTENT to content,
            ChatUIKitConstant.GROUP_WELCOME_MESSAGE_GROUP_NAME to groupName
         ).let { map ->
            body.params = map
         }
         it.body = body
         it.setStatus(ChatMessageStatus.SUCCESS)
         it
      }
   }
   return null
}