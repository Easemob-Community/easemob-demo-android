package com.hyphenate.chatdemo.ui.me

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.hyphenate.chatdemo.BuildConfig
import com.hyphenate.chatdemo.DemoApplication
import com.hyphenate.chatdemo.DemoHelper
import com.hyphenate.chatdemo.R
import com.hyphenate.chatdemo.common.DemoConstant
import com.hyphenate.chatdemo.common.PresenceCache
import com.hyphenate.chatdemo.controller.PresenceController
import com.hyphenate.chatdemo.databinding.DemoFragmentAboutMeBinding
import com.hyphenate.chatdemo.interfaces.IPresenceResultView
import com.hyphenate.chatdemo.ui.login.LoginActivity
import com.hyphenate.chatdemo.utils.EasePresenceUtil
import com.hyphenate.chatdemo.viewmodel.LoginViewModel
import com.hyphenate.chatdemo.viewmodel.PresenceViewModel
import com.hyphenate.chatdemo.viewmodel.ProfileInfoViewModel
import com.hyphenate.easeui.ChatUIKitClient
import com.hyphenate.easeui.base.ChatUIKitBaseFragment
import com.hyphenate.easeui.common.ChatClient
import com.hyphenate.easeui.common.ChatLog
import com.hyphenate.easeui.common.ChatPresence
import com.hyphenate.easeui.common.bus.ChatUIKitFlowBus
import com.hyphenate.easeui.common.dialog.CustomDialog
import com.hyphenate.easeui.common.extensions.catchChatException
import com.hyphenate.easeui.common.extensions.dpToPx
import com.hyphenate.easeui.common.extensions.showToast
import com.hyphenate.easeui.configs.setStatusStyle
import com.hyphenate.easeui.feature.contact.ChatUIKitBlockListActivity
import com.hyphenate.easeui.model.ChatUIKitEvent
import com.hyphenate.easeui.widget.ChatUIKitCustomAvatarView
import kotlinx.coroutines.launch


