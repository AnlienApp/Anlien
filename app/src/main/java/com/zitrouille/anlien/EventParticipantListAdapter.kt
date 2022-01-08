package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.zitrouille.anlien.MainActivity.Companion.userCacheInformation

class EventParticipantListAdapter(private val dataSet: ArrayList<EventParticipant>) :
    RecyclerView.Adapter<EventParticipantListAdapter.ViewHolder>() {

    private val mDataSet = dataSet

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val displayNameTextView: TextView = view.findViewById(R.id.name)
        val identifiantTextView: TextView = view.findViewById(R.id.identifiant)
        val profilePictureImageView: ImageView = view.findViewById(R.id.profile_picture)

        val validImageView: ImageView = view.findViewById(R.id.valid)
        val questionImageView: ImageView = view.findViewById(R.id.question)
        val cancelImageView: ImageView = view.findViewById(R.id.cancel)

        val deleteButton: ImageView = view.findViewById(R.id.delete)

        val ghostView: ImageView = view.findViewById(R.id.ghost)
        val mainLayout: ConstraintLayout = view.findViewById(R.id.main_layout)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_event_participant, viewGroup, false)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val userId = dataSet[position].getUserId()
        val eventId = dataSet[position].getEventId()
        if(userCacheInformation.containsKey(userId)) {
            viewHolder.displayNameTextView.text = userCacheInformation[userId]!!.displayName
            viewHolder.identifiantTextView.text = userCacheInformation[userId]!!.identifiant
            Glide.with(viewHolder.itemView.context).load(userCacheInformation[userId]!!.uri)
                .into(viewHolder.profilePictureImageView)
        }
        else {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId).get().addOnSuccessListener { doc ->
                    if(doc.exists()) {
                        val userCache = MainActivity.Companion.UserInformation()
                        userCache.displayName = doc["displayName"].toString()
                        userCache.identifiant = doc["identifiant"].toString()
                        userCache.notificationToken = doc["notificationToken"].toString()
                        viewHolder.displayNameTextView.text = userCache.displayName
                        viewHolder.identifiantTextView.text = userCache.identifiant
                        userCacheInformation[userId] = userCache
                        val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                            .child("profileImages")
                            .child("$userId.jpeg")
                        storageRef.downloadUrl.addOnSuccessListener {
                            Glide.with(viewHolder.itemView.context).load(it)
                                .into(viewHolder.profilePictureImageView)
                            userCache.uri = it
                        }
                    }
                }
        }

        when (dataSet[position].getStatus()) {
            0L -> {
                viewHolder.validImageView.visibility = View.VISIBLE
            }
            1L -> {
                viewHolder.questionImageView.visibility = View.VISIBLE
            }
            2L -> {
                viewHolder.cancelImageView.visibility = View.VISIBLE
            }
        }

        manageGhostIcon(viewHolder, userId)

        // Delete management
        if(dataSet[position].getMoreMenu()) {
            viewHolder.deleteButton.visibility = View.VISIBLE
            viewHolder.deleteButton.setOnClickListener {
                FirebaseFirestore.getInstance()
                    .collection("events")
                    .document(eventId)
                    .collection("participants")
                    .whereEqualTo("userId", userId).get().addOnSuccessListener { docs ->
                        for(doc in docs) {
                            if(null == doc) continue
                            FirebaseFirestore.getInstance()
                                .collection("events")
                                .document(eventId)
                                .collection("participants")
                                .document(doc.id).delete().addOnSuccessListener {
                                    FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(userId)
                                        .collection("events").whereEqualTo("eventId", eventId).get().addOnSuccessListener { docs2 ->
                                            for(doc2 in docs2) {
                                                if(null == doc2) continue
                                                FirebaseFirestore.getInstance()
                                                    .collection("users")
                                                    .document(userId)
                                                    .collection("events")
                                                    .document(doc2.id).delete()
                                            }
                                        }
                                    mDataSet.removeAt(position)
                                    notifyDataSetChanged()
                                }
                        }
                    }
            }
        }
        else {
            viewHolder.deleteButton.visibility = View.GONE
        }
    }

    override fun getItemCount() = dataSet.size

    fun isPresent(iUserId: String) : Boolean {
        for(participant in dataSet) {
            if(participant.getUserId() == iUserId)
                return true
        }
        return false
    }

    /**
     * When a participant is displayed, check if the user is a friend of the current one
     * If it is, let the ghost icon unvisble.
     * If they are not yet friends, display the ghost and:
     *  Display a message on click to prevent current user they are not friends
     *  On user click, display the add friend panel.
     */
    private fun manageGhostIcon(iViewHolder: ViewHolder, iUserId: String) {
        // At first, retrieve the current user ID
        val currentUserId = FirebaseAuth.getInstance().currentUser!!.uid
        if(currentUserId == iUserId) return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUserId)
            .collection("friends")
            .whereEqualTo("userId", iUserId).get().addOnSuccessListener { docs ->
                // They are not friends, display the ghost
                Log.i("Database request", "Friend retrieved from EventParticipantListAdapter::manageGhostIcon")
                if(0 == docs.documents.size) {
                    iViewHolder.ghostView.visibility = View.VISIBLE
                    iViewHolder.ghostView.setOnClickListener {
                        Toast.makeText(iViewHolder.ghostView.context, iViewHolder.ghostView.context.getString(R.string.not_friend), Toast.LENGTH_SHORT).show()
                    }
                    iViewHolder.mainLayout.setOnClickListener {
                        displayAddFriendDialog(iViewHolder, iViewHolder.ghostView.context, iUserId)
                    }
                }
                else {
                    iViewHolder.ghostView.visibility = View.GONE
                    iViewHolder.mainLayout.setOnClickListener(null)
                }
        }
    }

    private fun displayAddFriendDialog(iViewHolder: ViewHolder, iContext: Context, iUserId: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(iUserId).get().addOnSuccessListener { doc ->
                Log.i("Database request", "User retrieved in EventParticipantListAdapter::displayAddFriendDialog - "+doc.id)
                val dialog = Dialog(iContext)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCancelable(false)
                dialog.setContentView(R.layout.dialog_homepage_add_friend)
                dialog.setCanceledOnTouchOutside(true)
                dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
                val userNameTextView = dialog.findViewById(R.id.user_name) as TextView
                userNameTextView.text = doc.data!!["displayName"].toString()

                if(userCacheInformation.containsKey(iUserId)) {
                    Glide.with(iContext).load( userCacheInformation[iUserId]!!.uri).into(dialog.findViewById(R.id.profile_picture))
                }
                else {
                    FirebaseFirestore.getInstance().collection("users")
                        .document(iUserId).get().addOnSuccessListener { doc1 ->
                            Log.i("Database request", "User retrieved in EventParticipantListAdapter::displayAddFriendDialog - "+doc1.id)
                            if(doc1.exists()) {
                                val userCache = MainActivity.Companion.UserInformation()
                                userCache.displayName = doc1["displayName"].toString()
                                userCache.identifiant = doc1["identifiant"].toString()
                                userCache.notificationToken = doc1["notificationToken"].toString()
                                val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                                    .child("profileImages")
                                    .child("$iUserId.jpeg")
                                storageRef.downloadUrl.addOnSuccessListener {
                                    Glide.with(iContext).load(it).into(dialog.findViewById(R.id.profile_picture))
                                    userCache.uri = it
                                }
                                userCacheInformation[iUserId] = userCache
                            }
                        }
                }

                val addFriendImageView = dialog.findViewById(R.id.add_friend) as ImageView
                val mainLayout = dialog.findViewById(R.id.main_layout) as ConstraintLayout
                addFriendImageView.setOnClickListener {
                    addFriendImageView.animate().rotation(180F).withEndAction {
                        mainLayout.animate().alpha(0F).withEndAction {
                            sendFriendRequest(iUserId)
                            manageGhostIcon(iViewHolder, iUserId)
                            dialog.dismiss()
                        }
                    }
                }
                dialog.show()
        }
    }

    private fun sendFriendRequest(iUserId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser!!.uid
        val currentUserData = hashMapOf(
            "userId" to iUserId,
            "request" to true,
            "status" to 1
        )
        FirebaseFirestore.getInstance().collection("users").document(currentUserId).collection("friends").add(currentUserData)

        val otherUserData = hashMapOf(
            "userId" to currentUserId,
            "request" to true,
            "status" to 2
        )
        FirebaseFirestore.getInstance().collection("users").document(iUserId).collection("friends")
            .add(otherUserData)
            .addOnSuccessListener { documentReference ->
                Log.d(ContentValues.TAG, "DocumentSnapshot written with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w(ContentValues.TAG, "Error adding document", e)
            }
    }

}