package com.chezachat.ui.status

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.chezachat.ChezaApp
import com.chezachat.R
import com.chezachat.data.api.RetrofitClient
import com.chezachat.model.Status
import com.chezachat.model.UserStatuses
import com.chezachat.utils.loadAvatar
import com.chezachat.utils.toChatTimestamp
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch

class StatusViewerActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private lateinit var userStatuses: UserStatuses
    private val progressBars = mutableListOf<ProgressBar>()
    private val DURATION = 5000L // 5s per status

    companion object {
        private const val EXTRA = "user_statuses"
        fun start(context: Context, userStatuses: UserStatuses) {
            context.startActivity(Intent(context, StatusViewerActivity::class.java)
                .putExtra(EXTRA, userStatuses))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_viewer)

        userStatuses = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(EXTRA, UserStatuses::class.java)!!
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA)!!

        setupProgressBars()
        setupTapZones()
        showStatus(0)

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
    }

    private fun setupProgressBars() {
        val container = findViewById<LinearLayout>(R.id.progressBars)
        val count = userStatuses.statuses.size
        userStatuses.statuses.forEachIndexed { i, _ ->
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (i < count - 1) marginEnd = 4
                }
                max = 100
                progress = 0
                progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x66FFFFFF.toInt())
            }
            progressBars.add(pb)
            container.addView(pb)
        }
    }

    private fun setupTapZones() {
        val half = resources.displayMetrics.widthPixels / 2
        val leftZone  = findViewById<View>(R.id.tapLeft).apply {
            layoutParams = (layoutParams as FrameLayout.LayoutParams).also {
                it.width = half; it.gravity = android.view.Gravity.START
            }
        }
        val rightZone = findViewById<View>(R.id.tapRight).apply {
            layoutParams = (layoutParams as FrameLayout.LayoutParams).also {
                it.width = half; it.gravity = android.view.Gravity.END
            }
        }
        leftZone.setOnClickListener  { if (currentIndex > 0) showStatus(currentIndex - 1) else finish() }
        rightZone.setOnClickListener { advance() }
    }

    private fun showStatus(index: Int) {
        if (index >= userStatuses.statuses.size) { finish(); return }
        handler.removeCallbacksAndMessages(null)
        currentIndex = index
        val status = userStatuses.statuses[index]

        // Mark previous bars full, current bar starts, future bars empty
        progressBars.forEachIndexed { i, pb ->
            pb.progress = when {
                i < index  -> 100
                i == index -> 0
                else       -> 0
            }
        }

        // Bind user info
        val session = ChezaApp.instance.sessionManager
        val myId = session.getUserId()
        val isMine = userStatuses.userId == myId

        findViewById<CircleImageView>(R.id.ivUserAvatar).loadAvatar(userStatuses.userAvatar)
        findViewById<TextView>(R.id.tvUserName).text =
            if (isMine) "My status" else userStatuses.userName
        findViewById<TextView>(R.id.tvStatusTime).text =
            status.createdAt.toChatTimestamp()

        // Show view count for own statuses
        val bottomBar = findViewById<LinearLayout>(R.id.bottomBar)
        if (isMine) {
            bottomBar.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvViewCount).text = "${status.viewCount} views"
        } else {
            bottomBar.visibility = View.GONE
            markViewed(status.id)
        }

        // Render status content
        when (status.type) {
            "image" -> {
                findViewById<ImageView>(R.id.ivStatusMedia).apply {
                    visibility = View.VISIBLE
                    Glide.with(this@StatusViewerActivity).load(status.mediaUrl).into(this)
                }
                findViewById<View>(R.id.vTextBg).visibility = View.GONE
                findViewById<TextView>(R.id.tvStatusText).visibility = View.GONE
            }
            else -> {
                val bgView   = findViewById<View>(R.id.vTextBg)
                val tvStatus = findViewById<TextView>(R.id.tvStatusText)
                val ivMedia  = findViewById<ImageView>(R.id.ivStatusMedia)
                ivMedia.visibility  = View.GONE
                bgView.visibility   = View.VISIBLE
                tvStatus.visibility = View.VISIBLE
                runCatching { bgView.setBackgroundColor(Color.parseColor(status.bgColor)) }
                    .onFailure { bgView.setBackgroundColor(Color.parseColor("#1D9E75")) }
                tvStatus.text = status.content ?: ""
            }
        }

        // Animate progress bar
        animateProgress(progressBars[index])
    }

    private fun animateProgress(pb: ProgressBar) {
        val steps = 100
        val interval = DURATION / steps
        var step = 0
        val ticker = object : Runnable {
            override fun run() {
                if (step > steps) { advance(); return }
                pb.progress = step++
                handler.postDelayed(this, interval)
            }
        }
        handler.post(ticker)
    }

    private fun advance() {
        if (currentIndex + 1 < userStatuses.statuses.size) showStatus(currentIndex + 1)
        else finish()
    }

    private fun markViewed(statusId: Int) {
        lifecycleScope.launch {
            runCatching { RetrofitClient.api.viewStatus(mapOf("status_id" to statusId)) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
