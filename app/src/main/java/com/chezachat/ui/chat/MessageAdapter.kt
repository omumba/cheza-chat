package com.chezachat.ui.chat

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.chezachat.R
import com.chezachat.model.Message
import com.chezachat.utils.*
import de.hdodenhof.circleimageview.CircleImageView

class MessageAdapter(
    val myUserId: Int,
    private val onLongClick: (Message) -> Unit,
    private val onReplyClick: (Message) -> Unit,
    private val onImageClick: (String) -> Unit,
    private val isGroupChat: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val MINE_TEXT   = 0
        const val MINE_IMAGE  = 1
        const val MINE_MEDIA  = 2
        const val THEIR_TEXT  = 3
        const val THEIR_IMAGE = 4
        const val THEIR_MEDIA = 5
        const val DATE_HEADER = 6
    }

    private val items = mutableListOf<MessageListItem>()

    fun submitMessages(messages: List<Message>) {
        val newItems   = mutableListOf<MessageListItem>()
        var lastDate   = ""
        var lastSender = -1

        messages.forEach { msg ->
            val date = msg.createdAt.toDateHeader()
            if (date != lastDate) {
                newItems.add(MessageListItem.DateHeader(date))
                lastDate   = date
                lastSender = -1
            }
            val first = msg.senderId != lastSender
            newItems.add(MessageListItem.MessageItem(msg, first))
            lastSender = msg.senderId
        }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(op: Int, np: Int): Boolean {
                val o = items[op]; val n = newItems[np]
                return when {
                    o is MessageListItem.DateHeader  && n is MessageListItem.DateHeader  -> o.date == n.date
                    o is MessageListItem.MessageItem && n is MessageListItem.MessageItem -> o.message.id == n.message.id
                    else -> false
                }
            }
            override fun areContentsTheSame(op: Int, np: Int): Boolean {
                val o = items[op]; val n = newItems[np]
                return when {
                    o is MessageListItem.DateHeader  && n is MessageListItem.DateHeader  -> o.date == n.date
                    o is MessageListItem.MessageItem && n is MessageListItem.MessageItem ->
                        o.message == n.message && o.isFirstInCluster == n.isFirstInCluster
                    else -> false
                }
            }
        })
        items.clear(); items.addAll(newItems); diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = when (val item = items[position]) {
        is MessageListItem.DateHeader  -> item.date.hashCode().toLong() + Long.MIN_VALUE
        is MessageListItem.MessageItem -> item.message.id.toLong()
    }

    init { setHasStableIds(true) }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        if (item is MessageListItem.DateHeader) return DATE_HEADER
        val msg  = (item as MessageListItem.MessageItem).message
        val mine = msg.senderId == myUserId
        return when (msg.type) {
            "image"                  -> if (mine) MINE_IMAGE else THEIR_IMAGE
            "video", "audio", "file" -> if (mine) MINE_MEDIA else THEIR_MEDIA
            else                     -> if (mine) MINE_TEXT  else THEIR_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        fun inflate(id: Int) = LayoutInflater.from(parent.context).inflate(id, parent, false)
        return when (viewType) {
            DATE_HEADER  -> DateVH(inflate(R.layout.item_date_header))
            MINE_TEXT    -> MineTextVH(inflate(R.layout.item_message_mine))
            THEIR_TEXT   -> TheirTextVH(inflate(R.layout.item_message_theirs))
            MINE_IMAGE   -> MineImageVH(inflate(R.layout.item_message_image_mine))
            THEIR_IMAGE  -> TheirImageVH(inflate(R.layout.item_message_image_theirs))
            MINE_MEDIA   -> MineMediaVH(inflate(R.layout.item_message_media_mine))
            THEIR_MEDIA  -> TheirMediaVH(inflate(R.layout.item_message_media_theirs))
            else         -> MineTextVH(inflate(R.layout.item_message_mine))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MessageListItem.DateHeader  -> (holder as DateVH).bind(item.date)
            is MessageListItem.MessageItem -> when (holder) {
                is MineTextVH   -> holder.bind(item.message)
                is TheirTextVH  -> holder.bind(item.message, item.isFirstInCluster)
                is MineImageVH  -> holder.bind(item.message)
                is TheirImageVH -> holder.bind(item.message, item.isFirstInCluster)
                is MineMediaVH  -> holder.bind(item.message)
                is TheirMediaVH -> holder.bind(item.message, item.isFirstInCluster)
                else -> {}
            }
        }
    }

    // ── Date Header ───────────────────────────────────────────────────────────
    inner class DateVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvDate)
        fun bind(date: String) { tv.text = date }
    }

    // ── Mine Text ─────────────────────────────────────────────────────────────
    inner class MineTextVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvContent: TextView   = v.findViewById(R.id.tvContent)
        private val tvTime: TextView      = v.findViewById(R.id.tvTime)
        private val ivTick: ImageView     = v.findViewById(R.id.ivTick)
        private val replyContainer: View  = v.findViewById(R.id.replyContainer)
        private val tvReplyText: TextView = v.findViewById(R.id.tvReplyText)

        fun bind(msg: Message) {
            tvContent.text = if (msg.isDeleted) "This message was deleted" else msg.content
            tvTime.text    = msg.createdAt.toChatTimestamp()
            ivTick.setImageResource(tickDrawable(msg.status))
            bindReply(replyContainer, tvReplyText, msg)
            itemView.setOnLongClickListener { onLongClick(msg); true }
        }
    }

    // ── Their Text ────────────────────────────────────────────────────────────
    inner class TheirTextVH(v: View) : RecyclerView.ViewHolder(v) {
        private val ivAvatar: CircleImageView = v.findViewById(R.id.ivAvatar)
        private val tvName: TextView          = v.findViewById(R.id.tvName)
        private val tvContent: TextView       = v.findViewById(R.id.tvContent)
        private val tvTime: TextView          = v.findViewById(R.id.tvTime)
        private val replyContainer: View      = v.findViewById(R.id.replyContainer)
        private val tvReplyText: TextView     = v.findViewById(R.id.tvReplyText)

        fun bind(msg: Message, first: Boolean) {
            tvContent.text = if (msg.isDeleted) "This message was deleted" else msg.content
            tvTime.text    = msg.createdAt.toChatTimestamp()
            bindCluster(ivAvatar, tvName, msg, first)
            bindReply(replyContainer, tvReplyText, msg)
            itemView.setOnLongClickListener { onLongClick(msg); true }
        }
    }

    // ── Mine Image ────────────────────────────────────────────────────────────
    inner class MineImageVH(v: View) : RecyclerView.ViewHolder(v) {
        private val ivImage: ImageView = v.findViewById(R.id.ivImage)
        private val tvTime: TextView   = v.findViewById(R.id.tvTime)
        private val ivTick: ImageView  = v.findViewById(R.id.ivTick)

        fun bind(msg: Message) {
            ivImage.loadImage(msg.mediaUrl)
            tvTime.text = msg.createdAt.toChatTimestamp()
            ivTick.setImageResource(tickDrawable(msg.status))
            ivImage.setOnClickListener { msg.mediaUrl?.let { onImageClick(it) } }
            itemView.setOnLongClickListener { onLongClick(msg); true }
        }
    }

    // ── Their Image ───────────────────────────────────────────────────────────
    inner class TheirImageVH(v: View) : RecyclerView.ViewHolder(v) {
        private val ivAvatar: CircleImageView = v.findViewById(R.id.ivAvatar)
        private val ivImage: ImageView        = v.findViewById(R.id.ivImage)
        private val tvTime: TextView          = v.findViewById(R.id.tvTime)

        fun bind(msg: Message, first: Boolean) {
            ivImage.loadImage(msg.mediaUrl)
            tvTime.text = msg.createdAt.toChatTimestamp()
            if (first) { ivAvatar.show(); ivAvatar.loadAvatar(msg.senderAvatar) }
            else ivAvatar.invisible()
            ivImage.setOnClickListener { msg.mediaUrl?.let { onImageClick(it) } }
            itemView.setOnLongClickListener { onLongClick(msg); true }
        }
    }

    // ── Mine Media (video / audio / file) ─────────────────────────────────────
    inner class MineMediaVH(v: View) : RecyclerView.ViewHolder(v) {
        private val ivIcon: ImageView  = v.findViewById(R.id.ivMediaIcon)
        private val tvName: TextView   = v.findViewById(R.id.tvFileName)
        private val tvSize: TextView   = v.findViewById(R.id.tvFileSize)
        private val tvTime: TextView   = v.findViewById(R.id.tvTime)
        private val ivTick: ImageView  = v.findViewById(R.id.ivTick)

        fun bind(msg: Message) {
            ivIcon.setImageResource(mediaIcon(msg.type))
            tvName.text = msg.content.ifEmpty { "File" }
            tvSize.text = msg.mediaSize?.toReadableSize() ?: ""
            tvTime.text = msg.createdAt.toChatTimestamp()
            ivTick.setImageResource(tickDrawable(msg.status))
            // FIX: use itemView directly — no helper function that loses ViewHolder scope
            itemView.setOnClickListener {
                msg.mediaUrl?.let { url ->
                    itemView.context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    )
                }
            }
            itemView.setOnLongClickListener { onLongClick(msg); true }
        }
    }

    // ── Their Media ───────────────────────────────────────────────────────────
    inner class TheirMediaVH(v: View) : RecyclerView.ViewHolder(v) {
        private val ivAvatar: CircleImageView = v.findViewById(R.id.ivAvatar)
        private val ivIcon: ImageView         = v.findViewById(R.id.ivMediaIcon)
        private val tvName: TextView          = v.findViewById(R.id.tvFileName)
        private val tvSize: TextView          = v.findViewById(R.id.tvFileSize)
        private val tvTime: TextView          = v.findViewById(R.id.tvTime)

        fun bind(msg: Message, first: Boolean) {
            bindCluster(ivAvatar, null, msg, first)
            ivIcon.setImageResource(mediaIcon(msg.type))
            tvName.text = msg.content.ifEmpty { "File" }
            tvSize.text = msg.mediaSize?.toReadableSize() ?: ""
            tvTime.text = msg.createdAt.toChatTimestamp()
            itemView.setOnClickListener {
                msg.mediaUrl?.let { url ->
                    itemView.context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    )
                }
            }
            itemView.setOnLongClickListener { onLongClick(msg); true }
        }
    }

    // ── Shared helpers (private to adapter, no orphan functions) ──────────────

    private fun tickDrawable(status: String?) = when (status) {
        "read"      -> R.drawable.ic_tick_read
        "delivered" -> R.drawable.ic_tick_delivered
        "sent"      -> R.drawable.ic_tick_sent
        else        -> R.drawable.ic_tick_sending
    }

    private fun mediaIcon(type: String?) = when (type) {
        "video" -> R.drawable.ic_video
        "audio" -> R.drawable.ic_mic
        else    -> R.drawable.ic_attach
    }

    private fun bindReply(container: View, tvText: TextView, msg: Message) {
        if (msg.replyPreview != null) { container.show(); tvText.text = msg.replyPreview }
        else container.hide()
    }

    private fun bindCluster(
        ivAvatar: CircleImageView,
        tvName: TextView?,
        msg: Message,
        first: Boolean
    ) {
        if (first) {
            ivAvatar.show()
            ivAvatar.loadAvatar(msg.senderAvatar)
            if (isGroupChat) { tvName?.show(); tvName?.text = msg.senderName }
            else tvName?.hide()
        } else {
            ivAvatar.invisible()
            tvName?.hide()
        }
    }

    private fun Long.toReadableSize(): String = when {
        this < 1_024L              -> "${this} B"
        this < 1_048_576L          -> "${"%.1f".format(this / 1_024f)} KB"
        else                       -> "${"%.1f".format(this / 1_048_576f)} MB"
    }
}

sealed class MessageListItem {
    data class DateHeader(val date: String) : MessageListItem()
    data class MessageItem(val message: Message, val isFirstInCluster: Boolean) : MessageListItem()
}
