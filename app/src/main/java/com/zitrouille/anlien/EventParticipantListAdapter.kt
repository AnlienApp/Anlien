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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.zitrouille.anlien.MainActivity.Companion.globalUserInformations

class EventParticipantListAdapter(private val dataSet: ArrayList<EventParticipant>) :
    RecyclerView.Adapter<EventParticipantListAdapter.ViewHolder>() {

    private val mDataSet = dataSet

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val displayNameTextView: TextView = view.findViewById(R.id.name)
        val uniquePseudoTextView: TextView = view.findViewById(R.id.identifiant)
        val profilePictureImageView: ImageView = view.findViewById(R.id.profile_picture)

        val validImageView: ImageView = view.findViewById(R.id.valid)
        val questionImageView: ImageView = view.findViewById(R.id.question)
        val cancelImageView: ImageView = view.findViewById(R.id.cancel)

        val deleteButton: ImageView = view.findViewById(R.id.delete)

    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_event_participant, viewGroup, false)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val userId = dataSet[position].getUserId()
        val eventId = dataSet[position].getEventId()
        if(globalUserInformations.containsKey(userId)) {
            viewHolder.displayNameTextView.text = globalUserInformations[userId]!!.mDisplayName
            viewHolder.uniquePseudoTextView.text = globalUserInformations[userId]!!.mUniqueId
            if(null != globalUserInformations[userId]!!.mUri) {
                Glide.with(viewHolder.itemView.context).load(globalUserInformations[userId]!!.mUri)
                    .into(viewHolder.profilePictureImageView)
            }
        }
        FirebaseFirestore.getInstance().collection("users").document(userId).get().addOnSuccessListener { doc ->
            val newUserRetrieved = MainActivity.Companion.UserInformation()
            newUserRetrieved.mDisplayName = doc["displayName"].toString()
            newUserRetrieved.mUniqueId = doc["uniquePseudo"].toString()

            viewHolder.displayNameTextView.text = newUserRetrieved.mDisplayName
            viewHolder.uniquePseudoTextView.text = newUserRetrieved.mUniqueId

            val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                .child("profileImages")
                .child("$userId.jpeg")
            storageRef.downloadUrl.addOnSuccessListener {
                Glide.with(viewHolder.itemView.context).load(it)
                    .into(viewHolder.profilePictureImageView)
                if (globalUserInformations.containsKey(userId)) {
                    globalUserInformations[userId]!!.mUri = it
                }
            }
            globalUserInformations[userId] = newUserRetrieved
        }

        val status =  dataSet[position].getStatus()
        if(0L == status) {
            viewHolder.validImageView.visibility = View.VISIBLE
        }
        else if(1L == status) {
            viewHolder.questionImageView.visibility = View.VISIBLE
        }
        else if(2L == status) {
            viewHolder.cancelImageView.visibility = View.VISIBLE
        }

        // More menu management
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
                                    mDataSet.removeAt(viewHolder.position)
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

}