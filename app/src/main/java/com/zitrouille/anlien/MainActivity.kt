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
import com.google.android.gms.common.SignInButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.content.ContentValues.TAG
import android.net.Uri
import android.util.Log
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseUserMetadata
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import android.text.InputFilter
import android.text.InputFilter.AllCaps
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
        var globalUserInformations = HashMap<String, UserInformation>()

        class UserInformation {
            var mDisplayName = ""
            var mUniqueId = ""
            var mUri: Uri? = null
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
                "uniquePseudo" to ""
            )
            val db = FirebaseFirestore.getInstance()

            db.collection("users").document(currentUser.uid).get().addOnSuccessListener {
                if(it.exists()) {
                    if(it["uniquePseudo"] != "") {
                        startActivity(Intent(this, HomepageActivity::class.java))
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
            val DELAY: Long = 500 // Milliseconds
            pseudoEditText.doAfterTextChanged {
                timer.cancel()
                timer = Timer()
                timer.schedule(
                    object : TimerTask() {
                        override fun run() {
                            db.collection("users").whereEqualTo("uniquePseudo", it.toString())
                                .get().addOnSuccessListener { documents ->
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
                    DELAY
                )
            }
        }

        valid.setOnClickListener {
            val newText = pseudoEditText.text.toString()
            val userId = mAuth!!.currentUser!!.uid
            db.collection("users").document(userId).update("uniquePseudo", newText).addOnSuccessListener {
                checkCurrentUser()
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