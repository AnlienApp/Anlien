package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import com.zitrouille.anlien.MainActivity.Companion.userCacheInformation
import android.widget.ArrayAdapter
import android.widget.TextView
import kotlin.collections.ArrayList
import android.view.*
import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide

class HomepageFriendListAdapter(private val iContext : Activity, private val iArrayList: ArrayList<HomepageFriend>):
    ArrayAdapter<HomepageFriend>(iContext, R.layout.item_homepage_friend, iArrayList) {

    @SuppressLint("ViewHolder", "InflateParams")
    override fun getView(iPosition: Int, iConvertView: View?, iParent: ViewGroup): View {
        val inflater: LayoutInflater = LayoutInflater.from(iContext)
        val view: View = inflater.inflate(R.layout.item_homepage_friend, null)
        view.findViewById<TextView>(R.id.name).text = iArrayList[iPosition].getName()
        view.findViewById<TextView>(R.id.identifiant).text = iArrayList[iPosition].getIdentifiant()

        /**
         * Display profile picture if it exists
         */
        if(userCacheInformation.containsKey(iArrayList[iPosition].getFriendId())) {
            Glide.with(iContext).load(userCacheInformation[iArrayList[iPosition].getFriendId()]!!.uri).into(view.findViewById(R.id.profile_picture))
            if("none" != userCacheInformation[iArrayList[iPosition].getFriendId()]!!.displayedBadge)
                Glide.with(iContext).load(MainActivity.retrieveBadge(userCacheInformation[iArrayList[iPosition].getFriendId()]!!.displayedBadge)).into(view.findViewById(R.id.badge))
        }
        else {
            MainActivity.retrieveUserInformation(iArrayList[iPosition].getFriendId(),
                null,
                null,
                view.findViewById(R.id.profile_picture),
                view.findViewById(R.id.badge),
            )
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
                    Log.i("Database request", "Filtered friend list retrieved in HomepageFriendListAdapter::getView")
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
            deleteFriend(iView, iPosition)
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

                // Set the request to false for the current user
                database.collection("users")
                    .document(auth.currentUser!!.uid)
                    .collection("friends")
                    .whereEqualTo("userId", iArrayList[iPosition].getFriendId())
                    .get().addOnSuccessListener { documents ->
                        Log.i("Database request", "Filtered friend list retrieved in HomepageFriendListAdapter::initFromFriendRequest")
                        for(doc in documents) {
                            if(!doc.exists()) continue
                            val information = hashMapOf(
                                "request" to false,
                            )
                            doc.reference.update(information as Map<String, Any>).addOnSuccessListener {
                                    iView.findViewById<ImageView>(R.id.valid).visibility =
                                        View.GONE
                                    iView.findViewById<ImageView>(R.id.cancel).visibility =
                                        View.GONE
                                    iView.findViewById<ImageView>(R.id.delete).visibility =
                                        View.VISIBLE
                                }
                        }
                    }

                // Set the request to false for the friend user
                database.collection("users")
                    .document(iArrayList[iPosition].getFriendId())
                    .collection("friends")
                    .whereEqualTo("userId", auth.currentUser!!.uid)
                    .get().addOnSuccessListener { documents ->
                        Log.i("Database request", "Filtered friend list retrieved in HomepageFriendListAdapter::initFromFriendRequest")
                        for(doc in documents){
                            if(!doc.exists()) continue
                            val information = hashMapOf(
                                "request" to false,
                            )
                            doc.reference.update(information as Map<String, Any>).addOnSuccessListener {
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
                Log.i("Database request", "Filtered friend list retrieved in HomepageFriendListAdapter::initFromFriendRequest")
                for(doc in documents) {
                    if(!doc.exists()) continue
                    doc.reference.delete().addOnSuccessListener {
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
                Log.i("Database request", "Filtered friend list retrieved in HomepageFriendListAdapter::initFromFriendRequest")
                for(doc in documents) {
                    if(!doc.exists()) continue
                    doc.reference.delete().addOnSuccessListener {
                            iView.visibility = View.GONE
                            (iContext as HomepageActivity).retrieveFriendList()
                        }
                }
            }
    }

}