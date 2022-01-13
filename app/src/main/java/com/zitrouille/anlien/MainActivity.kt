package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Window
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import android.text.InputFilter
import android.text.InputFilter.AllCaps
import android.widget.*
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlin.collections.HashMap

/**
 * This activity is used to display the main screen of the application
 * On this one, there should be only login and sign up button.
 * If the user is already logged, the activity automatically send the user
 * to the homepage activity.
 */

private var mFirebaseAnalytics: FirebaseAnalytics? = null
private var mAuth: FirebaseAuth? = null
private var mGoogleSignInOptions: GoogleSignInOptions? = null
private var mResultLauncher: ActivityResultLauncher<Intent>? = null
private var mDialog: Dialog? = null

class MainActivity : AppCompatActivity(), View.OnClickListener {

    /**
     * Contains a cache list of all users already retrieved.
     */
    companion object {
        var userCacheInformation = HashMap<String, UserInformation>()

        class UserInformation {
            var displayName = ""
            var identifiant = ""
            var notificationToken = ""
            var uri: Uri? = null
            var displayedBadge: String = "none"
        }

        /**
         * Retrieve the current user and update the messaging token for notifications
         */
        fun updateCurrentUserMessagingToken(iToken: String) {
            if(FirebaseAuth.getInstance().currentUser != null) {
                FirebaseFirestore.getInstance().collection("users")
                    .document(FirebaseAuth.getInstance().currentUser!!.uid)
                    .update("notificationToken", iToken)
            }
        }

        fun retrieveUserInformation(iUserId: String,
                                    iDisplayNameView: TextView?,
                                    iIdentifiantView: TextView?,
                                    iProfilePictureView: ImageView?,
                                    iBadgeView: ImageView?) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(iUserId).get()
                .addOnSuccessListener { doc ->
                    Log.i("Database request", "User retrieved in cache creation - " + doc.id)
                    val userCache = UserInformation()

                    userCache.displayName = doc["displayName"].toString()
                    userCache.identifiant = doc["identifiant"].toString()
                    userCache.notificationToken = doc["notificationToken"].toString()

                    if("none" != doc["displayedBadge"]) {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(iUserId).collection("badges")
                            .document(doc["displayedBadge"].toString())
                            .get().addOnSuccessListener { badgeDoc ->
                                userCache.displayedBadge = badgeDoc["name"].toString()
                            }
                    }

                    if(null != iDisplayNameView) {
                        iDisplayNameView.text = userCache.displayName
                    }

                    if(null != iIdentifiantView) {
                        iIdentifiantView.text = userCache.displayName
                    }

                    if(null != iBadgeView) {
                        Glide.with(iBadgeView.context).load(retrieveBadge(userCache.displayedBadge))
                            .into(iBadgeView)
                    }

                    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                        .child("profileImages")
                        .child("$iUserId.jpeg")

                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        if(null != uri) {
                            userCache.uri = uri
                            if (null != iProfilePictureView) {
                                Glide.with(iProfilePictureView.context).load(userCache.uri)
                                    .into(iProfilePictureView)
                            }
                        }
                    }
                    userCacheInformation[iUserId] = userCache
                }
        }

        fun retrieveBadge (iName: String) : Int {
            if("alpha" == iName) return R.drawable.alpha
            if("creator" == iName) return R.drawable.king
            if("nde" == iName) return R.drawable.nde
            return R.drawable.transparent
        }
        fun retrieveBadgeLarge (iName: String) : Int {
            if("alpha" == iName) return R.drawable.badge_alpha
            if("creator" == iName) return R.drawable.badge_king
            if("nde" == iName) return R.drawable.badge_nde
            return R.drawable.transparent
        }
        fun retrieveBadgeName (iName: String, iContext: Context) : String {
            if("alpha" == iName) return iContext.getString(R.string.alpha_user)
            if("creator" == iName) return "Créateur"
            if("nde" == iName) return "Nicolas Dolé"
            return "Sans nom"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ImageView>(R.id.sign_in_button).setOnClickListener(this)

        mGoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        mAuth = FirebaseAuth.getInstance()

        mResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if(Activity.RESULT_OK == result.resultCode) {
                val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                signInProcess(task)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        checkCurrentUser()
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.sign_in_button -> signInActivity()
        }
    }

    /**
     * When user click on Google sign in button, it starts the default sign in activity from Google
     * Then when user select an account or cancel the connexion process,
     * goto registerForActivityResult to analyze the result of this activity.
     */
    private fun signInActivity() {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.sign_in_button).visibility = View.GONE
        val googleSignInClient = GoogleSignIn.getClient(this, mGoogleSignInOptions!!)
        val signInIntent = googleSignInClient.signInIntent
        mResultLauncher!!.launch(signInIntent)
    }

    /**
     * Analyse result of the sign in process.
     * In case of success, redirect user to homepage activity.
     * In case of failure, cancel the sign in process and display error message in the log.
     */
    private fun signInProcess(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)

            val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
            mAuth!!.signInWithCredential(credential).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                }
                checkCurrentUser()
            }
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
        }
    }

    /**
     * Called at the start of the application, check if a user is already connected to the app
     * If not, return directly at the beginning the the call.
     * If user is yet connected, redirect him to the homepage activity.
     */
    private fun checkCurrentUser() {
        val currentUser = mAuth!!.currentUser
        if(null != currentUser) {
            val userInformation = hashMapOf(
                "displayName" to currentUser.displayName,
                "identifiant" to "",
                "notificationToken" to "",
                "friendNotification" to false,
                "displayedBadge" to "none",
            )
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(currentUser.uid).get().addOnSuccessListener { doc->
                Log.i("Database request", "User retrieved in MainActivity::checkCurrentUser - "+doc.id)
                if(doc.exists()) {
                    if(doc["identifiant"] != "") {
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            updateCurrentUserMessagingToken(token)
                        }

                        val userCache = UserInformation()
                        userCache.displayName = doc["displayName"] as String
                        userCache.identifiant = doc["identifiant"] as String
                        userCache.notificationToken = doc["notificationToken"] as String

                        if("none" != doc["displayedBadge"]) {
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(currentUser.uid).collection("badges")
                                .document(doc["displayedBadge"].toString())
                                .get().addOnSuccessListener { badgeDoc ->
                                    userCache.displayedBadge = badgeDoc["name"].toString()
                                }
                        }

                        val userId = currentUser.uid
                        val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                            .child("profileImages")
                            .child("$userId.jpeg")
                        storageRef.downloadUrl.addOnSuccessListener {
                            userCache.uri = it
                        }
                        userCacheInformation[userId] = userCache

                        checkNewBadges(currentUser.uid)
                    }
                    else {
                        displayPseudoCreation()
                    }
                }
                else {
                    db.collection("users").document(currentUser.uid)
                        .set(userInformation)
                        .addOnSuccessListener {
                            displayPseudoCreation()
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Error adding document", e)
                        }
                }
            }
        }
        else {
            findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
            findViewById<ImageView>(R.id.sign_in_button).visibility = View.VISIBLE
        }
    }

    private fun checkNewBadges(iUserId: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(iUserId)
            .collection("badges")
            .whereEqualTo("new", true).get().addOnSuccessListener { newBadges ->
                if(0 != newBadges.size()) {
                    val doc = newBadges.documents[0]
                    if(doc.exists()) {
                        val badgeData = hashMapOf(
                            "name" to doc["name"].toString(),
                        )
                        doc.reference.set(badgeData).addOnSuccessListener {
                            mDialog = Dialog(this)
                            mDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
                            mDialog!!.setCancelable(false)
                            mDialog!!.setContentView(R.layout.dialog_new_badge)
                            mDialog!!.findViewById<TextView>(R.id.badge_name).text =
                                retrieveBadgeName(doc["name"].toString(), applicationContext)
                            Glide.with(applicationContext)
                                .load(retrieveBadgeLarge(doc["name"].toString()))
                                .into(mDialog!!.findViewById(R.id.badge_icon))
                            mDialog!!.window!!.setBackgroundDrawableResource(android.R.color.transparent)
                            mDialog!!.show()

                            mDialog!!.findViewById<ImageView>(R.id.badge_icon).animate().rotation(360.0f).start()

                            mDialog!!.findViewById<ImageView>(R.id.valid).setOnClickListener {
                                checkCurrentUser()
                            }
                        }.addOnFailureListener {
                            startActivity(Intent(this, HomepageActivity::class.java))
                        }
                    }
                    else {
                        startActivity(Intent(this, HomepageActivity::class.java))
                    }
                }
                else {
                    startActivity(Intent(this, HomepageActivity::class.java))
                }
            }
    }

    @SuppressLint("CutPasteId")
    private fun displayPseudoCreation() {
        mDialog = Dialog(this)
        mDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        mDialog!!.setCancelable(false)
        mDialog!!.setContentView(R.layout.dialog_main_create_pseudo)
        mDialog!!.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        mDialog!!.show()

        val valid = mDialog!!.findViewById<ImageView>(R.id.valid)
        val warning = mDialog!!.findViewById<ImageView>(R.id.warning)
        val wrong = mDialog!!.findViewById<ImageView>(R.id.wrong)

        val pseudoEditText = mDialog!!.findViewById(R.id.pseudo) as EditText
        pseudoEditText.filters = arrayOf<InputFilter>(AllCaps())


        // Check if the current pseudo is not yet in the database
        val db = FirebaseFirestore.getInstance()
        pseudoEditText.addTextChangedListener {
            var timer = Timer()
            val delay: Long = 500 // Milliseconds
            pseudoEditText.doAfterTextChanged {
                timer.cancel()
                timer = Timer()
                timer.schedule(
                    object : TimerTask() {
                        override fun run() {
                            db.collection("users").whereEqualTo("identifiant", it.toString())
                                .get().addOnSuccessListener { documents ->
                                    Log.i("Database request", "Filtered user list retrieved in MainActivity::displayPseudoCreation")
                                    if (documents.size() > 0) {
                                        valid.animate().alpha(0F).withEndAction {
                                            valid.visibility = View.GONE
                                        }.start()
                                        warning.animate().alpha(0F).withEndAction {
                                            warning.visibility = View.INVISIBLE
                                        }.start()
                                        wrong.visibility = View.VISIBLE
                                        wrong.animate().alpha(1F).rotation(wrong.rotation+360F).start()
                                    } else {
                                        wrong.animate().alpha(0F).withEndAction {
                                            wrong.visibility = View.GONE
                                        }.start()
                                        warning.animate().alpha(0F).withEndAction {
                                            warning.visibility = View.INVISIBLE
                                        }.start()
                                        valid.visibility = View.VISIBLE
                                        valid.animate().alpha(1F).rotation(valid.rotation+360F).start()
                                    }
                            }.addOnFailureListener {
                                wrong.animate().alpha(0F).withEndAction {
                                    wrong.visibility = View.GONE
                                }.start()
                                valid.animate().alpha(0F).withEndAction {
                                    valid.visibility = View.GONE
                                }.start()
                                warning.visibility = View.VISIBLE
                                warning.animate().alpha(1F).rotation(warning.rotation+360F).start()
                            }
                        }
                    },
                    delay
                )
            }
        }

        valid.setOnClickListener {
            val newText = pseudoEditText.text.toString()
            val userId = mAuth!!.currentUser!!.uid
            db.collection("users").document(userId).update("identifiant", newText).addOnSuccessListener {
                FirebaseFirestore.getInstance().collection("users").document(userId).get().addOnSuccessListener { doc ->
                    val alphaBadge = hashMapOf(
                        "name" to "alpha",
                        "new" to "true",
                    )
                    doc.reference.collection("badges").add(alphaBadge).addOnSuccessListener { badgeDoc ->
                        doc.reference.update("displayedBadge", badgeDoc.id).addOnSuccessListener {
                            checkCurrentUser()
                        }
                    }
                }
            }
        }

        warning.setOnClickListener {
            Toast.makeText(mDialog!!.context, "Saisissez un identifiant", Toast.LENGTH_LONG).show()
        }

        wrong.setOnClickListener {
            Toast.makeText(mDialog!!.context, "Identifiant non disponible", Toast.LENGTH_LONG).show()
        }

    }
}