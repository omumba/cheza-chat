package com.chezachat.ui.status

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chezachat.R
import com.chezachat.model.UserStatuses
import com.chezachat.utils.loadAvatar
import de.hdodenhof.circleimageview.CircleImageView

class StatusCircleAdapter(
    private val onMyCircleClick: () -> Unit,
    private val onFriendClick: (UserStatuses) -> Unit
) : RecyclerView.Adapter<StatusCircleAdapter.VH>() {

    private val items = mutableListOf<UserStatuses?>() // null = "my status" add button

    fun submit(myStatus: UserStatuses?, friendStatuses: List<UserStatuses>) {
        items.clear()
        items.add(myStatus) // index 0 is always my status (may be null if no status)
        items.addAll(friendStatuses)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_status_circle, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        if (position == 0) {
            // My status circle
            val session = com.chezachat.ChezaApp.instance.sessionManager
            val me = session.getUser()
            holder.ivAvatar.loadAvatar(me?.avatarUrl)
            holder.tvName.text = "My Status"
            if (item == null) {
                // No active status — show "+" ring
                holder.ring.setBackgroundResource(R.drawable.bg_status_ring_viewed)
                holder.ivAdd.visibility = View.VISIBLE
            } else {
                holder.ring.setBackgroundResource(R.drawable.bg_status_ring_unviewed)
                holder.ivAdd.visibility = View.GONE
            }
            holder.itemView.setOnClickListener { onMyCircleClick() }
        } else {
            val us = item ?: return
            holder.ivAvatar.loadAvatar(us.userAvatar)
            holder.tvName.text = us.userName
            holder.ivAdd.visibility = View.GONE
            holder.ring.setBackgroundResource(
                if (us.hasUnviewed) R.drawable.bg_status_ring_unviewed
                else R.drawable.bg_status_ring_viewed
            )
            holder.itemView.setOnClickListener { onFriendClick(us) }
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivAvatar: CircleImageView = v.findViewById(R.id.ivAvatar)
        val tvName: TextView          = v.findViewById(R.id.tvName)
        val ring: View                = v.findViewById(R.id.statusRing)
        val ivAdd: ImageView          = v.findViewById(R.id.ivAdd)
    }
}
