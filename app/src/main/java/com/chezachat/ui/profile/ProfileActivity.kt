package com.chezachat.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.chezachat.ChezaApp
import com.chezachat.R
import com.chezachat.data.api.RetrofitClient
import com.chezachat.databinding.ActivityProfileBinding
import com.chezachat.ui.auth.AuthActivity
import com.chezachat.utils.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val session by lazy { ChezaApp.instance.sessionManager }
    private val viewModel: ProfileViewModel by viewModels()

    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.updateAvatar(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        RetrofitClient.init(session)
        setupToolbar()
        loadProfile()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadProfile() {
        val user = session.getUser() ?: return
        binding.tvName.text = user.name
        binding.tvEmail.text = user.email
        binding.tvPhone.text = user.phone
        binding.etStatus.setText(user.status)
        binding.ivAvatar.loadAvatar(user.avatarUrl)
        binding.swDarkMode.isChecked = session.isDarkMode
        binding.swNotifications.isChecked = session.notificationsEnabled
        binding.swReadReceipts.isChecked = session.readReceiptsEnabled
        binding.swLastSeen.isChecked = session.lastSeenVisible
    }

    private fun setupListeners() {
        binding.ivAvatar.setOnClickListener { pickAvatarLauncher.launch("image/*") }
        binding.btnEditAvatar.setOnClickListener { pickAvatarLauncher.launch("image/*") }

        binding.btnSaveStatus.setOnClickListener {
            val status = binding.etStatus.text.toString().trim()
            if (status.isNotEmpty()) viewModel.updateStatus(status)
        }

        binding.swDarkMode.setOnCheckedChangeListener { _, checked ->
            session.isDarkMode = checked
            recreate()
        }
        binding.swNotifications.setOnCheckedChangeListener { _, checked ->
            session.notificationsEnabled = checked
        }
        binding.swReadReceipts.setOnCheckedChangeListener { _, checked ->
            session.readReceiptsEnabled = checked
        }
        binding.swLastSeen.setOnCheckedChangeListener { _, checked ->
            session.lastSeenVisible = checked
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log out") { _, _ -> logout() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            if (loading) binding.progressBar.show() else binding.progressBar.hide()
        }
        viewModel.updateResult.observe(this) { result ->
            result?.let {
                session.saveUser(it)
                loadProfile()
                toast("Profile updated")
            }
        }
        viewModel.error.observe(this) { err -> err?.let { toast(it) } }
    }

    private fun logout() {
        viewModel.logout()
        session.logout()
        startActivity(
            Intent(this, AuthActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
