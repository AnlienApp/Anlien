package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import android.widget.EditText





class CreateEventActivity : AppCompatActivity() {

    private var mAuth: FirebaseAuth? = null
    private var mDatabase: FirebaseFirestore? = null
    private var mStorage: FirebaseStorage? = null

    private var mDialog: Dialog? = null

    private var mParticipantList: ArrayList<CreateEventParticipant>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_event)

        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val informationView: View = bottomMenu.findViewById(R.id.nav_info)
        informationView.performClick()

        val backView: View = bottomMenu.findViewById(R.id.nav_back)
        backView.setOnClickListener {
            finish()
        }

        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseFirestore.getInstance()
        mStorage = FirebaseStorage.getInstance()

        initDatePicker(findViewById(R.id.date))
        initParticipantList()
        initCreatetionButton()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev!!.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm =
                        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun initCreatetionButton() {
        findViewById<AppCompatButton>(R.id.create_event_button).setOnClickListener {
            val title = findViewById<EditText>(R.id.title).text
            val address = findViewById<EditText>(R.id.address).text
            val date = findViewById<TextView>(R.id.date).text
            if(title.isNotEmpty() && address.isNotEmpty() && title.isNotBlank() && address.isNotBlank()) {
                val currentEventData = hashMapOf(
                    "title" to title.toString(),
                    "address" to address.toString(),
                    "date" to date.toString(),
                    "organizerId" to mAuth!!.currentUser!!.uid
                )
                mDatabase!!.collection("events").add(currentEventData)
                    .addOnSuccessListener { event ->

                        val data = hashMapOf(
                            "eventId" to event.id,
                        )
                        mDatabase!!.collection("users").document(mAuth!!.currentUser!!.uid).collection("events").add(data).addOnSuccessListener {
                            if (null != mParticipantList) {
                                if(0 == mParticipantList!!.size) {
                                    finish()
                                }
                                for (participant in mParticipantList!!) {
                                    val participantData = hashMapOf(
                                        "userId" to participant.getUserId(),
                                        "status" to 1, // 0 cancel, 1 pending, 2 accept
                                    )
                                    event.collection("participants").add(participantData)
                                        .addOnSuccessListener {
                                            mDatabase!!.collection("users").document(participant.getUserId()).collection("events").add(data).addOnSuccessListener {
                                                if(participant == mParticipantList!![mParticipantList!!.size-1])
                                                    finish()
                                            }
                                        }
                                }
                            }
                            else {
                                finish()
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(applicationContext, getString(R.string.creation_failed), Toast.LENGTH_LONG).show()
                    }
            }
            else {
                Toast.makeText(applicationContext, getString(R.string.error_name_or_address), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initParticipantList() {
        val addParticipant = findViewById<ImageView>(R.id.add_participant)
        val editListParticipant = findViewById<ImageView>(R.id.participant_list_edit)

        addParticipant.setOnClickListener {
            addParticipant.animate().rotation(addParticipant.rotation +360.0f).withEndAction {
                openParticipantSelectionDialog()
            }.start()
        }
        editListParticipant.setOnClickListener {
            openParticipantSelectionDialog()
        }
    }

    /**
     * This diaolg display all user friend and set them to green if they are already selected
     * When user click on a friend, switch the participation status of the friend
     */
    private fun openParticipantSelectionDialog() {
        mDialog = Dialog(this)
        mDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        mDialog!!.setCancelable(false)
        mDialog!!.setCanceledOnTouchOutside(true)
        mDialog!!.setContentView(R.layout.dialog_create_event_friend_list)
        mDialog!!.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        mDialog!!.show()

        mDialog!!.findViewById<LinearLayout>(R.id.main_layout).animate().alpha(1.0F).start()

        //Retrieve list of all current user friends
        val recyclerView: RecyclerView = mDialog!!.findViewById(R.id.friend_list)
        val participantList: ArrayList<CreateEventFriend> = ArrayList()
        mDatabase!!.collection("users")
            .document(mAuth!!.currentUser!!.uid)
            .collection("friends").get().addOnSuccessListener { documents ->

                val recyclerView1 = findViewById<RecyclerView>(R.id.participant_list)
                var adapter1 : CreateEventParticipantListAdapter? = null
                if(null != recyclerView1.adapter)
                    adapter1 = recyclerView1.adapter as CreateEventParticipantListAdapter
                for(doc in documents) {
                    if(null == doc) continue
                    val userId = doc.getString("userId").toString()
                    var bIsPresent = false
                    if(null != adapter1)
                        bIsPresent = adapter1!!.isPresent(userId)
                    participantList.add(CreateEventFriend(userId, bIsPresent))
                }

                val linearLayoutManager = LinearLayoutManager(applicationContext)
                linearLayoutManager.orientation = RecyclerView.HORIZONTAL
                recyclerView.layoutManager = linearLayoutManager
                recyclerView.setHasFixedSize(true)

                recyclerView.adapter = CreateEventFriendListAdapter(participantList)
            }

        mDialog!!.findViewById<ImageView>(R.id.valid_current_list).setOnClickListener {
            val newParticipantList: ArrayList<CreateEventParticipant> = ArrayList() // Contains ID
            for(participant in participantList) {
                val adapter = recyclerView.adapter as CreateEventFriendListAdapter
                val value = adapter.getSelectionStatus(participant.getUserId())
                if(value) {
                    newParticipantList.add(CreateEventParticipant(participant.getUserId()))
                }
            }
            setParticipantList(newParticipantList)
            mDialog!!.dismiss()
        }
    }

    private fun setParticipantList(iParticipantList: ArrayList<CreateEventParticipant>) {
        val recyclerView: RecyclerView = findViewById(R.id.participant_list)
        val linearLayoutManager = LinearLayoutManager(applicationContext)
        linearLayoutManager.orientation = RecyclerView.HORIZONTAL
        recyclerView.adapter = null
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = CreateEventParticipantListAdapter(iParticipantList)

        if(0 != iParticipantList.size) {
            findViewById<ImageView>(R.id.add_participant).visibility = View.GONE
            findViewById<ImageView>(R.id.participant_list_edit).visibility = View.VISIBLE
        }
        else {
            findViewById<ImageView>(R.id.add_participant).visibility = View.VISIBLE
            findViewById<ImageView>(R.id.participant_list_edit).visibility = View.GONE
        }

        mParticipantList = iParticipantList
    }

    /**
     * Set the current date to the date picker
     * and initialize date picker callback
     */
    @SuppressLint("SimpleDateFormat")
    private fun initDatePicker(iView: TextView) {
        val today: Date = Calendar.getInstance().time //getting date
        val formatter = SimpleDateFormat("EEEE d MMMM yyyy") //formating according to my need
        val date: String = formatter.format(today)
        iView.text = date

        // Display date picker
        iView.setOnClickListener {
            displayDatePicker(iView)
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun displayDatePicker(iView: TextView) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.choose_date))
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        datePicker.show(supportFragmentManager, "datePicker")
        datePicker.addOnPositiveButtonClickListener {
            val newDate: Date = Calendar.getInstance().time //getting date
            val formatter = SimpleDateFormat("EEEE d MMMM yyyy") //formating according to my need
            newDate.time = it
            iView.text = formatter.format(newDate)
        }
    }

}