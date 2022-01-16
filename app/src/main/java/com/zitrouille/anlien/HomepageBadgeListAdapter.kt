package com.zitrouille.anlien

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class HomepageBadgeListAdapter(private val dataSet: ArrayList<HomepageBadge>) :
    RecyclerView.Adapter<HomepageBadgeListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val badgeImageView: ImageView = view.findViewById(R.id.badge)
        var badgeName : String = ""
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_homepage_badge, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.badgeName = dataSet[position].getName()
        Glide.with(viewHolder.itemView.context).load(MainActivity.retrieveBadgeLarge(viewHolder.badgeName ))
            .into(viewHolder.badgeImageView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            viewHolder.badgeImageView.tooltipText = MainActivity.retrieveBadgeName(viewHolder.badgeName , viewHolder.itemView.context)
        }
    }

    override fun getItemCount() = dataSet.size
}