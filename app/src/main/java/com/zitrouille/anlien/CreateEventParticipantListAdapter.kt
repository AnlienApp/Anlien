package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class CreateEventParticipantListAdapter(private val dataSet: ArrayList<CreateEventParticipant>) :
    RecyclerView.Adapter<CreateEventParticipantListAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profilePicture: ImageView = view.findViewById(R.id.profile_picture)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_create_event_participant, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val userId = dataSet[position].getUserId()
        val storageRef: StorageReference = FirebaseStorage.getInstance().reference
            .child("profileImages")
            .child("$userId.jpeg")
        storageRef.downloadUrl.addOnSuccessListener {
            Glide.with(viewHolder.itemView.context).load(it).into(viewHolder.profilePicture)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    fun isPresent(iUserId: String) : Boolean {
        for(participant in dataSet) {
            if(participant.getUserId() == iUserId)
                return true
        }
        return false
    }

}