class AboutMeFragment: ChatUIKitBaseFragment<DemoFragmentAboutMeBinding>(), View.OnClickListener,
    ChatUIKitCustomAvatarView.OnPresenceClickListener, IPresenceResultView {

    /**
     * The clipboard manager.
     */
    private val clipboard by lazy { mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    private lateinit var loginViewModel: LoginViewModel

    private val presenceViewModel by lazy { ViewModelProvider(this)[PresenceViewModel::class.java] }
    private val presenceController by lazy { PresenceController(mContext,presenceViewModel) }
    private val profileViewModel by lazy { ViewModelProvider(this)[ProfileInfoViewModel::class.java] }

    private var signature = ""

    private val launcherToEditSign: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let {
                if (it.getBooleanExtra("isRefresh", false)) {
                    it.getStringExtra(EditUserSignActivity.EXTRA_SIGN)?.let { sign ->
                        updateSignature(sign)
                    }
                }
            }
        }
    }

    // 连续点击计数器
    private var registrationClickCount = 0
    private var lastRegistrationClickTime = 0L

    companion object{
        private val TAG = AboutMeFragment::class.java.simpleName
        private const val CLICK_INTERVAL = 2000L // 2秒
        private const val CLICK_COUNT_THRESHOLD = 3 // 点击3次
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): DemoFragmentAboutMeBinding {
        return DemoFragmentAboutMeBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        super.initView(savedInstanceState)
        initPresence()
        initStatus()
        binding?.run {
            itemSdkVersion.setContent("Easemob SDK v${BuildConfig.VERSION_NAME}")
            itemUikitVersion.setContent("Easemob UIKit v${BuildConfig.VERSION_NAME}")
        }
    }
    override fun initViewModel() {
        super.initViewModel()
        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]
        presenceViewModel.attachView(this)
    }

    override fun initListener() {
        super.initListener()
        binding?.run {
            epPresence.setOnPresenceClickListener(this@AboutMeFragment)
            tvNumber.setOnClickListener(this@AboutMeFragment)
            tvSign.setOnClickListener {
                val intent = Intent(mContext, EditUserSignActivity::class.java)
                intent.putExtra(EditUserSignActivity.EXTRA_SIGN, signature)
                launcherToEditSign.launch(intent)
            }
            itemBusiness.setOnClickListener(this@AboutMeFragment)
            itemWebsite.setOnClickListener(this@AboutMeFragment)
            itemSdkVersion.setOnClickListener(this@AboutMeFragment)
            itemUikitVersion.setOnClickListener(this@AboutMeFragment)
            itemRegistrationNumber.setOnClickListener(this@AboutMeFragment)
            itemPolicies.setOnClickListener(this@AboutMeFragment)
            itemPresence.setOnClickListener(this@AboutMeFragment)
            itemInformation.setOnClickListener(this@AboutMeFragment)
            itemCurrency.setOnClickListener(this@AboutMeFragment)
            itemNotify.setOnClickListener(this@AboutMeFragment)
            itemPrivacy.setOnClickListener(this@AboutMeFragment)
            aboutMeLogout.setOnClickListener(this@AboutMeFragment)
            aboutMeAccountCancellation.setOnClickListener(this@AboutMeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun initData() {
        super.initData()
        fetchCurrentPresence()
        fetchSignature()
        initEvent()
    }

    private fun initEvent() {
        ChatUIKitFlowBus.with<ChatUIKitEvent>(ChatUIKitEvent.EVENT.UPDATE.name).register(this) {
            if (it.isPresenceChange && it.message.equals(ChatUIKitClient.getCurrentUser()?.id) ) {
                updatePresence()
            }
        }

        ChatUIKitFlowBus.with<ChatUIKitEvent>(ChatUIKitEvent.EVENT.UPDATE + ChatUIKitEvent.TYPE.CONTACT).register(this) {
            if (it.isContactChange && it.event == DemoConstant.EVENT_UPDATE_SELF) {
                updatePresence(true)
            }
        }
    }

    private fun initPresence(){
        binding?.run {
            var name:String? = ChatClient.getInstance().currentUser
            val id = getString(R.string.main_about_me_id,ChatClient.getInstance().currentUser)
            ChatUIKitClient.getConfig()?.avatarConfig?.setStatusStyle(epPresence.getStatusView(),4.dpToPx(mContext),
                ContextCompat.getColor(mContext, com.hyphenate.easeui.R.color.ease_color_background))
            epPresence.setPresenceStatusMargin(end = -4, bottom = -4)
            epPresence.setPresenceStatusSize(resources.getDimensionPixelSize(com.hyphenate.easeui.R.dimen.ease_contact_status_icon_size))

            val layoutParams = epPresence.getUserAvatar().layoutParams
            layoutParams.width = 64.dpToPx(mContext)
            layoutParams.height = 64.dpToPx(mContext)
            epPresence.getUserAvatar().layoutParams = layoutParams
            // Round avatar, aligned with iOS.
            epPresence.getUserAvatar().setRadius(32.dpToPx(mContext))

            ChatUIKitClient.getCurrentUser()?.let {
                epPresence.setUserAvatarData(it)
                name = it.getRemarkOrName()
            }
            tvName.text = name
            tvNumber.text = id
        }
    }

    private fun fetchSignature(){
        val userId = ChatClient.getInstance().currentUser ?: return
        lifecycleScope.launch {
            profileViewModel.fetchUserInfoAttribute(listOf(userId), listOf(com.hyphenate.easeui.common.ChatUserInfoType.SIGN))
                .catchChatException { ChatLog.e(TAG, "fetch sign failed: ${it.description}") }
                .collect { map ->
                    signature = map?.get(userId)?.signature ?: ""
                    binding?.tvSign?.text = signature.ifEmpty { getString(R.string.main_about_me_sign_placeholder) }
                }
        }
    }

    private fun updateSignature(sign: String){
        signature = sign
        binding?.tvSign?.text = sign
        lifecycleScope.launch {
            profileViewModel.updateUserSign(sign)
                .catchChatException { ChatLog.e(TAG, "update sign failed: ${it.description}") }
                .collect { }
        }
    }

    private fun updatePresence(isRefreshAvatar:Boolean = false){
        ChatUIKitClient.getCurrentUser()?.let { user->
            val presence = PresenceCache.getUserPresence(user.id)
            presence?.let {
                if (isRefreshAvatar){
                    binding?.epPresence?.setUserAvatarData(user)
                }else{
                    binding?.epPresence?.setUserStatusData(EasePresenceUtil.getPresenceIcon(mContext,it))
                    binding?.epPresence?.getStatusView()?.visibility = View.VISIBLE
                    val subtitle = EasePresenceUtil.getPresenceString(mContext,it)
                    binding?.itemPresence?.setContent(subtitle)
                }
            }?:kotlin.run {
                binding?.epPresence?.setUserAvatarData(user)
            }
            binding?.tvName?.text = user.getNotEmptyName()
        }
    }

    private fun initStatus(){
        val isSilent = ChatUIKitClient.checkMutedConversationList(ChatClient.getInstance().currentUser)
        if (isSilent) {
            binding?.icNotice?.visibility = View.VISIBLE
        }else{
            binding?.icNotice?.visibility = View.GONE
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            loginViewModel.logout()
                .catchChatException { e ->
                    ChatLog.e(TAG, "logout failed: ${e.description}")
                }
                .collect {
                    DemoHelper.getInstance().getDataModel().clearCache()
                    PresenceCache.clear()
                    DemoApplication.getInstance().getLifecycleCallbacks().skipToTarget(
                        LoginActivity::class.java)
                }
        }
    }

    private fun cancelAccount(){
        lifecycleScope.launch {
            loginViewModel.cancelAccount()
                .catchChatException {e ->
                    ChatLog.e(TAG, "cancelAccount failed: ${e.errorCode} ${e.description}")
                }
                .collect{
                    DemoHelper.getInstance().getDataModel().clearCache()
                    DemoApplication.getInstance().getLifecycleCallbacks().skipToTarget(
                        LoginActivity::class.java)
                }
        }
    }

    private fun fetchCurrentPresence(){
        presenceViewModel.fetchPresenceStatus(mutableListOf(ChatClient.getInstance().currentUser))
    }

    /**
     * 处理备案号的连续点击
     * 2秒内连续点击3次进入EMActivity
     */
    private fun handleRegistrationNumberClick() {
        val currentTime = System.currentTimeMillis()
        
        // 如果距离上次点击超过2秒，重置计数
        if (currentTime - lastRegistrationClickTime > CLICK_INTERVAL) {
            registrationClickCount = 0
        }
        
        // 更新最后点击时间
        lastRegistrationClickTime = currentTime
        registrationClickCount++
        
        ChatLog.d(TAG, "Registration number clicked: $registrationClickCount times")
        
        // 如果点击次数达到3次，尝试进入EMActivity
        if (registrationClickCount >= CLICK_COUNT_THRESHOLD) {
            registrationClickCount = 0 // 重置计数
            try {
                val clazz = Class.forName("com.hyphenate.chatdemo.EMActivity")
                startActivity(Intent(mContext, clazz))
            } catch (e: Exception) {
                ChatLog.e(TAG, "Failed to open EMActivity: ${e.message}")
            }
        }
    }

    override fun onPresenceClick(v: View?) {

    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.item_business -> {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${getString(R.string.main_about_me_business_phone).replace("-", "")}")))
            }
            R.id.item_website -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.easemob.com")))
            }
            R.id.item_registration_number -> {
                handleRegistrationNumberClick()
            }
            R.id.item_policies -> {
                startActivity(Intent(mContext, PoliciesActivity::class.java))
            }
            R.id.item_presence -> {
                ChatUIKitClient.getCurrentUser()?.id?.let {
                    presenceController.showPresenceStatusDialog(PresenceCache.getUserPresence(it))
                }
            }
            R.id.item_information -> {
                startActivity(Intent(mContext, UserInformationActivity::class.java))
            }
            R.id.item_currency -> {
                startActivity(Intent(mContext, CurrencyActivity::class.java))
            }
            R.id.item_notify -> {
                startActivity(Intent(mContext, NotifyActivity::class.java))
            }
            R.id.item_privacy -> {
                startActivity(Intent(mContext, ChatUIKitBlockListActivity::class.java))
            }
            R.id.about_me_logout -> {
                showLogoutDialog()
            }
            R.id.about_me_account_cancellation -> {
                showCancelAccountDialog()
            }
            R.id.tv_number -> {
                val indexOfSpace = binding?.tvNumber?.text?.indexOf(":")
                indexOfSpace?.let {
                    if (indexOfSpace != -1) {
                        val substring = binding?.tvNumber?.text?.substring(indexOfSpace + 1)
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                null,
                                substring
                            )
                        )
                    }
                }

            }
            else -> {}
        }
    }

    private fun showLogoutDialog(){
        val logoutDialog = CustomDialog(
            context = mContext,
            title = resources.getString(R.string.em_login_out_hint),
            isEditTextMode = false,
            onLeftButtonClickListener = {

            },
            onRightButtonClickListener = {
                logout()
            }
        )
        logoutDialog.show()
    }

    private fun showCancelAccountDialog(){
        val cancelAccountDialog = CustomDialog(
            context = mContext,
            title = resources.getString(R.string.em_login_cancel_account_title),
            subtitle = resources.getString(R.string.em_login_cancel_account_subtitle),
            isEditTextMode = false,
            onLeftButtonClickListener = {

            },
            onRightButtonClickListener = {
                if (!DemoHelper.getInstance().getDataModel().isDeveloperMode()){
                    cancelAccount()
                }else{
                    mContext.showToast(R.string.main_account_cancellation)
                }
            }
        )
        cancelAccountDialog.show()
    }

    override fun fetchPresenceStatusSuccess(presence: MutableList<ChatPresence>) {
        updatePresence()
    }

    override fun fetchPresenceStatusFail(code: Int, message: String?) {

    }

}