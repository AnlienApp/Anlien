package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.zxing.WriterException
import com.zitrouille.anlien.databinding.ActivityHomepageBinding
import java.io.ByteArrayOutputStream
import android.provider.MediaStore
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import com.bumptech.glide.Glide
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import com.baoyz.widget.PullRefreshLayout
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.zitrouille.anlien.MainActivity.Companion.userCacheInformation
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener


class HomepageActivity : AppCompatActivity() {

    private var mActivityMainBinding: ActivityHomepageBinding? = null
    private var mEventArrayList: ArrayList<HomepageEvent>? = null
    private var mFriendArrayList: ArrayList<HomepageFriend>? = null

    private var mQRCodeResultLauncher: ActivityResultLauncher<Intent>? = null
    private var mQRCodeResultJoinEventLauncher: ActivityResultLauncher<Intent>? = null
    private var mCreateEventCallback: ActivityResultLauncher<Intent>? = null

    private var mAddFriendDialog: Dialog? = null

    private var mCurrentUserId: String = ""
    private var mDatabase: FirebaseFirestore? = null
    private var mStorage: FirebaseStorage? = null

    private var mFriendListReceptionRunning = false
    private var mEventListReceptionRunning = false

    private var mDialog: Dialog? = null

    private var mDisplayEventHistory: Boolean = false
    private var mDisplayCalendar: Boolean = false

    private var mFriendNotificationView: View? = null

    private var mCalendars: MutableList<CalendarDay> = ArrayList()
    private var bInitCalendar: Boolean = true
    private var mCurrentEventIndex: Int = 0

    /**
     * Init different behaviors when user click on the bottom navigation menu
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mActivityMainBinding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(mActivityMainBinding!!.root)

        initMemberData()

        initializeBottomNavigation()
        initializeSwipeRefresh() // For Event list and Friend list

        initializeQrCodeCallback() // Callback manager
        initializeCreateEventCallback() // Callback manager
        initializeProfilePicture() // Personal profile picture
        initializeBadge() // On badge click
        initializeQrCodeJoinEventCallback()
    }

    /**
     * At the beginning of the activity, check if user is well connected.
     */
    override fun onStart() {
        super.onStart()
        checkCurrentUser()
    }

    /**
     * When user reopen the application, connexion is tested in the checkCurrentUser().
     */
    override fun onResume() {
        super.onResume()
        checkCurrentUser()
        retrieveEventList()
        initFriendNotification()
    }

    override fun onPause() {
        super.onPause()
        hideFriendSearchBar(false)
    }

    private fun initMemberData() {
        mCurrentUserId = FirebaseAuth.getInstance().currentUser!!.uid
        mDatabase = FirebaseFirestore.getInstance()
        mStorage = FirebaseStorage.getInstance()
    }

    /**
     * Used to initialized swipe refresh callback for friend list and event list.
     */
    private fun initializeSwipeRefresh() {
        val eventListSwipe: PullRefreshLayout = findViewById(R.id.swipeRefreshListEvent)
        eventListSwipe.setOnRefreshListener {
            bInitCalendar = true
            val calendar = findViewById<com.applandeo.materialcalendarview.CalendarView>(R.id.calendarView)
            calendar.selectedDates = listOf(Calendar.getInstance())
            retrieveEventList()
            eventListSwipe.setRefreshing(false)
        }

        val friendListSwipe: PullRefreshLayout = findViewById(R.id.swipeRefreshListFriend)
        friendListSwipe.setOnRefreshListener {
            retrieveFriendList()
            friendListSwipe.setRefreshing(false)
        }
    }

