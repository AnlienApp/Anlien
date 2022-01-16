package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.zitrouille.anlien.MainActivity.Companion.userCacheInformation

class HomepageEventProfileListAdapter(private val dataSet: ArrayList<HomepageEventProfile>) :
    RecyclerView.Adapter<HomepageEventProfileListAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val remainingProfileTextView: TextView = view.findViewById(R.id.remaining_profile)
        val profilePictureImageView: ImageView = view.findViewById(R.id.profile_picture)
        val badge: ImageView = view.findViewById(R.id.badge)
        val profilePictureLightImageView: ImageView = view.findViewById(R.id.profile_picture_light)
        val badgeLight: ImageView = view.findViewById(R.id.badge_light)

        val normalVisu: ConstraintLayout = view.findViewById(R.id.normal_visu)
        val lightVisu: ConstraintLayout = view.findViewById(R.id.light_visu)
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

        val bLightVisu = dataSet[position].getLightVisu()
        if(bLightVisu) {
            viewHolder.normalVisu.visibility = View.GONE
            viewHolder.lightVisu.visibility = View.VISIBLE
        }
        else {
            viewHolder.normalVisu.visibility = View.VISIBLE
            viewHolder.lightVisu.visibility = View.GONE
        }

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        if(0 != dataSet[position].getRemainingProfile()) {
            viewHolder.remainingProfileTextView.text = "+ " + dataSet[position].getRemainingProfile().toString()
            viewHolder.profilePictureImageView.visibility = View.GONE
            viewHolder.profilePictureLightImageView.visibility = View.GONE
        }
        else {
            viewHolder.remainingProfileTextView.visibility = View.GONE
            if(bLightVisu) {
                viewHolder.profilePictureLightImageView.visibility = View.VISIBLE
                viewHolder.profilePictureImageView.visibility = View.GONE
            }
            else {
                viewHolder.profilePictureLightImageView.visibility = View.GONE
                viewHolder.profilePictureImageView.visibility = View.VISIBLE
            }

            val userId = dataSet[position].getUserId()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if(userCacheInformation.containsKey(userId)) {
                    if(bLightVisu) {
                        viewHolder.profilePictureLightImageView.tooltipText =
                            userCacheInformation[userId]!!.displayName
                    }
                    else {
                        viewHolder.profilePictureImageView.tooltipText =
                            userCacheInformation[userId]!!.displayName
                    }
                }
            }

            if(userCacheInformation.containsKey(userId)) {
                if(bLightVisu) {
                    Glide.with(viewHolder.itemView.context).load(userCacheInformation[userId]!!.uri)
                        .into(viewHolder.profilePictureLightImageView)
                }
                else {
                    Glide.with(viewHolder.itemView.context).load(userCacheInformation[userId]!!.uri)
                        .into(viewHolder.profilePictureImageView)
                }
                if("none" != userCacheInformation[userId]!!.displayedBadge) {
                    if(bLightVisu) {
                        Glide.with(viewHolder.itemView.context)
                            .load(MainActivity.retrieveBadge(userCacheInformation[userId]!!.displayedBadge))
                            .into(viewHolder.badgeLight)
                    }
                    else {
                        Glide.with(viewHolder.itemView.context)
                            .load(MainActivity.retrieveBadge(userCacheInformation[userId]!!.displayedBadge))
                            .into(viewHolder.badge)
                    }
                }
            }
            else {
                if(bLightVisu) {
                    MainActivity.retrieveUserInformation(
                        userId,
                        null,
                        null,
                        viewHolder.profilePictureLightImageView,
                        viewHolder.badgeLight
                    )
                }
                else {
                    MainActivity.retrieveUserInformation(
                        userId,
                        null,
                        null,
                        viewHolder.profilePictureImageView,
                        viewHolder.badge
                    )
                }
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

}