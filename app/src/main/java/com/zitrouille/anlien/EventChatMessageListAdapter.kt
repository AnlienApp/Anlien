package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.zitrouille.anlien.MainActivity.Companion.globalUserInformations

class EventChatMessageListAdapter(private val dataSet: ArrayList<EventChatMessage>) :
    RecyclerView.Adapter<EventChatMessageListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageLeftTextView: TextView = view.findViewById(R.id.message_left)
        val messageRightTextView: TextView = view.findViewById(R.id.message_right)
        val userDisplayName: TextView = view.findViewById(R.id.user_display_name)
        val profilePicture: ImageView = view.findViewById(R.id.profile_picture)

        val leftMessageLayout: ConstraintLayout = view.findViewById(R.id.left_side_message)
        val rightMessageLayout: ConstraintLayout = view.findViewById(R.id.right_side_message)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_event_chat_message, viewGroup, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val userId = dataSet[position].getUserId()

        if(userId == FirebaseAuth.getInstance().currentUser!!.uid) {
            val message = dataSet[position].getMessage()
            viewHolder.messageRightTextView.text = message

            viewHolder.leftMessageLayout.visibility = View.GONE
            viewHolder.rightMessageLayout.visibility = View.VISIBLE
        }
        else {
            val message = dataSet[position].getMessage()
            viewHolder.messageLeftTextView.text = message

            viewHolder.leftMessageLayout.visibility = View.VISIBLE
            viewHolder.rightMessageLayout.visibility = View.GONE

            // If previous message has been sent by the same user,
            // hide the profile picture and the name
            if (dataSet.size > 1 && position > 0) {
                val previousMessageUserId = dataSet[position - 1].getUserId()
                if (previousMessageUserId == userId) {
                    viewHolder.userDisplayName.visibility = View.GONE
                } else {
                    viewHolder.userDisplayName.visibility = View.VISIBLE
                }
            }
            if (position < dataSet.size - 1) {
                val previousMessageUserId = dataSet[position + 1].getUserId()
                if (previousMessageUserId != userId) {
                    viewHolder.profilePicture.visibility = View.VISIBLE
                } else {
                    viewHolder.profilePicture.visibility = View.INVISIBLE
                }
            } else if (position == dataSet.size - 1) {
                viewHolder.profilePicture.visibility = View.VISIBLE
            }

            if (View.VISIBLE == viewHolder.profilePicture.visibility) {
                if (globalUserInformations.containsKey(userId)
                    && null != globalUserInformations[userId]!!.mUri
                ) {
                    Glide.with(viewHolder.itemView.context)
                        .load(globalUserInformations[userId]!!.mUri)
                        .into(viewHolder.profilePicture)
                } else {
                    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                        .child("profileImages")
                        .child("$userId.jpeg")
                    storageRef.downloadUrl.addOnSuccessListener {
                        Glide.with(viewHolder.itemView.context).load(it)
                            .into(viewHolder.profilePicture)
                        if (globalUserInformations.containsKey(userId)) {
                            globalUserInformations[userId]!!.mUri = it
                        }
                    }
                }
            }

            if (View.VISIBLE == viewHolder.userDisplayName.visibility) {
                if (globalUserInformations.containsKey(userId)) {
                    viewHolder.userDisplayName.text = globalUserInformations[userId]!!.mDisplayName
                } else {
                    val newUserRetrieved = MainActivity.Companion.UserInformation()
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId).get().addOnSuccessListener { userDocument ->
                            newUserRetrieved.mDisplayName = userDocument["displayName"].toString()
                            newUserRetrieved.mUniqueId = userDocument["uniquePseudo"].toString()
                        }
                    globalUserInformations[userId] = newUserRetrieved
                }
            }
        }
    }

    override fun getItemCount() = dataSet.size

}