package com.chezachat.ui.status

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.chezachat.R
import com.chezachat.data.api.RetrofitClient
import com.chezachat.utils.toast
import com.google.android.material.tabs.TabLayout
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class StatusCreateActivity : AppCompatActivity() {

    private val bgColors = listOf(
        "#1D9E75","#534AB7","#E24B4A","#EF9F27","#222222",
        "#0077B6","#9B2226","#2D6A4F","#6A0572","#F77F00"
    )
    private var selectedColor = bgColors[0]
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { launchCrop(it) } }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            UCrop.getOutput(result.data!!)?.let { uri ->
                selectedImageUri = uri
                val ivPreview = findViewById<ImageView>(R.id.ivPreview)
                Glide.with(this).load(uri).centerCrop().into(ivPreview)
                ivPreview.visibility = View.VISIBLE
                findViewById<View>(R.id.pickImageHint).visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_create)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        setupColorPicker()
        setupTabs()

        // Tap image area to pick
        val imageContainer = findViewById<LinearLayout?>(R.id.imageStatusContainer)
        imageContainer?.setOnClickListener { pickImageLauncher.launch("image/*") }
        findViewById<View?>(R.id.pickImageHint)?.setOnClickListener { pickImageLauncher.launch("image/*") }

        findViewById<Button>(R.id.btnPost).setOnClickListener { post() }
    }

    private fun setupColorPicker() {
        val container = findViewById<LinearLayout>(R.id.colorPicker)
        val preview   = findViewById<FrameLayout>(R.id.textPreview)
        bgColors.forEach { hex ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { marginEnd = 8 }
                background = android.graphics.drawable.ShapeDrawable(
                    android.graphics.drawable.shapes.OvalShape()
                ).apply { paint.color = Color.parseColor(hex) }
                setOnClickListener {
                    selectedColor = hex
                    runCatching { preview.setBackgroundColor(Color.parseColor(hex)) }
                }
            }
            container.addView(dot)
        }
    }

    private fun setupTabs() {
        val textContainer  = findViewById<View>(R.id.textStatusContainer)
        val imageContainer = findViewById<View>(R.id.imageStatusContainer)
        val tabs = findViewById<TabLayout>(R.id.tabType)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> { textContainer.visibility  = View.VISIBLE; imageContainer.visibility = View.GONE }
                    1 -> { imageContainer.visibility = View.VISIBLE; textContainer.visibility  = View.GONE }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun launchCrop(uri: Uri) {
        val out    = File(cacheDir, "status_${System.currentTimeMillis()}.jpg")
        val dest   = FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
        val intent = UCrop.of(uri, dest)
            .withMaxResultSize(1080, 1080)
            .withOptions(UCrop.Options().apply {
                setFreeStyleCropEnabled(true)
                setCompressionQuality(85)
                setToolbarColor(getColor(R.color.primary))
                setStatusBarColor(getColor(R.color.primary_dark))
                setActiveControlsWidgetColor(getColor(R.color.primary))
                setToolbarTitle("Crop status image")
            }).getIntent(this)
        cropLauncher.launch(intent)
    }

    private fun post() {
        val tabs = findViewById<TabLayout>(R.id.tabType)
        val isText = tabs.selectedTabPosition == 0

        if (isText) postText() else postImage()
    }

    private fun postText() {
        val text = findViewById<EditText>(R.id.etStatusText).text.toString().trim()
        if (text.isEmpty()) { toast("Write something first"); return }
        setPosting(true)
        lifecycleScope.launch {
            val result = runCatching {
                RetrofitClient.api.createStatus(mapOf(
                    "type"     to "text",
                    "content"  to text,
                    "bg_color" to selectedColor
                ))
            }
            withContext(Dispatchers.Main) {
                setPosting(false)
                val resp = result.getOrNull()
                if (resp?.isSuccessful == true && resp.body()?.success == true) {
                    toast("Status posted!"); finish()
                } else {
                    toast("Failed to post status")
                }
            }
        }
    }

    private fun postImage() {
        val uri = selectedImageUri ?: run { toast("Pick an image first"); return }
        setPosting(true)
        lifecycleScope.launch {
            val result = runCatching {
                val stream  = contentResolver.openInputStream(uri)!!
                val mime    = contentResolver.getType(uri) ?: "image/jpeg"
                val bytes   = stream.readBytes(); stream.close()
                val reqBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
                val part    = MultipartBody.Part.createFormData("file", "status.jpg", reqBody)
                val typeP   = "image".toRequestBody("text/plain".toMediaTypeOrNull())
                val convP   = "0".toRequestBody("text/plain".toMediaTypeOrNull())
                val upResp  = RetrofitClient.api.uploadStatusMedia(part, typeP, convP)
                if (upResp.isSuccessful && upResp.body()?.success == true) {
                    val url     = upResp.body()!!.data!!["url"]!!
                    val caption = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCaption)
                        .text.toString().trim()
                    RetrofitClient.api.createStatus(mapOf(
                        "type"      to "image",
                        "content"   to caption,
                        "media_url" to url
                    ))
                } else null
            }
            withContext(Dispatchers.Main) {
                setPosting(false)
                val resp = result.getOrNull()
                if (resp?.isSuccessful == true && resp.body()?.success == true) {
                    toast("Status posted!"); finish()
                } else {
                    toast(result.exceptionOrNull()?.message ?: "Failed to post status")
                }
            }
        }
    }

    private fun setPosting(posting: Boolean) {
        findViewById<Button>(R.id.btnPost).isEnabled = !posting
        findViewById<Button>(R.id.btnPost).text = if (posting) "Posting…" else "Post Status"
    }
}
