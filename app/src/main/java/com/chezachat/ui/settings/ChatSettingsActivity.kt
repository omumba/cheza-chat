package com.chezachat.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chezachat.R
import com.chezachat.ChezaApp
import com.chezachat.data.api.RetrofitClient
import com.chezachat.utils.*
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ChatSettingsActivity : AppCompatActivity() {

    private val session by lazy { ChezaApp.instance.sessionManager }
    private val api     by lazy { RetrofitClient.api }

    // Views
    private lateinit var ivAvatar: CircleImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etStatus: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSave: Button
    private lateinit var btnLogout: Button
    private lateinit var btnChangePassword: TextView
    private lateinit var swNotifications: Switch
    private lateinit var swReadReceipts: Switch
    private lateinit var swOnlineStatus: Switch

    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { uploadAvatar(it) } }

    companion object {
        fun start(context: Context) =
            context.startActivity(Intent(context, ChatSettingsActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_settings)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply { setDisplayHomeAsUpEnabled(true); title = "Settings" }
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        ivAvatar           = findViewById(R.id.ivAvatar)
        tvName             = findViewById(R.id.tvName)
        tvEmail            = findViewById(R.id.tvEmail)
        tvPhone            = findViewById(R.id.tvPhone)
        etName             = findViewById(R.id.etName)
        etPhone            = findViewById(R.id.etPhone)
        etStatus           = findViewById(R.id.etStatus)
        progressBar        = findViewById(R.id.progressBar)
        btnSave            = findViewById(R.id.btnSave)
        btnLogout          = findViewById(R.id.btnLogout)
        btnChangePassword  = findViewById(R.id.tvChangePassword)
        swNotifications    = findViewById(R.id.swNotifications)
        swReadReceipts     = findViewById(R.id.swReadReceipts)
        swOnlineStatus     = findViewById(R.id.swOnlineStatus)

        loadUser()
        loadPreferences()

        ivAvatar.setOnClickListener { pickAvatarLauncher.launch("image/*") }

        btnSave.setOnClickListener { saveProfile() }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("You will need to log in again.")
                .setPositiveButton("Sign out") { _, _ -> logout() }
                .setNegativeButton("Cancel", null).show()
        }

        btnChangePassword.setOnClickListener { showChangePasswordDialog() }

        swNotifications.setOnCheckedChangeListener { _, checked ->
            savePref("notifications_enabled", checked)
        }
        swReadReceipts.setOnCheckedChangeListener { _, checked ->
            savePref("read_receipts_enabled", checked)
        }
        swOnlineStatus.setOnCheckedChangeListener { _, checked ->
            savePref("show_online_status", checked)
            updateOnlineStatus(checked)
        }
    }

    private fun loadUser() {
        val user = session.getUser() ?: return
        ivAvatar.loadAvatar(user.avatarUrl)
        tvName.text  = user.name
        tvEmail.text = user.email
        tvPhone.text = user.phone ?: ""
        etName.setText(user.name)
        etPhone.setText(user.phone ?: "")
        etStatus.setText(user.status ?: "Hey there! I'm using Cheza Chat")
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("cheza_prefs", Context.MODE_PRIVATE)
        swNotifications.isChecked = prefs.getBoolean("notifications_enabled", true)
        swReadReceipts.isChecked  = prefs.getBoolean("read_receipts_enabled", true)
        swOnlineStatus.isChecked  = prefs.getBoolean("show_online_status", true)
    }

    private fun savePref(key: String, value: Boolean) {
        getSharedPreferences("cheza_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(key, value).apply()
    }

    private fun saveProfile() {
        val name   = etName.text.toString().trim()
        val phone  = etPhone.text.toString().trim()
        val status = etStatus.text.toString().trim()

        if (name.isBlank()) { toast("Name cannot be empty"); return }

        lifecycleScope.launch {
            progressBar.show()
            btnSave.isEnabled = false
            try {
                val body = mapOf("name" to name, "phone" to phone, "status" to status)
                val response = withContext(Dispatchers.IO) { api.updateProfile(body) }
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.let { session.saveUser(it) }
                    loadUser()
                    toast("Profile updated")
                } else {
                    toast(response.body()?.message ?: "Update failed")
                }
            } catch (e: Exception) {
                toast("Error: ${e.message}")
            }
            progressBar.hide()
            btnSave.isEnabled = true
        }
    }

    private fun uploadAvatar(uri: Uri) {
        lifecycleScope.launch {
            progressBar.show()
            ivAvatar.alpha = 0.5f
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.readBytes()
                } ?: run { toast("Cannot read file"); return@launch }
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                val reqBody  = bytes.toRequestBody(mime.toMediaTypeOrNull())
                val part     = MultipartBody.Part.createFormData("image", "avatar.jpg", reqBody)
                val response = withContext(Dispatchers.IO) { api.updateAvatar(part) }
                if (response.isSuccessful && response.body()?.success == true) {
                    val url = response.body()?.data?.get("avatar_url")
                    if (url != null) {
                        session.getUser()?.copy(avatarUrl = url)?.let { session.saveUser(it) }
                        ivAvatar.loadAvatar(url)
                        toast("Photo updated")
                    }
                } else {
                    toast(response.body()?.message ?: "Upload failed")
                }
            } catch (e: Exception) { toast("Error: ${e.message}") }
            progressBar.hide()
            ivAvatar.alpha = 1f
        }
    }

    private fun updateOnlineStatus(show: Boolean) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.updateProfile(mapOf("show_online" to if (show) "1" else "0"))
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrent = view.findViewById<EditText>(R.id.etCurrentPassword)
        val etNew     = view.findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = view.findViewById<EditText>(R.id.etConfirmPassword)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(view)
            .setPositiveButton("Change") { _, _ ->
                val current = etCurrent.text.toString()
                val new     = etNew.text.toString()
                val confirm = etConfirm.text.toString()
                if (new.length < 6)   { toast("Password must be at least 6 characters"); return@setPositiveButton }
                if (new != confirm)    { toast("Passwords do not match"); return@setPositiveButton }
                changePassword(current, new)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun changePassword(current: String, new: String) {
        lifecycleScope.launch {
            progressBar.show()
            try {
                val body = mapOf("current_password" to current, "new_password" to new)
                val response = withContext(Dispatchers.IO) { api.changePassword(body) }
                if (response.isSuccessful && response.body()?.success == true) {
                    toast("Password changed successfully")
                } else {
                    toast(response.body()?.message ?: "Failed to change password")
                }
            } catch (e: Exception) { toast("Error: ${e.message}") }
            progressBar.hide()
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { api.logout() } } catch (e: Exception) { /* silent */ }
            session.logout()
            val intent = Intent(this@ChatSettingsActivity,
                com.chezachat.ui.auth.AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}
