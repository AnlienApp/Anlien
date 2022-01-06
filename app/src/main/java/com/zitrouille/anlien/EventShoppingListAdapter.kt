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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.zitrouille.anlien.MainActivity.Companion.globalUserInformations

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
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_event_shopping, viewGroup, false)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.ownerId = dataSet[position].getOwner()
        viewHolder.nameTextView.text = dataSet[position].getName()

        if(dataSet[position].getOwner().isNotBlank() && dataSet[position].getOwner().isNotEmpty()) {
            val bCache = globalUserInformations.containsKey(viewHolder.ownerId)
            if(bCache) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    viewHolder.profilePictureImageView.tooltipText = globalUserInformations[viewHolder.ownerId]!!.mDisplayName
                    viewHolder.identifiantTextView.text = globalUserInformations[viewHolder.ownerId]!!.mUniqueId
                }
                else {
                    FirebaseFirestore.getInstance().collection("users").document(viewHolder.ownerId).get().addOnSuccessListener { doc ->
                        val newUserRetrieved = MainActivity.Companion.UserInformation()
                        newUserRetrieved.mDisplayName = doc["displayName"].toString()
                        newUserRetrieved.mUniqueId = doc["uniquePseudo"].toString()
                        globalUserInformations[viewHolder.ownerId] = newUserRetrieved
                        viewHolder.identifiantTextView.text = globalUserInformations[viewHolder.ownerId]!!.mUniqueId
                    }
                }
            }
            else {
                FirebaseFirestore.getInstance().collection("users").document(viewHolder.ownerId).get().addOnSuccessListener { doc ->
                    val newUserRetrieved = MainActivity.Companion.UserInformation()
                    newUserRetrieved.mDisplayName = doc["displayName"].toString()
                    newUserRetrieved.mUniqueId = doc["uniquePseudo"].toString()
                    globalUserInformations[viewHolder.ownerId] = newUserRetrieved
                    viewHolder.identifiantTextView.text = globalUserInformations[viewHolder.ownerId]!!.mUniqueId
                }
            }

            if(bCache && null != globalUserInformations[viewHolder.ownerId]!!.mUri) {
                Glide.with(viewHolder.itemView.context).load(globalUserInformations[viewHolder.ownerId]!!.mUri).into(viewHolder.profilePictureImageView)
            }
            else {
                val ownerId = viewHolder.ownerId
                val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                    .child("profileImages")
                    .child("$ownerId.jpeg")
                storageRef.downloadUrl.addOnSuccessListener {
                    Glide.with(viewHolder.itemView.context).load(it).into(viewHolder.profilePictureImageView)
                    if(globalUserInformations.containsKey(viewHolder.ownerId)) {
                        globalUserInformations[viewHolder.ownerId]!!.mUri = it
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
                        dataSet.removeAt(viewHolder.position)
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
                .document(dataSet[iPosition].getId()).update(shoppingItemData as Map<String, Any>).addOnSuccessListener {
                    if(globalUserInformations.containsKey(currentUserId) && null != globalUserInformations[currentUserId]!!.mUri) {
                        iViewHolder.profilePictureImageView.animate().rotation(iViewHolder.profilePictureImageView.rotation+360.0f).withEndAction {
                            Glide.with(iViewHolder.itemView.context).load(globalUserInformations[currentUserId]!!.mUri).into(iViewHolder.profilePictureImageView)
                        }
                    }
                    else {
                        val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                            .child("profileImages")
                            .child("$currentUserId.jpeg")
                        storageRef.downloadUrl.addOnSuccessListener {
                            iViewHolder.profilePictureImageView.animate().rotation(iViewHolder.profilePictureImageView.rotation+360.0f).withEndAction {
                                Glide.with(iViewHolder.itemView.context).load(it).into(iViewHolder.profilePictureImageView)
                            }
                            if(globalUserInformations.containsKey(currentUserId)) {
                                globalUserInformations[currentUserId]!!.mUri = it
                            }
                        }
                    }
                }


            val bCache = globalUserInformations.containsKey(iViewHolder.ownerId)
            if(bCache) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    iViewHolder.profilePictureImageView.tooltipText = globalUserInformations[iViewHolder.ownerId]!!.mDisplayName
                    iViewHolder.identifiantTextView.text = globalUserInformations[iViewHolder.ownerId]!!.mUniqueId
                }
                else {
                    FirebaseFirestore.getInstance().collection("users").document(iViewHolder.ownerId).get().addOnSuccessListener { doc ->
                        val newUserRetrieved = MainActivity.Companion.UserInformation()
                        newUserRetrieved.mDisplayName = doc["displayName"].toString()
                        newUserRetrieved.mUniqueId = doc["uniquePseudo"].toString()
                        globalUserInformations[iViewHolder.ownerId] = newUserRetrieved
                        iViewHolder.identifiantTextView.text = globalUserInformations[iViewHolder.ownerId]!!.mUniqueId
                    }
                }
            }
            else {
                FirebaseFirestore.getInstance().collection("users").document(iViewHolder.ownerId).get().addOnSuccessListener { doc ->
                    val newUserRetrieved = MainActivity.Companion.UserInformation()
                    newUserRetrieved.mDisplayName = doc["displayName"].toString()
                    newUserRetrieved.mUniqueId = doc["uniquePseudo"].toString()
                    globalUserInformations[iViewHolder.ownerId] = newUserRetrieved
                    iViewHolder.identifiantTextView.text = globalUserInformations[iViewHolder.ownerId]!!.mUniqueId
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
        }
    }

}