package com.chezachat.utils

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chezachat.R
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ── View helpers ──────────────────────────────────────────────────────────────

fun View.show()      { visibility = View.VISIBLE }
fun View.hide()      { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

// ── Image loading ─────────────────────────────────────────────────────────────

fun ImageView.loadAvatar(url: String?, placeholder: Int = R.drawable.ic_default_avatar) {
    Glide.with(this)
        .load(url)
        .apply(RequestOptions.circleCropTransform())
        .placeholder(placeholder)
        .error(placeholder)
        .into(this)
}

fun ImageView.loadImage(url: String?) {
    Glide.with(this)
        .load(url)
        .placeholder(R.drawable.ic_image_placeholder)
        .error(R.drawable.ic_image_placeholder)
        .into(this)
}

// ── Time formatting ───────────────────────────────────────────────────────────

fun Long.toMessageTime(): String {
    val now  = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1)   -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1)    -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(this))
        diff < TimeUnit.DAYS.toMillis(7)    -> SimpleDateFormat("EEE",    Locale.getDefault()).format(Date(this))
        else                                -> SimpleDateFormat("dd/MM/yy",Locale.getDefault()).format(Date(this))
    }
}

fun Long.toChatTimestamp(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(this))

fun Long.toLastSeen(): String {
    val now  = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < TimeUnit.MINUTES.toMillis(2) -> "online"
        diff < TimeUnit.HOURS.toMillis(1)   -> "last seen ${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
        diff < TimeUnit.DAYS.toMillis(1)    -> "last seen today at ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(this))}"
        diff < TimeUnit.DAYS.toMillis(2)    -> "last seen yesterday"
        else                                -> "last seen ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(this))}"
    }
}

fun Long.toDateHeader(): String {
    val cal       = Calendar.getInstance().apply { timeInMillis = this@toDateHeader }
    val today     = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)     -> "Today"
        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(this))
    }
}

// ── String utils ──────────────────────────────────────────────────────────────

fun String.initials(): String {
    val parts = trim().split(" ")
    return when {
        parts.size >= 2    -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.isNotEmpty() -> parts[0].take(2).uppercase()
        else               -> "??"
    }
}

// ── File/multipart utils ──────────────────────────────────────────────────────

fun Uri.toMultipart(context: Context, fieldName: String = "file"): MultipartBody.Part? {
    return try {
        val stream = context.contentResolver.openInputStream(this) ?: return null
        val mime   = context.contentResolver.getType(this) ?: "image/jpeg"
        val bytes  = stream.readBytes()
        stream.close()
        val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
        MultipartBody.Part.createFormData(
            fieldName,
            "upload_${System.currentTimeMillis()}",
            requestBody
        )
    } catch (e: Exception) {
        null
    }
}
