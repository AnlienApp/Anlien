package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.util.Log
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
import com.zitrouille.anlien.MainActivity.Companion.userCacheInformation

class EventShoppingListAdapter(private val dataSet: ArrayList<EventShopping>) :
    RecyclerView.Adapter<EventShoppingListAdapter.ViewHolder>() {

    private var mUserId = FirebaseAuth.getInstance().currentUser!!.uid

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.name)
        val identifiantTextView: TextView = view.findViewById(R.id.identifiant)
        val profilePictureImageView: ImageView = view.findViewById(R.id.profile_picture)
        val mainLayout: ConstraintLayout = view.findViewById(R.id.main_layout)

        val deleteButton: ImageView = view.findViewById(R.id.delete)

        var ownerId: String = ""
        var eventOrganizerId: String = ""
        var eventName: String = ""
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_event_shopping, viewGroup, false)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.ownerId = dataSet[position].getOwner()
        viewHolder.eventOrganizerId = dataSet[position].getEventOrganizerId()
        viewHolder.eventName = dataSet[position].getEventName()
        viewHolder.nameTextView.text = dataSet[position].getName()

        if(dataSet[position].getOwner().isNotBlank() && dataSet[position].getOwner().isNotEmpty()) {

            if(userCacheInformation.containsKey(viewHolder.ownerId)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    viewHolder.profilePictureImageView.tooltipText =
                        userCacheInformation[viewHolder.ownerId]!!.displayName
                }
                viewHolder.identifiantTextView.text = userCacheInformation[viewHolder.ownerId]!!.identifiant
                Glide.with(viewHolder.itemView.context).load(userCacheInformation[viewHolder.ownerId]!!.uri).into(viewHolder.profilePictureImageView)
            }
            else {
                val userId = viewHolder.ownerId
                FirebaseFirestore.getInstance().collection("users")
                    .document(userId).get().addOnSuccessListener { doc ->
                        if(doc.exists()) {
                            Log.i("Database request", "User retrieved in EventShoppingListAdapter::onBindViewHolder - "+doc.id)
                            val userCache = MainActivity.Companion.UserInformation()
                            userCache.displayName = doc["displayName"].toString()
                            userCache.identifiant = doc["identifiant"].toString()
                            userCache.notificationToken = doc["notificationToken"].toString()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                viewHolder.profilePictureImageView.tooltipText =
                                    userCacheInformation[viewHolder.ownerId]!!.displayName
                            }
                            viewHolder.identifiantTextView.text = userCacheInformation[viewHolder.ownerId]!!.identifiant
                            val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                                .child("profileImages")
                                .child("$userId.jpeg")
                            storageRef.downloadUrl.addOnSuccessListener {
                                Glide.with(viewHolder.itemView.context).load(it).into(viewHolder.profilePictureImageView)
                                userCache.uri = it
                            }
                            userCacheInformation[userId] = userCache
                        }
                    }
            }
        }
        else {
            viewHolder.identifiantTextView.text = viewHolder.itemView.context.getString(R.string.touche_to_take)
        }

        viewHolder.mainLayout.setOnClickListener {
            switchOwner(viewHolder, position)
        }
        viewHolder.profilePictureImageView.setOnClickListener {
            switchOwner(viewHolder, position)
        }

        if(dataSet[position].getMoreMenu() || mUserId == dataSet[position].getCreatorId()) {
            viewHolder.deleteButton.visibility = View.VISIBLE
            viewHolder.deleteButton.setOnClickListener {
                FirebaseFirestore.getInstance()
                    .collection("events")
                    .document(dataSet[position].getEventId())
                    .collection("shopping")
                    .document(dataSet[position].getId()).delete().addOnSuccessListener {
                        dataSet.removeAt(position)
                        notifyDataSetChanged()
                    }
            }
        }
    }

    override fun getItemCount() = dataSet.size

    private fun switchOwner(iViewHolder: ViewHolder, iPosition: Int) {
        val currentUserId = FirebaseAuth.getInstance().currentUser!!.uid
        if(null == iViewHolder.profilePictureImageView.drawable) {
            iViewHolder.ownerId = currentUserId
            dataSet[iPosition].setOwner(currentUserId)
            val shoppingItemData = hashMapOf(
                "owner" to currentUserId,
            )
            FirebaseFirestore.getInstance()
                .collection("events")
                .document(dataSet[iPosition].getEventId())
                .collection("shopping")
                .document(dataSet[iPosition].getId()).update(shoppingItemData as Map<String, Any>)
                .addOnSuccessListener {
                    if (userCacheInformation.containsKey(currentUserId)) {
                        iViewHolder.profilePictureImageView.animate()
                            .rotation(iViewHolder.profilePictureImageView.rotation + 360.0f)
                            .withEndAction {
                                Glide.with(iViewHolder.itemView.context)
                                    .load(userCacheInformation[currentUserId]!!.uri)
                                    .into(iViewHolder.profilePictureImageView)
                            }
                    } else {
                        FirebaseFirestore.getInstance().collection("users")
                            .document(currentUserId).get().addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    val userCache = MainActivity.Companion.UserInformation()
                                    userCache.displayName = doc["displayName"].toString()
                                    userCache.identifiant = doc["identifiant"].toString()
                                    userCache.notificationToken =
                                        doc["notificationToken"].toString()
                                    val storageRef: StorageReference =
                                        FirebaseStorage.getInstance().reference
                                            .child("profileImages")
                                            .child("$currentUserId.jpeg")
                                    storageRef.downloadUrl.addOnSuccessListener {
                                        iViewHolder.profilePictureImageView.animate()
                                            .rotation(iViewHolder.profilePictureImageView.rotation + 360.0f)
                                            .withEndAction {
                                                Glide.with(iViewHolder.itemView.context).load(it)
                                                    .into(iViewHolder.profilePictureImageView)
                                            }
                                        userCache.uri = it
                                    }
                                    userCacheInformation[currentUserId] = userCache
                                }
                            }
                    }
                }


            if (userCacheInformation.containsKey(currentUserId)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    iViewHolder.profilePictureImageView.tooltipText =
                        userCacheInformation[iViewHolder.ownerId]!!.displayName
                }
                iViewHolder.identifiantTextView.text =
                    userCacheInformation[iViewHolder.ownerId]!!.identifiant

                // Notification to the organizer
                if (userCacheInformation.containsKey(iViewHolder.eventOrganizerId)) {
                    val notification =
                        FirebaseNotificationSender(
                            userCacheInformation[iViewHolder.eventOrganizerId]!!.notificationToken,
                            iViewHolder.eventName,
                            userCacheInformation[currentUserId]!!.displayName + " s'occupe de " + iViewHolder.nameTextView.text,
                            iViewHolder.itemView.context as Activity
                        )
                    notification.SendNotification()
                }
                else {
                    val organizerId = iViewHolder.eventOrganizerId
                    FirebaseFirestore.getInstance().collection("users")
                        .document(organizerId).get().addOnSuccessListener { doc ->
                            if(doc.exists()) {
                                Log.i("Database request", "User retrieved in EventShoppingListAdapter::switchOwner - "+doc.id)
                                val userCache = MainActivity.Companion.UserInformation()
                                userCache.displayName = doc["displayName"].toString()
                                userCache.identifiant = doc["identifiant"].toString()
                                userCache.notificationToken = doc["notificationToken"].toString()

                                val notification =
                                    FirebaseNotificationSender(
                                        userCache.notificationToken,
                                        iViewHolder.eventName,
                                        userCache.displayName + " s'occupe de " + iViewHolder.nameTextView.text,
                                        iViewHolder.itemView.context as Activity
                                    )
                                notification.SendNotification()

                                val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                                    .child("profileImages")
                                    .child("$organizerId.jpeg")
                                storageRef.downloadUrl.addOnSuccessListener {
                                    userCache.uri = it
                                }
                                userCacheInformation[organizerId] = userCache
                            }
                        }
                }
            }
            else {
                FirebaseFirestore.getInstance().collection("users")
                    .document(currentUserId).get().addOnSuccessListener { doc ->
                        if(doc.exists()) {
                            Log.i("Database request", "User retrieved in EventShoppingListAdapter::switchOwner - "+doc.id)
                            val userCache = MainActivity.Companion.UserInformation()
                            userCache.displayName = doc["displayName"].toString()
                            userCache.identifiant = doc["identifiant"].toString()
                            userCache.notificationToken = doc["notificationToken"].toString()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                iViewHolder.profilePictureImageView.tooltipText = userCache.displayName
                            }
                            iViewHolder.identifiantTextView.text = userCache.identifiant
                            val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                                .child("profileImages")
                                .child("$currentUserId.jpeg")
                            storageRef.downloadUrl.addOnSuccessListener {
                                userCache.uri = it
                            }
                            userCacheInformation[currentUserId] = userCache

                            // Notification to the organizer
                            if(currentUserId != iViewHolder.eventOrganizerId) {
                                if (userCacheInformation.containsKey(iViewHolder.eventOrganizerId)) {
                                    val notification =
                                        FirebaseNotificationSender(
                                            userCacheInformation[iViewHolder.eventOrganizerId]!!.notificationToken,
                                            iViewHolder.eventName,
                                            userCacheInformation[currentUserId]!!.displayName + " s'occupe de " + iViewHolder.nameTextView.text,
                                            iViewHolder.itemView.context as Activity
                                        )
                                    notification.SendNotification()
                                } else {
                                    val organizerId = iViewHolder.eventOrganizerId
                                    FirebaseFirestore.getInstance().collection("users")
                                        .document(organizerId).get().addOnSuccessListener { doc1 ->
                                            if (doc1.exists()) {
                                                Log.i(
                                                    "Database request",
                                                    "User retrieved in EventShoppingListAdapter::switchOwner - " + doc1.id
                                                )
                                                val userCache1 =
                                                    MainActivity.Companion.UserInformation()
                                                userCache1.displayName =
                                                    doc1["displayName"].toString()
                                                userCache1.identifiant =
                                                    doc1["identifiant"].toString()
                                                userCache1.notificationToken =
                                                    doc1["notificationToken"].toString()

                                                val notification =
                                                    FirebaseNotificationSender(
                                                        userCache1.notificationToken,
                                                        iViewHolder.eventName,
                                                        userCache1.displayName + " s'occupe de " + iViewHolder.nameTextView.text,
                                                        iViewHolder.itemView.context as Activity
                                                    )
                                                notification.SendNotification()

                                                val storageRef1: StorageReference =
                                                    FirebaseStorage.getInstance().reference
                                                        .child("profileImages")
                                                        .child("$organizerId.jpeg")
                                                storageRef1.downloadUrl.addOnSuccessListener {
                                                    userCache1.uri = it
                                                }
                                                userCacheInformation[organizerId] = userCache1
                                            }
                                        }
                                }
                            }
                        }
                    }
            }
        }
        else if(currentUserId ==  iViewHolder.ownerId) {
            val shoppingItemData = hashMapOf(
                "owner" to "",
            )
            FirebaseFirestore.getInstance()
                .collection("events")
                .document(dataSet[iPosition].getEventId())
                .collection("shopping")
                .document(dataSet[iPosition].getId()).update(shoppingItemData as Map<String, Any>).addOnSuccessListener {
                    iViewHolder.profilePictureImageView.animate().rotation(iViewHolder.profilePictureImageView.rotation+360.0f).withEndAction {
                        Glide.with(iViewHolder.itemView.context).load("").into(iViewHolder.profilePictureImageView)
                        iViewHolder.identifiantTextView.text = iViewHolder.itemView.context.getString(R.string.touche_to_take)
                    }
                }

            // Notification to the organizer
            if(currentUserId != iViewHolder.eventOrganizerId) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    iViewHolder.profilePictureImageView.tooltipText =
                        userCacheInformation[iViewHolder.ownerId]!!.displayName
                }
                iViewHolder.identifiantTextView.text =
                    userCacheInformation[iViewHolder.ownerId]!!.identifiant

                // Notification to the organizer
                if (userCacheInformation.containsKey(iViewHolder.eventOrganizerId)) {
                    val notification =
                        FirebaseNotificationSender(
                            userCacheInformation[iViewHolder.eventOrganizerId]!!.notificationToken,
                            iViewHolder.eventName,
                            userCacheInformation[currentUserId]!!.displayName + " ne s'occupe plus de " + iViewHolder.nameTextView.text,
                            iViewHolder.itemView.context as Activity
                        )
                    notification.SendNotification()
                } else {
                    val organizerId = iViewHolder.eventOrganizerId
                    FirebaseFirestore.getInstance().collection("users")
                        .document(organizerId).get().addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                Log.i(
                                    "Database request",
                                    "User retrieved in EventShoppingListAdapter::switchOwner - " + doc.id
                                )
                                val userCache = MainActivity.Companion.UserInformation()
                                userCache.displayName = doc["displayName"].toString()
                                userCache.identifiant = doc["identifiant"].toString()
                                userCache.notificationToken =
                                    doc["notificationToken"].toString()

                                val notification =
                                    FirebaseNotificationSender(
                                        userCache.notificationToken,
                                        iViewHolder.eventName,
                                        userCache.displayName + " ne s'occupe plus de " + iViewHolder.nameTextView.text,
                                        iViewHolder.itemView.context as Activity
                                    )
                                notification.SendNotification()

                                val storageRef: StorageReference =
                                    FirebaseStorage.getInstance().reference
                                        .child("profileImages")
                                        .child("$organizerId.jpeg")
                                storageRef.downloadUrl.addOnSuccessListener {
                                    userCache.uri = it
                                }
                                userCacheInformation[organizerId] = userCache
                            }
                        }
                }
            }
        }
    }

}