package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ArrayAdapter
import android.widget.TextView
import kotlin.collections.ArrayList
import android.view.*
import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.PopupMenu
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream


class HomepageFriendListAdapter(private val iContext : Activity, private val iArrayList: ArrayList<HomepageFriend>):
    ArrayAdapter<HomepageFriend>(iContext, R.layout.item_homepage_friend, iArrayList) {

    @SuppressLint("ViewHolder", "InflateParams")
    override fun getView(iPosition: Int, iConvertView: View?, iParent: ViewGroup): View {
        val inflater: LayoutInflater = LayoutInflater.from(iContext)
        val view: View = inflater.inflate(R.layout.item_homepage_friend, null)
        view.findViewById<TextView>(R.id.name).text = iArrayList[iPosition].getName()

        /**
         * Display profile picture if it exists
         */
        val profilePictureRef = FirebaseStorage.getInstance().reference.child("profileImages/"+iArrayList[iPosition].getFriendId()+".jpeg")
        profilePictureRef.downloadUrl.addOnSuccessListener {
            Glide.with(iContext).load(it).into(view.findViewById(R.id.profile_picture))
        }

        /**
         * When user click on more menu, create popup window
         */
        view.findViewById<ImageView>(R.id.more).setOnClickListener {
            val popup = PopupMenu(context, it)
            popup.setOnMenuItemClickListener { it1 ->
                when(it1.itemId) {
                    R.id.delete -> {
                        deleteFriend(view, iPosition)
                    }
                }
                true
            }
            popup.menuInflater.inflate(R.menu.homepage_friend_more_menu, popup.menu)
            popup.show()
        }

        if(iArrayList[iPosition].getRequest() && !iArrayList[iPosition].getShouldBeValid()) {
            /**
             * Current user cannot valid the friend request
             */
            view.findViewById<ImageView>(R.id.request).visibility = View.VISIBLE
        }
        else if(iArrayList[iPosition].getRequest() && iArrayList[iPosition].getShouldBeValid()) {
            /**
             * Current user can valid the friend request
             */
            view.findViewById<ImageView>(R.id.valid).visibility = View.VISIBLE
            view.findViewById<ImageView>(R.id.cancel).visibility = View.VISIBLE
            view.findViewById<ImageView>(R.id.more).visibility = View.GONE

            view.findViewById<ImageView>(R.id.valid).setOnClickListener {
                val auth = FirebaseAuth.getInstance()
                val database = FirebaseFirestore.getInstance()

                database.collection("users")
                    .document(auth.currentUser!!.uid)
                    .collection("friends")
                    .whereEqualTo("userId", iArrayList[iPosition].getFriendId())
                    .get().addOnSuccessListener { documents ->
                        if (documents.size() != 0) {
                            val information = hashMapOf(
                                "request" to false,
                            )
                            database.collection("users")
                                .document(auth.currentUser!!.uid)
                                .collection("friends")
                                .document(documents.documents[0].id)
                                .update(information as Map<String, Any>).addOnSuccessListener {
                                    view.findViewById<ImageView>(R.id.valid).visibility = View.GONE
                                    view.findViewById<ImageView>(R.id.cancel).visibility = View.GONE
                                    view.findViewById<ImageView>(R.id.more).visibility = View.VISIBLE
                                }
                        }
                    }

                database.collection("users")
                    .document(iArrayList[iPosition].getFriendId())
                    .collection("friends")
                    .whereEqualTo("userId", auth.currentUser!!.uid)
                    .get().addOnSuccessListener { documents ->
                        if (documents.size() != 0) {
                            val information = hashMapOf(
                                "request" to false,
                            )
                            database.collection("users")
                                .document(iArrayList[iPosition].getFriendId())
                                .collection("friends")
                                .document(documents.documents[0].id)
                                .update(information as Map<String, Any>).addOnSuccessListener {
                                    view.findViewById<ImageView>(R.id.valid).visibility = View.GONE
                                    view.findViewById<ImageView>(R.id.cancel).visibility = View.GONE
                                    view.findViewById<ImageView>(R.id.more).visibility = View.VISIBLE
                                }
                        }
                    }
            }

            view.findViewById<ImageView>(R.id.cancel).setOnClickListener {
                deleteFriend(view, iPosition)
            }

        }
        return view
    }

    private fun deleteFriend(iView: View, iPosition: Int) {
        val auth = FirebaseAuth.getInstance()
        val database = FirebaseFirestore.getInstance()

        database.collection("users")
            .document(auth.currentUser!!.uid)
            .collection("friends")
            .whereEqualTo("userId", iArrayList[iPosition].getFriendId())
            .get().addOnSuccessListener { documents ->
                if (documents.size() != 0) {
                    database.collection("users")
                        .document(auth.currentUser!!.uid)
                        .collection("friends")
                        .document(documents.documents[0].id)
                        .delete().addOnSuccessListener {
                            iView.visibility = View.GONE
                            (iContext as HomepageActivity).retrieveFriendList()
                        }
                }
            }

        database.collection("users")
            .document(iArrayList[iPosition].getFriendId())
            .collection("friends")
            .whereEqualTo("userId", auth.currentUser!!.uid)
            .get().addOnSuccessListener { documents ->
                if (documents.size() != 0) {
                    database.collection("users")
                        .document(iArrayList[iPosition].getFriendId())
                        .collection("friends")
                        .document(documents.documents[0].id)
                        .delete().addOnSuccessListener {
                            iView.visibility = View.GONE
                            (iContext as HomepageActivity).retrieveFriendList()
                        }
                }
            }
    }

}