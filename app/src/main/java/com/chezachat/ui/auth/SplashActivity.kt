package com.chezachat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.chezachat.ChezaApp
import com.chezachat.R
import com.chezachat.ui.home.MainActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val session = ChezaApp.instance.sessionManager
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                if (session.isLoggedIn()) {
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    startActivity(Intent(this, AuthActivity::class.java))
                }
                finish()
            }
        }, 1800)
    }
}
