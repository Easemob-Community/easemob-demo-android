package com.hyphenate.chatdemo.ui.me

import android.os.Bundle
import android.view.LayoutInflater
import com.hyphenate.chatdemo.R
import com.hyphenate.chatdemo.databinding.DemoActivityPoliciesBinding
import com.hyphenate.easeui.base.ChatUIKitBaseActivity

/**
 * Privacy & agreements page, aligned with iOS PrivacyPolicyViewController:
 * lists the privacy related entries that open the corresponding web pages.
 */
class PoliciesActivity : ChatUIKitBaseActivity<DemoActivityPoliciesBinding>() {

    override fun getViewBinding(inflater: LayoutInflater): DemoActivityPoliciesBinding {
        return DemoActivityPoliciesBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitles()
        initListener()
    }

    private fun setTitles() {
        binding.titleBar.setTitle(getString(R.string.main_about_me_policies))
    }

    private fun initListener() {
        binding.titleBar.setNavigationOnClickListener { onBackPressed() }
        binding.itemPrivacyPolicy.setOnClickListener {
            WebViewActivity.actionStart(mContext, WebViewLoadType.PrivacyPolicy)
        }
        binding.itemTermsOfService.setOnClickListener {
            WebViewActivity.actionStart(mContext, WebViewLoadType.TermsOfService)
        }
        binding.itemThirdPartyData.setOnClickListener {
            WebViewActivity.actionStart(mContext, WebViewLoadType.ThirdPartyDataSharing)
        }
        binding.itemPersonalDataCollection.setOnClickListener {
            val username = com.hyphenate.easeui.common.ChatClient.getInstance().currentUser ?: ""
            val phone = com.hyphenate.chatdemo.DemoHelper.getInstance().getDataModel().getPhoneNumber()
            val device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
            val avatar = com.hyphenate.easeui.ChatUIKitClient.getCurrentUser()?.avatar ?: ""
            WebViewActivity.actionStartWithParams(
                mContext,
                WebViewLoadType.PersonalDataCollection,
                username = username,
                phone = phone,
                device = device,
                avatar = avatar
            )
        }
    }
}
