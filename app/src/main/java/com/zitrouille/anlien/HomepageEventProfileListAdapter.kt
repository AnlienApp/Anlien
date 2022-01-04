package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class HomepageEventProfileListAdapter(private val dataSet: ArrayList<HomepageEventProfile>) :
    RecyclerView.Adapter<HomepageEventProfileListAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val remainingProfileTextView: TextView = view.findViewById(R.id.remaining_profile)
        val profilePictureImageView: ImageView = view.findViewById(R.id.profile_picture)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_homepage_event_profile, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        if(0 != dataSet[position].getRemainingProfile()) {
            viewHolder.remainingProfileTextView.text = "+ " + dataSet[position].getRemainingProfile().toString()
            viewHolder.profilePictureImageView.visibility = View.GONE
        }
        else {
            viewHolder.remainingProfileTextView.visibility = View.GONE
            viewHolder.profilePictureImageView.visibility = View.VISIBLE

            val userId = dataSet[position].getUserId()
            val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                .child("profileImages")
                .child("$userId.jpeg")
            storageRef.downloadUrl.addOnSuccessListener {
                Glide.with(viewHolder.itemView.context).load(it).into(viewHolder.profilePictureImageView)
            }

        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

}