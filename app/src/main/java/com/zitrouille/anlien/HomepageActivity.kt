package com.zitrouille.anlien

import android.app.Activity
import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
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
import com.bumptech.glide.Glide
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView

class HomepageActivity : AppCompatActivity() {

    private var mActivityMainBinding: ActivityHomepageBinding? = null
    private var mEventArrayList: ArrayList<HomepageEvent>? = null
    private var mFriendArrayList: ArrayList<HomepageFriend>? = null

    private var mQRCodeResultLauncher: ActivityResultLauncher<Intent>? = null

    private var mAddFriendDialog: Dialog? = null

    private var mAuth: FirebaseAuth? = null
    private var mDatabase: FirebaseFirestore? = null
    private var mStorage: FirebaseStorage? = null

    private var mfriendListReceptionRunning = false

    /**
     * Init different behaviors when user click on the bottom navigation menu
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mActivityMainBinding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(mActivityMainBinding!!.root)

        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseFirestore.getInstance()
        mStorage = FirebaseStorage.getInstance()

        initializeBottomNavigation()

        initializeQrCodeCallback() // Callback manager
        initializeProfilePicture() // Personal profile picture
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
                    displayPersonnalProfilePage()
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
        if(null != mAuth!!.currentUser!!.photoUrl) {
            Glide.with(applicationContext).load(mAuth!!.currentUser!!.photoUrl)
                .into(findViewById(R.id.profile_picture))
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
                if(otherUserId == mAuth!!.currentUser!!.uid) {
                    mDatabase!!.collection("users").document(otherUserId).get().addOnSuccessListener { document ->
                        displayAddFriendDialog(
                            document,
                            ibCanBeAdded = false,
                            ibPending = false,
                            ibYourSelf = true
                        )
                    }
                }

                /**
                 * User scan QR code from another user
                 */
                if(otherUserId != mAuth!!.currentUser!!.uid) {
                    mDatabase!!.collection("users").document(otherUserId).get()
                        .addOnSuccessListener { document ->
                            if (document != null) {
                                mDatabase!!.collection("users").document(otherUserId)
                                    .collection("friends")
                                    .whereEqualTo("userId", mAuth!!.currentUser!!.uid)
                                    .get().addOnSuccessListener { documents ->

                                        /**
                                         * User already add this other user
                                         */
                                        if (documents.size() != 0) {
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


        val addFriendImageView = mAddFriendDialog!!.findViewById(R.id.add_friend) as ImageView
        val yourselfImageView = mAddFriendDialog!!.findViewById(R.id.yourself) as ImageView
        if(ibYourSelf) {
            addFriendImageView.visibility = View.GONE
            yourselfImageView.visibility = View.VISIBLE
            yourselfImageView.setOnClickListener { currentView ->
                Snackbar.make(currentView, "C'est vous !", Snackbar.LENGTH_LONG)
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
                        Snackbar.make(currentView, "Demande déjà envoyée", Snackbar.LENGTH_LONG)
                            .show()
                    }
                } else {
                    addFriendImageView.visibility = View.GONE
                    val friendAddedImageView =
                        mAddFriendDialog!!.findViewById(R.id.friend_added) as ImageView
                    friendAddedImageView.visibility = View.VISIBLE
                    friendAddedImageView.setOnClickListener { currentView ->
                        Snackbar.make(currentView, "Vous êtes déjà amis", Snackbar.LENGTH_LONG)
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
        mDatabase!!.collection("users").document(mAuth!!.currentUser!!.uid).collection("friends")
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
            "userId" to mAuth!!.currentUser!!.uid,
            "request" to true,
            "status" to 2
        )
        mDatabase!!.collection("users").document(iUserId).collection("friends")
            .add(otherUserData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot written with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
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
        }
        else {
            findViewById<RelativeLayout>(R.id.eventPage).animate().alpha(0F).withEndAction {
                findViewById<RelativeLayout>(R.id.eventPage).visibility = View.GONE
            }
            findViewById<ImageView>(R.id.create_event).animate().alpha(0F).withEndAction {
                findViewById<ImageView>(R.id.create_event).visibility = View.GONE
            }
        }
    }

    private fun friendpageVisibility(ibValue : Boolean) {
        if(ibValue) {
            findViewById<RelativeLayout>(R.id.friendPage).animate().alpha(1F).withEndAction {
                findViewById<RelativeLayout>(R.id.friendPage).visibility = View.VISIBLE
            }
            findViewById<ImageView>(R.id.add_friend).animate().alpha(1F).withEndAction {
                findViewById<ImageView>(R.id.add_friend).visibility = View.VISIBLE
            }
        }
        else {
            findViewById<RelativeLayout>(R.id.friendPage).animate().alpha(0F).withEndAction {
                findViewById<RelativeLayout>(R.id.friendPage).visibility = View.GONE
            }
            findViewById<ImageView>(R.id.add_friend).animate().alpha(0F).withEndAction {
                findViewById<ImageView>(R.id.add_friend).visibility = View.GONE
            }
        }
    }

    private fun userpageVisibility(ibValue : Boolean) {
        if(ibValue) {
            findViewById<RelativeLayout>(R.id.mainUserPage).animate().alpha(1F).withEndAction {
                findViewById<RelativeLayout>(R.id.mainUserPage).visibility = View.VISIBLE
            }
        }
        else {
            findViewById<RelativeLayout>(R.id.mainUserPage).animate().alpha(0F).withEndAction {
                findViewById<RelativeLayout>(R.id.mainUserPage).visibility = View.GONE
            }
        }
    }

    private fun displayEventListPage() {
        findViewById<TextView>(R.id.my_activity).text = getString(R.string.my_activity)
        homepageVisibility(true)
        friendpageVisibility(false)
        findViewById<RelativeLayout>(R.id.mainUserPage).animate().alpha(0F).withEndAction {
            findViewById<RelativeLayout>(R.id.mainUserPage).visibility = View.GONE
        }

        if(null == mEventArrayList) {
            /*val titleList = arrayOf(
                "Nouvel année",
                "Anniversaire de Maude",
                "Week end au ski",
                "Restaurant entre potes",
                "Anniversaire de Hugo",
                "Accrobranche",
            )*/

            mEventArrayList = ArrayList()
            /*for(ii in titleList.indices) {
                val event = HomepageEvent(titleList[ii])
                mEventArrayList!!.add(event)
            }*/

            if(0 == mEventArrayList!!.size) {
                findViewById<ConstraintLayout>(R.id.eventListEmpty).visibility = View.VISIBLE
                findViewById<ListView>(R.id.eventList).visibility = View.GONE
            }
            else {
                findViewById<ConstraintLayout>(R.id.eventListEmpty).visibility = View.GONE
                findViewById<ListView>(R.id.eventList).visibility = View.VISIBLE
                mActivityMainBinding!!.eventList.isClickable = true
                mActivityMainBinding!!.eventList.adapter = HomepageEventListAdapter(this, mEventArrayList!!)
            }
        }
    }

    private fun displayFriendPage() {
        findViewById<TextView>(R.id.my_activity).text = getString(R.string.my_friends)
        homepageVisibility(false)
        friendpageVisibility(true)
        userpageVisibility(false)

        if(null == mFriendArrayList) {
            retrieveFriendList()
            findViewById<ImageView>(R.id.add_friend).setOnClickListener {
                mQRCodeResultLauncher!!.launch(Intent(this, ScannerActivity::class.java))
            }
            findViewById<ImageView>(R.id.add_friend_reminder).setOnClickListener {
                mQRCodeResultLauncher!!.launch(Intent(this, ScannerActivity::class.java))
            }
        }
        retrieveFriendList()

    }

    private fun displayPersonnalProfilePage() {
        findViewById<TextView>(R.id.my_activity).text = getString(R.string.my_options)
        homepageVisibility(false)
        friendpageVisibility(false)
        userpageVisibility(true)

        val currentUser = mAuth!!.currentUser
        if(null != currentUser) {
            findViewById<TextView>(R.id.userName).text = currentUser.displayName
            findViewById<ImageView>(R.id.qr_code).setImageBitmap(generateQRCode(currentUser.uid))
        }

        findViewById<ImageView>(R.id.profile_picture).setOnClickListener {
            // start picker to get image for cropping and then use the image in cropping activity
            CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON)
                .setCropShape(CropImageView.CropShape.OVAL)
                .setFixAspectRatio(true)
                .start(this)
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
        val baos = ByteArrayOutputStream()
        iBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val uid: String = mAuth!!.currentUser!!.uid
        val storageRef: StorageReference = mStorage!!.reference
            .child("profileImages")
            .child("$uid.jpeg")
        storageRef.putBytes(baos.toByteArray()).addOnSuccessListener {
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
        mAuth!!.currentUser!!.updateProfile(request).addOnSuccessListener {
            Toast.makeText(this, "Photo mise à jour", Toast.LENGTH_LONG).show()
            Glide.with(applicationContext).load(iUri).into(findViewById(R.id.profile_picture))
        }.addOnFailureListener {
            Toast.makeText(this, "Echec de la mise à jour", Toast.LENGTH_LONG).show()
        }
    }


    /**
     * Clean existing friend list displayed in the app and retrieve new one
     * from the firebase database. Be carefully and call it only only when necessary.
     */
    fun retrieveFriendList() {
        if(mfriendListReceptionRunning)
            return
        mfriendListReceptionRunning = true
        // At first clean the previous list if exists
        mActivityMainBinding!!.friendList.adapter = null
        if(null != mFriendArrayList)
            mFriendArrayList!!.clear()

        mActivityMainBinding!!.friendList.isClickable = false

        /**
         * Retrieve all friends from the current user
         */
        mDatabase!!.collection("users")
            .document(mAuth!!.currentUser!!.uid)
            .collection("friends").get().addOnSuccessListener { documents ->
                mFriendArrayList = ArrayList()

                if(0 == documents.size()) {
                    mfriendListReceptionRunning = false
                    findViewById<ListView>(R.id.friendList).visibility = View.GONE
                    findViewById<ConstraintLayout>(R.id.friendListEmpty).visibility = View.VISIBLE
                }
                else {
                    findViewById<ListView>(R.id.friendList).visibility = View.VISIBLE
                    findViewById<ConstraintLayout>(R.id.friendListEmpty).visibility = View.GONE
                }

                for(document in documents) {
                    /**
                     * The current friend information is not well defined.
                     * If this is the last document of the list, finish the creation.
                     * In other case, juste continue into the next friend document.
                     */
                    if(null == document) {
                        // Current document is the last one of the list, fill list with retrieve users
                        if(document == documents.documents[documents.size()-1]) {
                            mActivityMainBinding!!.friendList.adapter = HomepageFriendListAdapter(this, mFriendArrayList!!)
                            mfriendListReceptionRunning = false
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
                    mDatabase!!.collection("users")
                        .document(document["userId"].toString()).get().addOnSuccessListener { doc ->
                            var bShouldBeValid = false
                            val status = document.getLong("status")
                            if(status == 2L) {
                                bShouldBeValid = true
                            }

                            // Create user to display in the list
                            mFriendArrayList!!.add(HomepageFriend(
                                doc["displayName"].toString(),
                                document["request"] as Boolean,
                                bShouldBeValid,
                                document["userId"].toString()
                            ))

                            // Current document is the last one of the list, fill list with retrieve users
                            if(document == documents.documents[documents.size()-1]) {
                                mActivityMainBinding!!.friendList.adapter = HomepageFriendListAdapter(this, mFriendArrayList!!)
                                mfriendListReceptionRunning = false
                            }
                        }.addOnFailureListener {
                            // Current document is the last one of the list, fill list with retrieve users
                            if(document == documents.documents[documents.size()-1]) {
                                mActivityMainBinding!!.friendList.adapter = HomepageFriendListAdapter(this, mFriendArrayList!!)
                                mfriendListReceptionRunning = false
                            }
                        }
                }
            }.addOnFailureListener {
                mfriendListReceptionRunning = false
                findViewById<ListView>(R.id.friendList).visibility = View.GONE
                findViewById<ImageView>(R.id.friendListEmpty).visibility = View.VISIBLE
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
        val currentUser = mAuth!!.currentUser
        if(null == currentUser) {
            startActivity(Intent(this, HomepageActivity::class.java))
        }
    }

}