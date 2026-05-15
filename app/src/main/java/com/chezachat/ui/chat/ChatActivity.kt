package com.chezachat.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chezachat.R
import com.chezachat.data.api.RetrofitClient
import com.chezachat.databinding.ActivityChatBinding
import com.chezachat.model.Conversation
import com.chezachat.model.Message
import com.chezachat.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var conversation: Conversation

    private val viewModel: ChatViewModel by lazy {
        val conv = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONVERSATION, Conversation::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Conversation>(EXTRA_CONVERSATION)!!
        }
        conversation = conv
        val factory = ChatViewModel.Factory(application, conv.id)
        ViewModelProvider(this, factory)[ChatViewModel::class.java]
    }

    // ── File pickers ──────────────────────────────────────────────────────────
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { uploadAndSend(it, "image") } }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { uploadAndSend(it, "video") } }

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { uploadAndSend(it, "audio") } }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { uploadAndSend(it, "file") } }

    // ── Permission launcher ───────────────────────────────────────────────────
    private var pendingPickAction: (() -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) pendingPickAction?.invoke()
        else toast("Permission denied")
        pendingPickAction = null
    }

    companion object {
        const val EXTRA_CONVERSATION = "conversation"
        fun start(context: Context, conversation: Conversation) {
            context.startActivity(
                Intent(context, ChatActivity::class.java)
                    .putExtra(EXTRA_CONVERSATION, conversation)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // viewModel lazy init reads conversation from intent — just access it to trigger init
        viewModel

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvName.text = conversation.name
        binding.ivAvatar.loadAvatar(conversation.avatarUrl)
        updateOnlineStatus(conversation.isOnline, conversation.isTyping)
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            myUserId     = viewModel.myUserId,
            onLongClick  = { msg -> showMessageOptions(msg) },
            onReplyClick = { msg -> viewModel.setReply(msg) },
            onImageClick = { url -> openImageViewer(url) },
            isGroupChat  = conversation.type == "group"
        )
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply { stackFromEnd = true }
            adapter = this@ChatActivity.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (!rv.canScrollVertically(-1)) viewModel.loadMoreMessages()
                }
            })
        }
    }

    private fun setupInput() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.visibility   = if (hasText) View.VISIBLE else View.GONE
                binding.btnRecord.visibility = if (hasText) View.GONE    else View.VISIBLE
                if (hasText) viewModel.onTyping()
            }
        })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString()?.trim() ?: return@setOnClickListener
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                binding.etMessage.text?.clear()
            }
        }

        binding.btnAttach.setOnClickListener { showAttachMenu() }
        binding.btnCancelReply.setOnClickListener { viewModel.clearReply() }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            adapter.submitMessages(messages)
            if (messages.isNotEmpty()) {
                val last = messages.last()
                if (last.senderId != viewModel.myUserId) viewModel.sendReadReceipt(last.id)
                scrollToBottom()
            }
        }

        viewModel.typingUsers.observe(this) { typing ->
            updateOnlineStatus(conversation.isOnline, typing.isNotEmpty())
        }

        viewModel.replyMessage.observe(this) { msg ->
            if (msg != null) {
                binding.replyContainer.show()
                binding.tvReplyName.text    = msg.senderName
                binding.tvReplyPreview.text = msg.content.ifEmpty { "📎 Media" }
            } else {
                binding.replyContainer.hide()
            }
        }

        viewModel.uploadState.observe(this) { state ->
            when (state) {
                is UploadState.Idle -> {
                    binding.uploadProgress.hide()
                    binding.tvUploadStatus.hide()
                    binding.btnAttach.isEnabled = true
                }
                is UploadState.Uploading -> {
                    binding.uploadProgress.show()
                    binding.tvUploadStatus.show()
                    binding.uploadProgress.progress = state.percent
                    binding.tvUploadStatus.text = "Uploading ${state.fileName}… ${state.percent}%"
                    binding.btnAttach.isEnabled = false
                }
                is UploadState.Done -> {
                    binding.uploadProgress.hide()
                    binding.tvUploadStatus.hide()
                    binding.btnAttach.isEnabled = true
                }
                is UploadState.Error -> {
                    binding.uploadProgress.hide()
                    binding.tvUploadStatus.hide()
                    binding.btnAttach.isEnabled = true
                    toast(state.message)
                }
            }
        }

        // FIX: correct import androidx.lifecycle.repeatOnLifecycle
        // used as a top-level extension on LifecycleOwner inside lifecycleScope
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        is ChatEvent.ShowError      -> toast(event.message)
                        is ChatEvent.ScrollToBottom -> scrollToBottom()
                        else -> {}
                    }
                }
            }
        }
    }

    // ── Attach menu ───────────────────────────────────────────────────────────
    private fun showAttachMenu() {
        val options = arrayOf(
            "📷  Photo / Image",
            "🎬  Video",
            "🎵  Audio",
            "📄  Document / File"
        )
        AlertDialog.Builder(this)
            .setTitle("Send attachment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestAndPick(mediaPermissions(), { pickImageLauncher.launch("image/*") })
                    1 -> requestAndPick(mediaPermissions(), { pickVideoLauncher.launch("video/*") })
                    2 -> requestAndPick(audioPermissions(), { pickAudioLauncher.launch("audio/*") })
                    3 -> requestAndPick(mediaPermissions(), { pickFileLauncher.launch("*/*") })
                }
            }.show()
    }

    private fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun audioPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun requestAndPick(permissions: Array<String>, launch: () -> Unit) {
        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isEmpty()) {
            launch()
        } else {
            pendingPickAction = launch
            permissionLauncher.launch(denied.toTypedArray())
        }
    }

    // ── Upload ────────────────────────────────────────────────────────────────
    private fun uploadAndSend(uri: Uri, mediaType: String) {
        lifecycleScope.launch {
            val fileName = resolveFileName(uri) ?: "file_${System.currentTimeMillis()}"
            val mime = contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                ) ?: when (mediaType) {
                    "image" -> "image/jpeg"
                    "video" -> "video/mp4"
                    "audio" -> "audio/mpeg"
                    else    -> "application/octet-stream"
                }

            val resolvedType = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                mime.startsWith("audio/") -> "audio"
                else                      -> "file"
            }

            viewModel.startUpload(fileName)

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val totalBytes = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
                    val inputStream: InputStream = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot open: $uri")

                    val buffer = ByteArray(8192)
                    var bytesRead = 0L
                    val out = ByteArrayOutputStream()
                    var n: Int
                    while (inputStream.read(buffer).also { n = it } != -1) {
                        out.write(buffer, 0, n)
                        bytesRead += n
                        if (totalBytes > 0) {
                            val pct = ((bytesRead.toFloat() / totalBytes) * 100).toInt()
                            withContext(Dispatchers.Main) { viewModel.updateUploadProgress(pct) }
                        }
                    }
                    inputStream.close()
                    val bytes = out.toByteArray()

                    val requestBody: RequestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
                    val typePart   = resolvedType.toRequestBody("text/plain".toMediaTypeOrNull())
                    val convIdPart = conversation.id.toString()
                        .toRequestBody("text/plain".toMediaTypeOrNull())

                    RetrofitClient.api.uploadMedia(part, typePart, convIdPart)
                }
            }

            result.fold(
                onSuccess = { response ->
                    if (response.isSuccessful && response.body()?.success == true) {
                        val url  = response.body()!!.data?.get("url")
                        val size = response.body()!!.data?.get("size")?.toLongOrNull()
                        if (url != null) {
                            viewModel.onUploadSuccess()
                            viewModel.sendMessage(
                                content   = fileName,
                                type      = resolvedType,
                                mediaUrl  = url,
                                mediaSize = size
                            )
                        } else {
                            viewModel.onUploadError("Server error: no URL returned")
                        }
                    } else {
                        val msg = response.body()?.message
                            ?: response.errorBody()?.string()
                            ?: "Upload failed (${response.code()})"
                        viewModel.onUploadError(msg)
                    }
                },
                onFailure = { e ->
                    viewModel.onUploadError(e.message ?: "Upload failed")
                }
            )
        }
    }

    private fun resolveFileName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        }
    } catch (e: Exception) { null }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun scrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) binding.rvMessages.smoothScrollToPosition(count - 1)
    }

    private fun updateOnlineStatus(online: Boolean, typing: Boolean) {
        binding.tvStatus.text = when {
            typing -> getString(R.string.typing)
            online -> getString(R.string.online)
            else   -> ""
        }
        binding.statusDot.setBackgroundResource(
            if (online || typing) R.drawable.bg_online_dot else R.drawable.bg_offline_dot
        )
    }

    private fun showMessageOptions(message: Message) {
        val isMine  = message.senderId == viewModel.myUserId
        val options = buildList {
            add("Reply")
            if (isMine) add("Delete")
            if (message.type == "text") add("Copy text")
        }
        AlertDialog.Builder(this)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Reply"     -> viewModel.setReply(message)
                    "Delete"    -> confirmDelete(message)
                    "Copy text" -> copyText(message.content)
                }
            }.show()
    }

    private fun confirmDelete(message: Message) {
        AlertDialog.Builder(this)
            .setTitle("Delete message?")
            .setMessage("This will delete the message for everyone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteMessage(message) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun copyText(content: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(android.content.ClipData.newPlainText("message", content))
        toast("Copied")
    }

    private fun openImageViewer(url: String) {
        startActivity(Intent(this, ImageViewerActivity::class.java).putExtra("url", url))
    }
}