    private fun initFriendNotification() {
        if(null == mFriendNotificationView) {
            val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            mDatabase!!.collection("users").document(mCurrentUserId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists() && doc.contains("friendNotification") && doc["friendNotification"] == true) {
                        val v: View = bottomMenu.findViewById(R.id.nav_friend)
                        val itemView = v as BottomNavigationItemView
                        mFriendNotificationView = LayoutInflater.from(this)
                            .inflate(R.layout.notification_badge, itemView, true)
                    }
                }
        }
    }

    /**
     * For now they are 3 buttons available in the bottom navigation bar.
     * Should be called only one time in the onCreate method
     * By default, the selected menu is the event list (middle one)
     */
    private fun initializeBottomNavigation() {
        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomMenu.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    displayEventListPage()
                }
                R.id.nav_friend -> {
                    displayFriendPage()
                }
                R.id.nav_option -> {
                    displayPersonalProfilePage()
                }
            }
            true
        }
        val eventListView: View = bottomMenu.findViewById(R.id.nav_home)
        eventListView.performClick()
    }

    /**
     * Retrieve existing profile picture and set it to the profile
     */
    private fun initializeProfilePicture() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if(null != currentUser!!.photoUrl) {
            Glide.with(applicationContext).load(currentUser.photoUrl)
                .into(findViewById(R.id.profile_picture))
        }

        findViewById<ConstraintLayout>(R.id.profile_picture_layout).setOnClickListener {

            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setCancelable(false)
            dialog.setContentView(R.layout.dialog_homepage_edit_profile_picture)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)

            dialog.findViewById<ImageView>(R.id.import_picture).setOnClickListener {
                dialog.dismiss()
                // start picker to get image for cropping and then use the image in cropping activity
                 CropImage.activity()
                     .setGuidelines(CropImageView.Guidelines.ON)
                     .setCropShape(CropImageView.CropShape.OVAL)
                     .setFixAspectRatio(true)
                     .start(this)
            }
            dialog.show()
        }
    }

    private fun initializeBadge() {
        val badge = findViewById<ImageView>(R.id.main_user_badge)
        badge.setOnClickListener {

            // Retrieve list of available badges for the current user
            mDatabase!!
                .collection("users")
                .document(mCurrentUserId)
                .collection("badges")
                .get().addOnSuccessListener { badges ->

                    val badgeArrayList = ArrayList<HomepageBadge>()

                    for(badgeDoc in badges) {
                        if(!badgeDoc.exists()) continue
                        badgeArrayList.add(HomepageBadge(badgeDoc["name"].toString()))
                    }

                    val dialog = Dialog(this)
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    dialog.setCancelable(false)
                    dialog.setContentView(R.layout.dialog_homepage_edit_badge)
                    dialog.setCanceledOnTouchOutside(true)
                    dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)

                    val recyclerView: RecyclerView = dialog.findViewById(R.id.participant_list)
                    val linearLayoutManager = LinearLayoutManager(applicationContext)
                    linearLayoutManager.orientation = RecyclerView.HORIZONTAL
                    recyclerView.layoutManager = linearLayoutManager
                    recyclerView.setHasFixedSize(true)
                    recyclerView.adapter = HomepageBadgeListAdapter(badgeArrayList)

                    recyclerView.addOnItemTouchListener(RecyclerItemClickListener(this, recyclerView, object : RecyclerItemClickListener.OnItemClickListener {

                        override fun onItemClick(view: View, position: Int) {
                            mDatabase!!
                                .collection("users")
                                .document(mCurrentUserId)
                                .update("displayedBadge", badgeArrayList[position].getName()).addOnSuccessListener {
                                    Glide.with(applicationContext).load(MainActivity.retrieveBadgeLarge(badgeArrayList[position].getName()))
                                        .into(findViewById(R.id.main_user_badge))
                                    userCacheInformation[mCurrentUserId]!!.displayedBadge = badgeArrayList[position].getName()
                                    dialog.dismiss()
                                    Toast.makeText(applicationContext, getString(R.string.badge_update_success), Toast.LENGTH_SHORT).show()
                                }
                        }
                        override fun onItemLongClick(view: View?, position: Int) {

                        }
                    }))
                    dialog.show()
                }
        }
    }

    private fun initializeCreateEventCallback() {
        mCreateEventCallback =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                retrieveEventList()
            }
    }

    private fun initializeQrCodeJoinEventCallback() {
        mQRCodeResultJoinEventLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if(Activity.RESULT_OK == result.resultCode) {
                val eventId = result.data!!.extras?.get("qrCode").toString()
                displayJoinEventDialog(eventId)
            }
        }

    }

    @SuppressLint("SetTextI18n", "SimpleDateFormat")
    private fun displayJoinEventDialog(iEventId: String) {

        val database = FirebaseFirestore.getInstance()

        database.collection("events").document(iEventId).get().addOnSuccessListener { doc ->
            if(doc.exists()) {
                val dialog = Dialog(this)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCancelable(false)
                dialog.setContentView(R.layout.dialog_homepage_join_event)
                dialog.setCanceledOnTouchOutside(true)
                dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)

                dialog.findViewById<TextView>(R.id.event_name).text =
                    doc.data!!["title"].toString()

                // Retrieve date from milliseconds
                val newDate: Date = Calendar.getInstance().time
                val formatter = SimpleDateFormat("EEEE d MMMM yyyy")
                newDate.time = doc.getLong("date")!!
                dialog.findViewById<TextView>(R.id.event_date).text =
                    formatter.format(newDate).toString().substring(0, 1)
                        .uppercase(Locale.getDefault()) + formatter.format(newDate).toString()
                        .substring(1)
                        .lowercase(Locale.getDefault())

                dialog.findViewById<TextView>(R.id.event_hour).text =
                    doc.data!!["hour"].toString()

                val organizerId = doc.data!!["organizerId"].toString()

                val eventOrganizerName = dialog.findViewById<TextView>(R.id.event_organizer_name)

                // User is present in the cache
                if (userCacheInformation.containsKey(organizerId)) {
                    eventOrganizerName.text = userCacheInformation[organizerId]!!.displayName
                        Glide.with(applicationContext)
                            .load(userCacheInformation[organizerId]!!.uri)
                            .into(dialog.findViewById(R.id.event_organizer_profile_picture))
                }
                else {
                    MainActivity.retrieveUserInformation(organizerId,
                        eventOrganizerName,
                        null,
                        dialog.findViewById(R.id.event_organizer_profile_picture),
                        null
                    )
                }
                database.collection("users")
                    .document(mCurrentUserId)
                    .collection("events")
                    .whereEqualTo("eventId", iEventId)
                    .get().addOnSuccessListener { docs ->
                        if (docs.size() != 0) {
                            dialog.findViewById<ImageView>(R.id.join_event)
                                .setOnClickListener {
                                    dialog.dismiss()
                                    val intent =
                                        Intent(applicationContext, EventActivity::class.java)
                                    intent.putExtra("eventId", iEventId)
                                    intent.putExtra("organizerId", doc["organizerId"].toString())
                                    intent.putExtra("role", 0L)
                                    startActivity(intent)
                                }
                        } else {
                            dialog.findViewById<ImageView>(R.id.join_event)
                                .setOnClickListener {
                                    val participantData = hashMapOf(
                                        "userId" to mCurrentUserId,
                                        "status" to 1, // 0 cancel, 1 pending, 2 accept
                                    )
                                    database.collection("events")
                                        .document(iEventId)
                                        .collection("participants")
                                        .add(participantData).addOnSuccessListener {
                                            val eventData = hashMapOf(
                                                "eventId" to iEventId,
                                            )
                                            database.collection("users")
                                                .document(mCurrentUserId)
                                                .collection("events")
                                                .add(eventData).addOnSuccessListener {
                                                    dialog.dismiss()
                                                    val intent =
                                                        Intent(
                                                            applicationContext,
                                                            EventActivity::class.java
                                                        )
                                                    intent.putExtra("eventId", iEventId)
                                                    intent.putExtra(
                                                        "organizerId",
                                                        doc["organizerId"].toString(),
                                                    )
                                                    intent.putExtra(
                                                        "role",
                                                        0L,
                                                    )
                                                    startActivity(intent)
                                                }
                                        }
                                }
                        }
                    }
                dialog.show()
            }
            else {
                Toast.makeText(applicationContext, getString(R.string.not_event_qr_code), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Used to manage qr code scan.
     * Retrieve associated user if it exists and display popup to the current user.
     * He can choose if he wants to add this user to his friend list.
     */
    private fun initializeQrCodeCallback() {
        mQRCodeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if(Activity.RESULT_OK == result.resultCode) {
                val otherUserId = result.data!!.extras?.get("qrCode").toString()

                /**
                 * User scan his own QR code
                 */
                if(otherUserId == mCurrentUserId) {
                    mDatabase!!.collection("users").document(otherUserId).get().addOnSuccessListener { document ->
                        if(document.exists()) {
                            Log.i("Database request", "User retrieved in HomepageActivity::initializeQrCodeCallback - "+document.id)
                            displayAddFriendDialog(
                                document,
                                ibCanBeAdded = false,
                                ibPending = false,
                                ibYourSelf = true
                            )
                        }
                        else {
                            Toast.makeText(applicationContext, getString(R.string.not_valid_qr_code), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                /**
                 * User scan QR code from another user
                 */
                if(otherUserId != mCurrentUserId) {
                    mDatabase!!.collection("users").document(otherUserId).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                Log.i("Database request", "User retrieved in HomepageActivity::initializeQrCodeCallback - "+document.id)
                                mDatabase!!.collection("users").document(otherUserId)
                                    .collection("friends")
                                    .whereEqualTo("userId", mCurrentUserId)
                                    .get().addOnSuccessListener { documents ->
                                        /**
                                         * User already add this other user
                                         */
                                        if (documents.size() != 0) {
                                            Log.i("Database request", "Friend retrieved in HomepageActivity::initializeQrCodeCallback - "+documents.documents[0].id)
                                            /**
                                             * The request has not yet being validated by the other
                                             */
                                            if (true == documents.documents[0].data!!["request"]) {
                                                displayAddFriendDialog(
                                                    document,
                                                    ibCanBeAdded = false,
                                                    ibPending = true,
                                                    ibYourSelf = false
                                                )
                                            }
                                            /**
                                             * The request has been validated by the other user
                                             */
                                            if (false == documents.documents[0].data!!["request"]) {
                                                displayAddFriendDialog(
                                                    document,
                                                    ibCanBeAdded = false,
                                                    ibPending = false,
                                                    ibYourSelf = false
                                                )
                                            }
                                        }
                                        /**
                                         * The other user is not linked to a friend request
                                         */
                                        if (documents.size() == 0) {
                                            displayAddFriendDialog(
                                                document,
                                                ibCanBeAdded = true,
                                                ibPending = false,
                                                ibYourSelf = false
                                            )
                                        }
                                    }
                            }
                            else {
                                Toast.makeText(applicationContext, getString(R.string.not_valid_qr_code), Toast.LENGTH_SHORT).show()
                            }
                        }.addOnFailureListener { exception ->
                        Log.d(TAG, "get failed with ", exception)
                    }
                }
            }
        }
    }

    private fun displayAddFriendDialog(iDoc: DocumentSnapshot, ibCanBeAdded: Boolean, ibPending: Boolean, ibYourSelf: Boolean) {
        mAddFriendDialog = Dialog(this)
        mAddFriendDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        mAddFriendDialog!!.setCancelable(false)
        mAddFriendDialog!!.setContentView(R.layout.dialog_homepage_add_friend)
        mAddFriendDialog!!.setCanceledOnTouchOutside(true)
        mAddFriendDialog!!.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        val userNameTextView = mAddFriendDialog!!.findViewById(R.id.user_name) as TextView
        userNameTextView.text = iDoc.data!!["displayName"].toString()

        /**
         * Display profile picture if it exists
         */
        val profilePictureRef = FirebaseStorage.getInstance().reference.child("profileImages/"+iDoc.id+".jpeg")
        profilePictureRef.downloadUrl.addOnSuccessListener {
            Glide.with(applicationContext).load(it).into(mAddFriendDialog!!.findViewById(R.id.profile_picture))
        }

        if(userCacheInformation.containsKey(iDoc.id)) {
            if("none" != userCacheInformation[iDoc.id]!!.displayedBadge)
                Glide.with(applicationContext).load(MainActivity.retrieveBadge(userCacheInformation[iDoc.id]!!.displayedBadge)).into(mAddFriendDialog!!.findViewById(R.id.badge))
        }

        val addFriendImageView = mAddFriendDialog!!.findViewById(R.id.add_friend) as ImageView
        val yourselfImageView = mAddFriendDialog!!.findViewById(R.id.yourself) as ImageView
        if(ibYourSelf) {
            addFriendImageView.visibility = View.GONE
            yourselfImageView.visibility = View.VISIBLE
            yourselfImageView.setOnClickListener { currentView ->
                Snackbar.make(currentView, getString(R.string.you), Snackbar.LENGTH_LONG)
                    .show()
            }
        }
        else {
            if (ibCanBeAdded) {
                val mainLayout =
                    mAddFriendDialog!!.findViewById(R.id.main_layout) as ConstraintLayout
                addFriendImageView.setOnClickListener {
                    addFriendImageView.animate().rotation(180F).withEndAction {
                        mainLayout.animate().alpha(0F).withEndAction {
                            sendFriendRequest(iDoc.id)
                        }
                    }
                }
            } else {
                if (ibPending) {
                    addFriendImageView.visibility = View.GONE
                    val friendPendingImageView =
                        mAddFriendDialog!!.findViewById(R.id.friend_pending) as ImageView
                    friendPendingImageView.visibility = View.VISIBLE
                    friendPendingImageView.setOnClickListener { currentView ->
                        Snackbar.make(currentView, getString(R.string.friend_request_already_done), Snackbar.LENGTH_LONG)
                            .show()
                    }
                } else {
                    addFriendImageView.visibility = View.GONE
                    val friendAddedImageView =
                        mAddFriendDialog!!.findViewById(R.id.friend_added) as ImageView
                    friendAddedImageView.visibility = View.VISIBLE
                    friendAddedImageView.setOnClickListener { currentView ->
                        Snackbar.make(currentView, getString(R.string.already_friends), Snackbar.LENGTH_LONG)
                            .show()
                    }
                }

            }
        }
        mAddFriendDialog!!.show()
    }

    private fun sendFriendRequest(iUserId: String) {

        val currentUserData = hashMapOf(
            "userId" to iUserId,
            "request" to true,
            "status" to 1
        )
        mDatabase!!.collection("users").document(mCurrentUserId).collection("friends")
            .add(currentUserData)
            .addOnSuccessListener {
                mAddFriendDialog!!.dismiss()
                mAddFriendDialog = null
                retrieveFriendList()
            }
            .addOnFailureListener {
                mAddFriendDialog!!.dismiss()
                mAddFriendDialog = null
            }

        val otherUserData = hashMapOf(
            "userId" to mCurrentUserId,
            "request" to true,
            "status" to 2
        )
        mDatabase!!.collection("users").document(iUserId).collection("friends")
            .add(otherUserData)
            .addOnSuccessListener {

                if(userCacheInformation.containsKey(iUserId)) {
                    val notification =
                        FirebaseNotificationSender(
                            userCacheInformation[iUserId]!!.notificationToken,
                            getString(R.string.ask),
                            userCacheInformation[mCurrentUserId]!!.displayName + getString(R.string.want_to_be_your_friend),
                            this
                        )
                    notification.sendNotification()
                }

                // Update the dest user to display notification on his app
                mDatabase!!.collection("users").document(iUserId).update("friendNotification", true)

                hideFriendSearchBar(false)
            }
            .addOnFailureListener {
                hideFriendSearchBar(false)
            }
    }

    private fun homepageVisibility(ibValue : Boolean) {
        if(ibValue) {
            findViewById<RelativeLayout>(R.id.eventPage).animate().alpha(1F).withEndAction {
                findViewById<RelativeLayout>(R.id.eventPage).visibility = View.VISIBLE
            }
            findViewById<ImageView>(R.id.create_event).animate().alpha(1F).withEndAction {
                findViewById<ImageView>(R.id.create_event).visibility = View.VISIBLE
            }
            findViewById<ImageView>(R.id.display_history).animate().alpha(1F).withEndAction {
                findViewById<ImageView>(R.id.display_history).visibility = View.VISIBLE
            }
            findViewById<ImageView>(R.id.display_calendar).animate().alpha(1F).withEndAction {
                findViewById<ImageView>(R.id.display_calendar).visibility = View.VISIBLE
            }
            findViewById<ImageView>(R.id.join_event).animate().alpha(1F).withEndAction {
                findViewById<ImageView>(R.id.join_event).visibility = View.VISIBLE
            }
        }
        else {
            findViewById<RelativeLayout>(R.id.eventPage).animate().alpha(0F).withEndAction {
                findViewById<RelativeLayout>(R.id.eventPage).visibility = View.GONE
            }
            findViewById<ImageView>(R.id.create_event).animate().alpha(0F).withEndAction {
                findViewById<ImageView>(R.id.create_event).visibility = View.GONE
            }
            findViewById<ImageView>(R.id.display_history).animate().alpha(0F).withEndAction {
                findViewById<ImageView>(R.id.display_history).visibility = View.GONE
            }
            findViewById<ImageView>(R.id.display_calendar).animate().alpha(0F).withEndAction {
                findViewById<ImageView>(R.id.display_calendar).visibility = View.GONE
            }
            findViewById<ImageView>(R.id.join_event).animate().alpha(1F).withEndAction {
                findViewById<ImageView>(R.id.join_event).visibility = View.GONE
            }
        }
    }

    private fun friendPageVisibility(ibValue : Boolean) {
        if(ibValue) {
            findViewById<RelativeLayout>(R.id.friendPage).animate().alpha(1F).withEndAction {
                findViewById<RelativeLayout>(R.id.friendPage).visibility = View.VISIBLE
            }
            findViewById<ImageView>(R.id.add_friend).animate().alpha(1F).withEndAction {
                findViewById<ImageView>(R.id.add_friend).visibility = View.VISIBLE
            }
            findViewById<ConstraintLayout>(R.id.search_friend_layout).animate().alpha(1F).withEndAction {
                findViewById<ConstraintLayout>(R.id.search_friend_layout).visibility = View.VISIBLE
            }

        }
        else {
            findViewById<RelativeLayout>(R.id.friendPage).animate().alpha(0F).withEndAction {
                findViewById<RelativeLayout>(R.id.friendPage).visibility = View.GONE
            }
            findViewById<ImageView>(R.id.add_friend).animate().alpha(0F).withEndAction {
                findViewById<ImageView>(R.id.add_friend).visibility = View.GONE
            }
            findViewById<ConstraintLayout>(R.id.search_friend_layout).animate().alpha(0F).withEndAction {
                findViewById<ConstraintLayout>(R.id.search_friend_layout).visibility = View.GONE

            }

        }
        hideFriendSearchBar(false)
    }

    private fun userPageVisibility(ibValue : Boolean) {
        if(ibValue) {
            findViewById<RelativeLayout>(R.id.mainUserPage).animate().alpha(1F).withEndAction {
                findViewById<RelativeLayout>(R.id.mainUserPage).visibility = View.VISIBLE
            }
            findViewById<ImageView>(R.id.logout).animate().alpha(1F).withEndAction {
                findViewById<ImageView>(R.id.logout).visibility = View.VISIBLE
            }
        }
        else {
            findViewById<RelativeLayout>(R.id.mainUserPage).animate().alpha(0F).withEndAction {
                findViewById<RelativeLayout>(R.id.mainUserPage).visibility = View.GONE
            }
            findViewById<ImageView>(R.id.logout).animate().alpha(0F).withEndAction {
                findViewById<ImageView>(R.id.logout).visibility = View.GONE
            }
        }
    }

    private fun displayEventListPage() {
        findViewById<TextView>(R.id.my_activity).text = getString(R.string.my_activity)
        homepageVisibility(true)
        friendPageVisibility(false)
        userPageVisibility(false)
        findViewById<RelativeLayout>(R.id.mainUserPage).animate().alpha(0F).withEndAction {
            findViewById<RelativeLayout>(R.id.mainUserPage).visibility = View.GONE
        }

        initFriendNotification()

        if(null == mEventArrayList) {

            findViewById<ImageView>(R.id.join_event).setOnClickListener {
                mQRCodeResultJoinEventLauncher!!.launch(Intent(this, ScannerActivity::class.java))
            }

            findViewById<ConstraintLayout>(R.id.eventListEmpty).setOnClickListener {
                mCreateEventCallback!!.launch(Intent(this, CreateEventActivity::class.java))
            }
            findViewById<ImageView>(R.id.create_event).setOnClickListener {
                mCreateEventCallback!!.launch(Intent(this, CreateEventActivity::class.java))
            }
            findViewById<ImageView>(R.id.display_history).setOnClickListener {
                mDisplayEventHistory = !mDisplayEventHistory
                if(mDisplayEventHistory) {
                    findViewById<ImageView>(R.id.display_history).clearColorFilter()
                    findViewById<ImageView>(R.id.display_history).setColorFilter(R.attr.colorSecondary)
                }
                else {
                    findViewById<ImageView>(R.id.display_history).clearColorFilter()
                }
                retrieveEventList()
            }
            findViewById<ImageView>(R.id.display_calendar).setOnClickListener {
                mDisplayCalendar = !mDisplayCalendar
                if(mDisplayCalendar) {
                    findViewById<ImageView>(R.id.display_history).visibility = View.INVISIBLE
                    findViewById<ImageView>(R.id.display_calendar).clearColorFilter()
                    findViewById<ImageView>(R.id.display_calendar).setColorFilter(R.attr.colorSecondary)
                    val calendar = findViewById<com.applandeo.materialcalendarview.CalendarView>(R.id.calendarView)
                    val calendars: MutableList<Calendar> = ArrayList()
                    val appCalendar = Calendar.getInstance()
                    calendars.add(appCalendar)
                    calendar.selectedDates = calendars
                    calendar.visibility = View.VISIBLE
                    findViewById<ConstraintLayout>(R.id.eventListEmpty).visibility = View.GONE
                    calendar.animate().alpha(1.0f).start()

                    calendar.setOnDayClickListener(object : OnDayClickListener {
                        override fun onDayClick(eventDay: EventDay) {
                            for(calendarDay in mCalendars) {
                                if(calendarDay.calendar.timeInMillis == eventDay.calendar.timeInMillis) {
                                    calendarDay.backgroundResource = R.drawable.circular_image_background_red
                                }
                                else {
                                    calendarDay.backgroundResource = R.drawable.round_corner_blue
                                }
                            }
                            calendar.setCalendarDays(mCalendars)
                            retrieveEventList()
                        }
                    })
                }
                else {
                    findViewById<ImageView>(R.id.display_history).visibility = View.VISIBLE
                    findViewById<ImageView>(R.id.display_calendar).clearColorFilter()
                    findViewById<com.applandeo.materialcalendarview.CalendarView>(R.id.calendarView).animate().alpha(0.0f).withEndAction {
                        findViewById<com.applandeo.materialcalendarview.CalendarView>(R.id.calendarView).visibility = View.GONE
                        if(mEventArrayList!!.size == 0)
                            findViewById<ConstraintLayout>(R.id.eventListEmpty).visibility = View.VISIBLE
                    }
                }
                retrieveEventList()
            }
            retrieveEventList()
        }
    }

    private fun displayFriendPage() {
        findViewById<TextView>(R.id.my_activity).text = getString(R.string.my_friends)
        homepageVisibility(false)
        friendPageVisibility(true)
        userPageVisibility(false)

        if(null != mFriendNotificationView) {
            val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            val view = bottomMenu.findViewById<BottomNavigationItemView>(R.id.nav_friend)
            view.removeView(view.findViewById<TextView>(R.id.notifications_badge))
            mFriendNotificationView = null
            mDatabase!!.collection("users").document(mCurrentUserId).update("friendNotification", false)
        }

        if(null == mFriendArrayList) {
            retrieveFriendList()
            findViewById<ImageView>(R.id.add_friend).setOnClickListener {
                mQRCodeResultLauncher!!.launch(Intent(this, ScannerActivity::class.java))
            }
            findViewById<ConstraintLayout>(R.id.friendListEmpty).setOnClickListener {
                mQRCodeResultLauncher!!.launch(Intent(this, ScannerActivity::class.java))
            }
        }

        val searchEditText =  findViewById<EditText>(R.id.search_bar)

        findViewById<ImageView>(R.id.search_friend).setOnClickListener {
            if(View.VISIBLE == findViewById<EditText>(R.id.search_bar).visibility) {
                hideFriendSearchBar(true)
            }
            else {
                displayFriendSearchBar()
            }
        }

        searchEditText.addTextChangedListener {
            var timer = Timer()
            val delay: Long = 500 // Milliseconds
            searchEditText.doAfterTextChanged {
                timer.cancel()
                timer = Timer()
                timer.schedule(
                    object : TimerTask() {
                        override fun run() {
                            val currentSearch = it.toString().uppercase(Locale.getDefault())
                            mDatabase!!.collection("users").whereGreaterThanOrEqualTo("identifiant",  currentSearch).whereLessThanOrEqualTo("identifiant", currentSearch+"\uf8ff")
                                .get().addOnSuccessListener { documents ->

                                    // Clean operation
                                    if(null != mFriendArrayList) {
                                        mFriendArrayList!!.clear()
                                        mActivityMainBinding!!.friendList.adapter = null
                                        mFriendArrayList = ArrayList()
                                    }

                                    if(currentSearch.length >= 3) {
                                        for (document in documents) {
                                            if (null == document) continue
                                            mFriendArrayList!!.add(
                                                HomepageFriend(
                                                    document["displayName"].toString(),
                                                    document["identifiant"].toString(),
                                                    iPendingRequest = false,
                                                    iShouldBeValid = false,
                                                    iFriendId = document.id,
                                                    ibFindFromSearch = true
                                                )
                                            )
                                            mActivityMainBinding!!.friendList.adapter =
                                                HomepageFriendListAdapter(
                                                    this@HomepageActivity,
                                                    mFriendArrayList!!
                                                )
                                            mActivityMainBinding!!.friendList.setOnItemClickListener { _, _, position, _ ->
                                                mDatabase!!.collection("users").document(mFriendArrayList!![position].getFriendId()).get().addOnSuccessListener { doc ->
                                                    Log.i("Database request", "User retrieved in HomepageActivity::displayFriendPage - "+doc.id)
                                                    var bIsYourSelf = false
                                                    if(mCurrentUserId == mFriendArrayList!![position].getFriendId())
                                                        bIsYourSelf = true
                                                    var bCanBeAdded = true
                                                    var bPending = true
                                                    if(mFriendArrayList!![position].getAssociatedToFriendRequest()) {
                                                        bCanBeAdded = false
                                                        bPending = false
                                                        if(mFriendArrayList!![position].getRequest()) {
                                                            bCanBeAdded = false
                                                            bPending = true
                                                        }
                                                    }

                                                    if(mFriendArrayList!![position].getRequest()) {
                                                        bCanBeAdded = false
                                                        bPending = true
                                                    }

                                                    displayAddFriendDialog(document,
                                                        ibCanBeAdded = bCanBeAdded,
                                                        ibPending = bPending,
                                                        ibYourSelf = bIsYourSelf
                                                    )
                                                }

                                            }
                                        }
                                    }
                                    if(documents.size() != 0) {
                                        findViewById<ListView>(R.id.friendList).visibility = View.VISIBLE
                                        findViewById<ConstraintLayout>(R.id.friendListEmpty).visibility = View.GONE
                                        findViewById<ConstraintLayout>(R.id.friendListSearchIssue).visibility = View.GONE
                                    }
                                    else {
                                        findViewById<ListView>(R.id.friendList).visibility = View.GONE
                                        findViewById<ConstraintLayout>(R.id.friendListEmpty).visibility = View.GONE
                                        findViewById<ConstraintLayout>(R.id.friendListSearchIssue).visibility = View.VISIBLE
                                    }
                                }
                        }
                    },
                    delay
                )
            }
        }
        if(null != mFriendArrayList && 0 == mFriendArrayList!!.size)
            retrieveFriendList()
    }

    private fun hideFriendSearchBar(ibUpdateFriendList: Boolean) {
        val searchEditText =  findViewById<EditText>(R.id.search_bar)
        searchEditText.animate().alpha(0.0f).withEndAction {
            searchEditText.visibility = View.GONE
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
            Glide.with(applicationContext).load(R.drawable.ic_search).into(findViewById(R.id.search_friend))
            if(ibUpdateFriendList)
                retrieveFriendList()
        }

        // Disable scroll update
        val friendListSwipe: PullRefreshLayout = findViewById(R.id.swipeRefreshListFriend)
        friendListSwipe.isEnabled = true

        if(null != mActivityMainBinding)
            mActivityMainBinding!!.friendList.onItemClickListener = null
    }

    private fun displayFriendSearchBar() {
        val searchEditText =  findViewById<EditText>(R.id.search_bar)
        searchEditText.visibility = View.VISIBLE
        searchEditText.animate().alpha(0.9f)
        searchEditText.requestFocus()
        val imm: InputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(
            searchEditText,
            InputMethodManager.SHOW_IMPLICIT
        )
        Glide.with(applicationContext).load(R.drawable.ic_close).into(findViewById(R.id.search_friend))
        searchEditText.setText("")

        // Disable scroll update
        val friendListSwipe: PullRefreshLayout = findViewById(R.id.swipeRefreshListFriend)
        friendListSwipe.isEnabled = false
    }

    @SuppressLint("CutPasteId")
    private fun displayPersonalProfilePage() {
        findViewById<TextView>(R.id.my_activity).text = getString(R.string.my_options)
        homepageVisibility(false)
        friendPageVisibility(false)
        userPageVisibility(true)

        initFriendNotification()

        if(userCacheInformation.containsKey(mCurrentUserId)) {
            findViewById<TextView>(R.id.userName).text = userCacheInformation[mCurrentUserId]!!.displayName
            findViewById<TextView>(R.id.pseudo).text = userCacheInformation[mCurrentUserId]!!.identifiant
            findViewById<ImageView>(R.id.qr_code).setImageBitmap(generateQRCode(mCurrentUserId))
            if("none" != userCacheInformation[mCurrentUserId]!!.displayedBadge)
                Glide.with(applicationContext).load(MainActivity.retrieveBadgeLarge(userCacheInformation[mCurrentUserId]!!.displayedBadge)).into(findViewById(R.id.main_user_badge))
        }
        else {
            MainActivity.retrieveUserInformation(mCurrentUserId,
                findViewById(R.id.userName),
                findViewById(R.id.pseudo),
                null,
                findViewById(R.id.badge))
        }

        findViewById<ImageView>(R.id.qr_code).setImageBitmap(
            generateQRCode(
                mCurrentUserId
            )
        )

        // Open help page
        findViewById<TextView>(R.id.help_project).setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fr.tipeee.com/anlien/"))
            startActivity(browserIntent)
        }

        findViewById<ImageView>(R.id.edit_displayed_name).setOnClickListener {
            mDialog = Dialog(this)
            mDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
            mDialog!!.setCancelable(false)
            mDialog!!.setCanceledOnTouchOutside(true)
            mDialog!!.setContentView(R.layout.dialog_main_edit_pseudo)
            mDialog!!.window!!.setBackgroundDrawableResource(android.R.color.transparent)

            val nameEditText = mDialog!!.findViewById<EditText>(R.id.pseudo)
            val currentName = findViewById<TextView>(R.id.userName).text
            nameEditText.setText(currentName)
            val validName = mDialog!!.findViewById<ImageView>(R.id.valid)
            validName.setOnClickListener {
                if(nameEditText.text.toString().isNotEmpty()) {
                    mDatabase!!.collection("users")
                        .document(mCurrentUserId)
                        .update("displayName", nameEditText.text.toString())
                        .addOnSuccessListener {
                            findViewById<TextView>(R.id.userName).text =
                                nameEditText.text.toString()
                            mDialog!!.dismiss()
                        }
                }
            }
            mDialog!!.show()
        }

        findViewById<ImageView>(R.id.logout).setOnClickListener {
            Firebase.auth.signOut()
            checkCurrentUser()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result = CropImage.getActivityResult(data)
            if (resultCode == RESULT_OK) {
                val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, result.uri)
                uploadImageToProfile(bitmap)
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Log.e("Tag", result.error.toString())
            }
        }
    }

    private fun uploadImageToProfile(iBitmap: Bitmap) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        iBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val uid: String = mCurrentUserId
        val storageRef: StorageReference = mStorage!!.reference
            .child("profileImages")
            .child("$uid.jpeg")
        storageRef.putBytes(byteArrayOutputStream.toByteArray()).addOnSuccessListener {
            getDownloadUrl(storageRef)
        }
    }

    private fun getDownloadUrl(iStorageRef: StorageReference) {
        iStorageRef.downloadUrl.addOnSuccessListener {
            setUserProfileImageUri(it)
        }
    }

    private fun setUserProfileImageUri(iUri : Uri) {
        val request: UserProfileChangeRequest = UserProfileChangeRequest.Builder().setPhotoUri(iUri).build()
        FirebaseAuth.getInstance().currentUser!!.updateProfile(request).addOnSuccessListener {
            Toast.makeText(this, "Photo mise ?? jour", Toast.LENGTH_LONG).show()
            Glide.with(applicationContext).load(iUri).into(findViewById(R.id.profile_picture))
        }.addOnFailureListener {
            Toast.makeText(this, getString(R.string.update_failure), Toast.LENGTH_LONG).show()
        }
        val uid: String = mCurrentUserId
        if(userCacheInformation.containsKey(uid)) {
            userCacheInformation[uid]!!.uri = iUri
        }
    }


    /**
     * Clean existing friend list displayed in the app and retrieve new one
     * from the firebase database. Be carefully and call it only only when necessary.
     */
    fun retrieveFriendList() {
        if(mFriendListReceptionRunning)
            return
        mFriendListReceptionRunning = true
        // At first clean the previous list if exists
        mActivityMainBinding!!.friendList.adapter = null
        if(null != mFriendArrayList)
            mFriendArrayList!!.clear()

        mActivityMainBinding!!.friendList.isClickable = false

        /**
         * Retrieve all friends from the current user
         * DATABASE CALL USERS/FRIENDS
         */
        mDatabase!!.collection("users")
            .document(mCurrentUserId)
            .collection("friends")
            .get().addOnSuccessListener { documents ->
                Log.i("Database request", "Friend list retrieved in HomepageActivity::retrieveFriendList")

                mFriendArrayList = ArrayList()

                if(0 == documents.size()) {
                    mFriendListReceptionRunning = false
                    findViewById<ListView>(R.id.friendList).visibility = View.GONE
                    findViewById<ConstraintLayout>(R.id.friendListEmpty).visibility = View.VISIBLE
                    findViewById<ConstraintLayout>(R.id.friendListSearchIssue).visibility = View.GONE
                }
                else {
                    findViewById<ListView>(R.id.friendList).visibility = View.VISIBLE
                    findViewById<ConstraintLayout>(R.id.friendListEmpty).visibility = View.GONE
                    findViewById<ConstraintLayout>(R.id.friendListSearchIssue).visibility = View.GONE
                }

                for(document in documents) {
                    /**
                     * The current friend information is not well defined.
                     * If this is the last document of the list, finish the creation.
                     * In other case, juste continue into the next friend document.
                     */
                    if(!document.exists()) {
                        // Current document is the last one of the list, fill list with retrieve users
                        if(mFriendArrayList!!.size == documents.size()) {
                            mFriendArrayList!!.sortBy { it.getIdentifiant() }
                            mActivityMainBinding!!.friendList.adapter = HomepageFriendListAdapter(this, mFriendArrayList!!)
                            mFriendListReceptionRunning = false
                        }
                        else {
                            continue
                        }
                    }
                    /**
                     * The friend document is well defined, we need to retrieve concrete information
                     * about the friend now. If the friend is not yet added (request only),
                     * display the right icon(s)
                     */
                    val userId = document["userId"].toString()
                    if(userCacheInformation.containsKey(userId)) {
                        var bShouldBeValid = false
                        val status = document.getLong("status")
                        if (status == 2L) {
                            bShouldBeValid = true
                        }

                        // Create user to display in the list
                        mFriendArrayList!!.add(
                            HomepageFriend(
                                userCacheInformation[userId]!!.displayName,
                                userCacheInformation[userId]!!.identifiant,
                                document["request"] as Boolean,
                                bShouldBeValid,
                                document["userId"].toString(),
                                false
                            )
                        )

                        // Current document is the last one of the list, fill list with retrieve users
                        if(mFriendArrayList!!.size == documents.size()) {
                            mFriendArrayList!!.sortBy { it.getIdentifiant() }
                            mActivityMainBinding!!.friendList.adapter =
                                HomepageFriendListAdapter(this, mFriendArrayList!!)
                            mFriendListReceptionRunning = false
                        }
                    }
                    else {
                        mDatabase!!.collection("users")
                            .document(userId).get()
                            .addOnSuccessListener { doc ->
                                Log.i("Database request", "User retrieved in HomepageActivity::retrieveFriendList - " + doc.id)

                                val userCache = MainActivity.Companion.UserInformation()
                                userCache.displayName = doc["displayName"].toString()
                                userCache.identifiant = doc["identifiant"].toString()
                                userCache.notificationToken = doc["notificationToken"].toString()

                                if("none" != doc["displayedBadge"]) {
                                    FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(userId).collection("badges")
                                        .document(doc["displayedBadge"].toString())
                                        .get().addOnSuccessListener { badgeDoc ->
                                            userCache.displayedBadge = badgeDoc["name"].toString()
                                        }
                                }
                                userCacheInformation[userId] = userCache
                                val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                                    .child("profileImages")
                                    .child("$userId.jpeg")
                                storageRef.downloadUrl.addOnSuccessListener { uri ->
                                    userCache.uri = uri

                                    var bShouldBeValid = false
                                    val status = document.getLong("status")
                                    if (status == 2L) {
                                        bShouldBeValid = true
                                    }

                                    // Create user to display in the list
                                    mFriendArrayList!!.add(
                                        HomepageFriend(
                                            userCacheInformation[userId]!!.displayName,
                                            userCacheInformation[userId]!!.identifiant,
                                            document["request"] as Boolean,
                                            bShouldBeValid,
                                            document["userId"].toString(),
                                            false
                                        )
                                    )

                                    // Current document is the last one of the list, fill list with retrieve users
                                    if(mFriendArrayList!!.size == documents.size()) {
                                        mFriendArrayList!!.sortBy { it.getIdentifiant() }
                                        mActivityMainBinding!!.friendList.adapter =
                                            HomepageFriendListAdapter(this, mFriendArrayList!!)
                                        mFriendListReceptionRunning = false
                                    }
                                }.addOnFailureListener {
                                    var bShouldBeValid = false
                                    val status = document.getLong("status")
                                    if (status == 2L) {
                                        bShouldBeValid = true
                                    }

                                    // Create user to display in the list
                                    mFriendArrayList!!.add(
                                        HomepageFriend(
                                            userCacheInformation[userId]!!.displayName,
                                            userCacheInformation[userId]!!.identifiant,
                                            document["request"] as Boolean,
                                            bShouldBeValid,
                                            document["userId"].toString(),
                                            false
                                        )
                                    )

                                    // Current document is the last one of the list, fill list with retrieve users
                                    if(mFriendArrayList!!.size == documents.size()) {
                                        mFriendArrayList!!.sortBy { it.getIdentifiant() }
                                        mActivityMainBinding!!.friendList.adapter =
                                            HomepageFriendListAdapter(this, mFriendArrayList!!)
                                        mFriendListReceptionRunning = false
                                    }
                                }

                            }.addOnFailureListener {
                                // Current document is the last one of the list, fill list with retrieve users
                                if(mFriendArrayList!!.size == documents.size()) {
                                    mFriendArrayList!!.sortBy { it.getIdentifiant() }
                                    mActivityMainBinding!!.friendList.adapter =
                                        HomepageFriendListAdapter(this, mFriendArrayList!!)
                                    mFriendListReceptionRunning = false
                                }
                            }
                    }
                }
            }.addOnFailureListener {
                mFriendListReceptionRunning = false
                findViewById<ListView>(R.id.friendList).visibility = View.GONE
                findViewById<ImageView>(R.id.friendListEmpty).visibility = View.VISIBLE
                findViewById<ConstraintLayout>(R.id.friendListSearchIssue).visibility = View.GONE
            }
    }

    private fun retrieveEventList() {
        if(mEventListReceptionRunning)
            return

        mEventListReceptionRunning = true
        // At first clean the previous list if exists
        mActivityMainBinding!!.eventList.adapter = null
        if(null != mEventArrayList)
            mEventArrayList!!.clear()

        mCurrentEventIndex = 0
        mDatabase!!.collection("users")
            .document(mCurrentUserId)
            .collection("events")
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                Log.i("Database request", "Event list retrieved in HomepageActivity::retrieveEventList")

                if(0 == documents.size()) {
                    mEventListReceptionRunning = false
                    findViewById<ListView>(R.id.eventList).visibility = View.GONE
                    findViewById<ConstraintLayout>(R.id.eventListEmpty).visibility = View.VISIBLE
                }
                else {

                    if(bInitCalendar)
                        mCalendars = ArrayList()

                    mEventArrayList = ArrayList()
                    for (ii in 0 until documents.size()) {

                        val doc = documents.documents[ii] ?: continue
                        var eventId = ""
                        if(doc.contains("eventId")) eventId = doc["eventId"].toString()
                        var bNotification = false
                        if(doc.contains("notification")) bNotification = doc["notification"] as Boolean
                        var bMessageNotification = false
                        if(doc.contains("messageNotification")) bMessageNotification = doc["messageNotification"] as Boolean

                        retrieveEvent(eventId, bNotification, bMessageNotification, documents.size())
                    }
                    findViewById<ListView>(R.id.eventList).visibility = View.VISIBLE
                    findViewById<ConstraintLayout>(R.id.eventListEmpty).visibility = View.GONE
                }
            }.addOnFailureListener {
                mEventListReceptionRunning = false
                findViewById<ListView>(R.id.eventList).visibility = View.GONE
                findViewById<ConstraintLayout>(R.id.eventListEmpty).visibility = View.VISIBLE
            }
    }

    @SuppressLint("SimpleDateFormat")
    private fun retrieveEvent(iEventId: String, ibNotification: Boolean, ibMessageNotification: Boolean, iNbEvent: Int) {
        mDatabase!!
            .collection("events")
            .document(iEventId)
            .get().addOnSuccessListener { eventDocument ->
                mCurrentEventIndex++
                if(eventDocument.exists()) {

                    var date = 0L
                    if(eventDocument.contains("date")) date = eventDocument["date"] as Long

                    if(mDisplayCalendar) {
                        if(bInitCalendar) {
                            val eventDate = Calendar.getInstance()
                            eventDate.timeInMillis = date
                            mCalendars.add(CalendarDay(eventDate).apply {
                                backgroundResource = R.drawable.round_corner_blue
                                labelColor = R.color.white
                            })
                        }

                        val appDate: Date = Calendar.getInstance().time
                        val formatter = SimpleDateFormat("EEEE d MMMM yyyy")

                        val calendar = findViewById<com.applandeo.materialcalendarview.CalendarView>(R.id.calendarView)
                        val currentSelectedDate = calendar.firstSelectedDate.timeInMillis

                        appDate.time = currentSelectedDate
                        val calendarDateString = formatter.format(appDate).toString()

                        appDate.time = date
                        val eventDateString = formatter.format(appDate).toString()

                        if(eventDateString != calendarDateString) {
                            if(mCurrentEventIndex == iNbEvent) finishEventReception()
                            return@addOnSuccessListener
                        }
                    }

                    val newDate: Date = Calendar.getInstance().time
                    val sdf = SimpleDateFormat("D")
                    sdf.timeZone = TimeZone.getDefault()
                    newDate.time = date
                    val eventDayOfYear = sdf.format(newDate)

                    newDate.time = System.currentTimeMillis()
                    val currentDayOfYear = sdf.format(newDate)
                    if(!mDisplayCalendar) {
                        if (mDisplayEventHistory && eventDayOfYear >= currentDayOfYear) {
                            if (mCurrentEventIndex == iNbEvent) finishEventReception()
                            return@addOnSuccessListener
                        } else if (!mDisplayEventHistory && eventDayOfYear < currentDayOfYear) {
                            if (mCurrentEventIndex == iNbEvent) finishEventReception()
                            return@addOnSuccessListener
                        }
                    }

                    val homepageEvent = HomepageEvent(
                        iEventId,
                        ibNotification,
                        ibMessageNotification,
                        date,
                        mDisplayCalendar
                    )

                    Log.i("Database request", "Event retrieved in HomepageActivity::retrieveEventList - "+eventDocument.id)
                    mEventArrayList!!.add(homepageEvent)

                    if(mCurrentEventIndex == iNbEvent) finishEventReception()
                }
            }
    }

    private fun finishEventReception() {
        mEventArrayList!!.sortBy { it.getDate() }

        if(mDisplayEventHistory) mEventArrayList!!.reverse()
        mActivityMainBinding!!.eventList.adapter =
            HomepageEventListAdapter(this, mEventArrayList!!)
        mEventListReceptionRunning = false

        if(mDisplayCalendar && bInitCalendar) {
            val calendar =
                findViewById<com.applandeo.materialcalendarview.CalendarView>(R.id.calendarView)
            if (mDisplayCalendar) {
                calendar.setCalendarDays(mCalendars)
            }
            bInitCalendar = false
        }
    }

    /**
     * This function is used to generate a bitmap image qr code from
     * the input string
     */
    private fun generateQRCode(iData : String) : Bitmap {
        var bitmap : Bitmap? = null
        val qrgEncoder = QRGEncoder(iData, null, QRGContents.Type.TEXT, 400)
        try {
            bitmap = qrgEncoder.encodeAsBitmap()
        } catch (e: WriterException) {
            Log.e("Tag", e.toString())
        }
        return bitmap!!
    }

    /**
     * Called at the start of the application, check if a user is already connected to the app
     * If not, return directly at the beginning the the call.
     * If user is yet connected, redirect him to the homepage activity.
     */
    private fun checkCurrentUser() {
        if(null == FirebaseAuth.getInstance().currentUser) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        else {
            initMemberData()
        }
    }

    class RecyclerItemClickListener(context: Context, recyclerView: RecyclerView, private val mListener: OnItemClickListener?) : RecyclerView.OnItemTouchListener {

        private val mGestureDetector: GestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val childView = recyclerView.findChildViewUnder(e.x, e.y)

                if (childView != null && mListener != null) {
                    mListener.onItemLongClick(childView, recyclerView.getChildAdapterPosition(childView))
                }
            }
        })

        interface OnItemClickListener {
            fun onItemClick(view: View, position: Int)

            fun onItemLongClick(view: View?, position: Int)
        }

        override fun onInterceptTouchEvent(view: RecyclerView, e: MotionEvent): Boolean {
            val childView = view.findChildViewUnder(e.x, e.y)

            if (childView != null && mListener != null && mGestureDetector.onTouchEvent(e)) {
                mListener.onItemClick(childView, view.getChildAdapterPosition(childView))
            }

            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            TODO("Not yet implemented")
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            TODO("Not yet implemented")
        }
    }


}