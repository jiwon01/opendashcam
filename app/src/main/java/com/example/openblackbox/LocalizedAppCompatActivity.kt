package com.example.openblackbox

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

open class LocalizedAppCompatActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }
}
