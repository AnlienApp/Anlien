package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.zitrouille.anlien.MainActivity.Companion.globalUserInformations

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

        // At first, check if the profile picture has been already retrieved
        if(globalUserInformations.containsKey(userId)) {
            val uri = globalUserInformations[userId]!!.mUri
            if(null != uri) {
                Glide.with(viewHolder.itemView.context).load(uri).into(viewHolder.profilePicture)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                viewHolder.profilePicture.tooltipText = globalUserInformations[userId]!!.mDisplayName
            }
        }
        else {
            // FIREBASE - FIRESTORE | FIRESTORAGE
            val newUserRetrieved = MainActivity.Companion.UserInformation()
            val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                .child("profileImages")
                .child("$userId.jpeg")
            storageRef.downloadUrl.addOnSuccessListener {
                Glide.with(viewHolder.itemView.context).load(it).into(viewHolder.profilePicture)
                newUserRetrieved.mUri = it
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId).get().addOnSuccessListener { userDocument ->
                        newUserRetrieved.mDisplayName = userDocument["displayName"].toString()
                        newUserRetrieved.mUniqueId = userDocument["uniquePseudo"].toString()
                        viewHolder.profilePicture.tooltipText = newUserRetrieved.mDisplayName
                    }
            }
            globalUserInformations[userId] = newUserRetrieved
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