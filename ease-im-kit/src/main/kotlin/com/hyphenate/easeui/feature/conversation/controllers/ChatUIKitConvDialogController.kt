package com.hyphenate.easeui.feature.conversation.controllers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.hyphenate.easeui.R
import com.hyphenate.easeui.common.dialog.CustomDialog
import com.hyphenate.easeui.common.dialog.ChatUIKitNewBottomSheetFragment
import com.hyphenate.easeui.common.extensions.dpToPx
import com.hyphenate.easeui.feature.group.ChatUIKitCreateGroupActivity

class ChatUIKitConvDialogController(
    private val context: Context,
    private val fragment: Fragment
) {

    /**
     * Show the compact menu anchored below the "+" title bar item, aligned with
     * its trailing edge, the same as the iOS ActionMenu popup (instead of a
     * bottom sheet).
     */
    fun showMoreDialog(anchor: View?, addContactAction: (String) -> Unit) {
        val activity = (context as FragmentActivity)
        val anchorView = anchor ?: activity.findViewById(android.R.id.content)
        val content = LayoutInflater.from(context).inflate(R.layout.uikit_pop_conv_more, null)
        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isOutsideTouchable = true
        popup.elevation = 16.dpToPx(context).toFloat()

        bindMoreItem(content, R.id.item_new_chat, R.drawable.uikit_conv_new_chat,
            R.string.uikit_conv_action_new_conversation) {
            popup.dismiss()
            val sheet = ChatUIKitNewBottomSheetFragment()
            sheet.show(fragment.childFragmentManager, "createNewChat")
        }
        bindMoreItem(content, R.id.item_add_contact, R.drawable.uikit_conv_add_contact,
            R.string.uikit_conv_action_add_contact) {
            popup.dismiss()
            showAddContactDialog(addContactAction)
        }
        bindMoreItem(content, R.id.item_create_group, R.drawable.uikit_conv_new_group,
            R.string.uikit_conv_action_create_group) {
            popup.dismiss()
            ChatUIKitCreateGroupActivity.actionStart(context)
        }

        // Align the popup's trailing edge with the anchor's trailing edge.
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val xOffset = anchorView.width - content.measuredWidth
        popup.showAsDropDown(anchorView, xOffset, 4.dpToPx(context))
    }

    private fun bindMoreItem(
        content: View,
        itemId: Int,
        iconRes: Int,
        titleRes: Int,
        onClick: () -> Unit,
    ) {
        val item = content.findViewById<View>(itemId)
        // Same as iOS: the menu icons are gray, not the brand blue.
        val iconTint = ContextCompat.getColor(context, R.color.ease_color_on_background)
        item.findViewById<AppCompatImageView>(R.id.iv_icon).apply {
            setImageResource(iconRes)
            setColorFilter(iconTint)
        }
        item.findViewById<TextView>(R.id.tv_title).setText(titleRes)
        item.setOnClickListener { onClick() }
    }

    fun showAddContactDialog(addContactAction: (String) -> Unit){
        val context = (context as FragmentActivity)
        val contactDialog = CustomDialog(
            context = context,
            title = context.getString(R.string.uikit_conv_action_add_contact),
            subtitle = context.getString(R.string.uikit_conv_dialog_add_contact),
            inputHint = context.getString(R.string.uikit_dialog_edit_input_id_hint),
            rightButtonText = context.getString(R.string.uikit_dialog_right_text),
            isEditTextMode = true,
            onInputModeConfirmListener = {
                addContactAction.invoke(it)
            }
        )
        contactDialog.show()
    }
}