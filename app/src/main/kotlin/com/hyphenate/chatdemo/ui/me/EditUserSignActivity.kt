package com.hyphenate.chatdemo.ui.me

import android.os.Bundle
import com.hyphenate.chatdemo.R

/**
 * Edit the user signature, reuses the nickname edit page layout.
 * Pass the current signature with the [EXTRA_SIGN] extra.
 */
class EditUserSignActivity : EditUserNicknameActivity() {

    companion object {
        const val EXTRA_SIGN = "sign"
    }

    override val resultKey: String = EXTRA_SIGN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sign = intent.getStringExtra(EXTRA_SIGN) ?: ""
        binding.etName.setText(sign)
        binding.etName.setSelection(sign.length)
        binding.inputNameCount.text = resources.getString(
            R.string.main_about_me_information_change_name_count, sign.length
        )
        updateSaveView(if (sign.isEmpty()) 0 else 1)
    }

    override fun initTitle() {
        binding.titleBar.setTitle(getString(R.string.main_about_me_information_edit_sign))
    }
}
