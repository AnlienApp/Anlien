package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.util.Log
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
import com.zitrouille.anlien.MainActivity.Companion.userCacheInformation

class CreateEventFriendListAdapter(private val dataSet: ArrayList<CreateEventFriend>) :
    RecyclerView.Adapter<CreateEventFriendListAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val displayNameTextView: TextView = view.findViewById(R.id.name)
        val uniqueIdentifiantTextView: TextView = view.findViewById(R.id.identifiant)
        val profilePicture: ImageView = view.findViewById(R.id.profile_picture)
        val badge: ImageView = view.findViewById(R.id.badge)

        val addParticipantImageView: ImageView = view.findViewById(R.id.add_participant)
        val removeParticipantImageView: ImageView = view.findViewById(R.id.remove_participant)

        val mainLayout: ConstraintLayout = view.findViewById(R.id.main_layout)
        var animationIsRunning = false
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_create_event_friend, viewGroup, false)
        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val userId = dataSet[position].getUserId()

        if(userCacheInformation.containsKey(userId)) {
            viewHolder.displayNameTextView.text = userCacheInformation[userId]!!.displayName
            viewHolder.uniqueIdentifiantTextView.text = userCacheInformation[userId]!!.identifiant
            Glide.with(viewHolder.itemView.context).load(userCacheInformation[userId]!!.uri)
                .into(viewHolder.profilePicture)
            if("none" != userCacheInformation[userId]!!.displayedBadge)
                Glide.with(viewHolder.itemView.context).load(MainActivity.retrieveBadge(userCacheInformation[userId]!!.displayedBadge)).into(viewHolder.badge)
        }
        else {
            MainActivity.retrieveUserInformation(userId,
                viewHolder.displayNameTextView,
                viewHolder.uniqueIdentifiantTextView,
                viewHolder.profilePicture,
                viewHolder.badge,
            )
        }

        viewHolder.mainLayout.setOnClickListener {
            if(!viewHolder.animationIsRunning)
            if(View.VISIBLE == viewHolder.removeParticipantImageView.visibility) {
                removeAnimation(viewHolder)
                dataSet[position].setSelected(false)
            }
            else {
                addAnimation(viewHolder)
                dataSet[position].setSelected(true)
            }
        }

        if(dataSet[position].getSelected()) {
            viewHolder.removeParticipantImageView.visibility = View.VISIBLE
            viewHolder.removeParticipantImageView.alpha = 1.0f
            viewHolder.addParticipantImageView.visibility = View.GONE
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    fun getSelectionStatus(iUserId: String) : Boolean {
        for(data in dataSet) {
            if(data.getUserId() == iUserId)
                return data.getSelected()
        }
        return false
    }

    private fun addAnimation(viewHolder: ViewHolder) {
        viewHolder.animationIsRunning = true
        viewHolder.removeParticipantImageView.visibility = View.VISIBLE
        viewHolder.addParticipantImageView.visibility = View.GONE
        viewHolder.removeParticipantImageView.animate().alpha(1.0f).rotation(viewHolder.removeParticipantImageView.rotation+360.0f).withEndAction {
            viewHolder.animationIsRunning = false
        }
    }

    private fun removeAnimation(viewHolder: ViewHolder) {
        viewHolder.animationIsRunning = true
        viewHolder.removeParticipantImageView.animate().alpha(0.0f).rotation(viewHolder.removeParticipantImageView.rotation+360.0f).withEndAction {
            viewHolder.addParticipantImageView.visibility = View.VISIBLE
            viewHolder.removeParticipantImageView.visibility = View.GONE
            viewHolder.animationIsRunning = false
        }
    }

}