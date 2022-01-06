package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import com.zitrouille.anlien.MainActivity.Companion.globalUserInformations
import android.widget.ArrayAdapter
import android.widget.TextView
import kotlin.collections.ArrayList
import android.view.*
import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import android.content.DialogInterface





class HomepageFriendListAdapter(private val iContext : Activity, private val iArrayList: ArrayList<HomepageFriend>):
    ArrayAdapter<HomepageFriend>(iContext, R.layout.item_homepage_friend, iArrayList) {

    @SuppressLint("ViewHolder", "InflateParams")
    override fun getView(iPosition: Int, iConvertView: View?, iParent: ViewGroup): View {
        val inflater: LayoutInflater = LayoutInflater.from(iContext)
        val view: View = inflater.inflate(R.layout.item_homepage_friend, null)
        view.findViewById<TextView>(R.id.name).text = iArrayList[iPosition].getName()
        view.findViewById<TextView>(R.id.identifiant).text = iArrayList[iPosition].getUniquePseudo()

        /**
         * Display profile picture if it exists
         */
        if(globalUserInformations.containsKey(iArrayList[iPosition].getFriendId()) && null != globalUserInformations[iArrayList[iPosition].getFriendId()]!!.mUri) {
            Glide.with(iContext).load(globalUserInformations[iArrayList[iPosition].getFriendId()]!!.mUri).into(view.findViewById(R.id.profile_picture))
        }
        else {
            val profilePictureRef =
                FirebaseStorage.getInstance().reference.child("profileImages/" + iArrayList[iPosition].getFriendId() + ".jpeg")
            profilePictureRef.downloadUrl.addOnSuccessListener {
                Glide.with(iContext).load(it).into(view.findViewById(R.id.profile_picture))
                if(globalUserInformations.containsKey(iArrayList[iPosition].getFriendId())) {
                    globalUserInformations[iArrayList[iPosition].getFriendId()]!!.mUri = it
                }
            }
        }

        val user: HomepageFriend = iArrayList[iPosition]
        // If the profile has been retrieved from search
        // check if friend request is already done or not.
        if(iArrayList[iPosition].getFriendFromSearch()) {
            val auth = FirebaseAuth.getInstance()
            val database = FirebaseFirestore.getInstance()
            database.collection("users")
                .document(auth.currentUser!!.uid)
                .collection("friends")
                .whereEqualTo("userId", iArrayList[iPosition].getFriendId())
                .get().addOnSuccessListener { documents ->
                    if(0 == documents.size()) {
                        initFromNotFriendquest(view)
                    }
                    else {
                        for(document in documents) {
                            user.setRequest(document["request"] as Boolean)
                            if(2L == document["status"]) {
                                user.setShouldBeValid(true)
                            }
                            if(0L != document["status"] || true == document["request"])
                                user.setAssociatedToFriendRequest(true)
                        }
                        initFromFriendRequest(view, user, iPosition)
                    }
            }
        }
        else {
            initFromFriendRequest(view, user, iPosition)
        }
        return view
    }

    /**
     * User is not linked to a friend request
     */
    private fun initFromNotFriendquest(iView: View) {
        iView.findViewById<ImageView>(R.id.delete).visibility = View.GONE
    }

    /**
     * The user is already linked to a friend request, we need to display the well
     * panel with the right icon. Click is disabled on it
     */
    private fun initFromFriendRequest(iView: View, iUser: HomepageFriend, iPosition: Int) {

        val bRequest = iUser.getRequest()
        val bShouldBeValid = iUser.getShouldBeValid()

        /**
         * When user click on more menu, create popup window
         */
        iView.findViewById<ImageView>(R.id.delete).setOnClickListener {

            val builder: AlertDialog.Builder = AlertDialog.Builder(iView.context)

            builder.setTitle("Supprimer")
            builder.setMessage(context.getString(R.string.are_you_sure))

            builder.setPositiveButton(
                context.getString(R.string.yes)
            ) { dialog, _ -> // Do nothing but close the dialog
                dialog.dismiss()
                deleteFriend(iView, iPosition)
            }

            builder.create().show()
        }

        if (bRequest && !bShouldBeValid) {
            /**
             * Current user cannot valid the friend request
             */
            iView.findViewById<ImageView>(R.id.request).visibility = View.VISIBLE
        } else if (bRequest && bShouldBeValid) {
            /**
             * Current user can valid the friend request
             */
            iView.findViewById<ImageView>(R.id.valid).visibility = View.VISIBLE
            iView.findViewById<ImageView>(R.id.cancel).visibility = View.VISIBLE
            iView.findViewById<ImageView>(R.id.delete).visibility = View.GONE

            iView.findViewById<ImageView>(R.id.valid).setOnClickListener {
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
                                    iView.findViewById<ImageView>(R.id.valid).visibility =
                                        View.GONE
                                    iView.findViewById<ImageView>(R.id.cancel).visibility =
                                        View.GONE
                                    iView.findViewById<ImageView>(R.id.delete).visibility =
                                        View.VISIBLE
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
                                    iView.findViewById<ImageView>(R.id.valid).visibility =
                                        View.GONE
                                    iView.findViewById<ImageView>(R.id.cancel).visibility =
                                        View.GONE
                                    iView.findViewById<ImageView>(R.id.delete).visibility =
                                        View.VISIBLE
                                }
                        }
                    }
            }
            iView.findViewById<ImageView>(R.id.cancel).setOnClickListener {
                deleteFriend(iView, iPosition)
            }
        }
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