package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.*
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
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.zitrouille.anlien.MainActivity.Companion.userCacheInformation

class CreateEventActivity : AppCompatActivity() {

    private var mCurrentUserId: String? = ""
    private var mDatabase: FirebaseFirestore? = null
    private var mStorage: FirebaseStorage? = null

    private var mDateInMillis = 0L
    private var mPlaceId: String = ""
    private var mPlaceLatitude = 0.0
    private var mPlaceLongitude = 0.0

    private var mDialog: Dialog? = null

    private var mParticipantList: ArrayList<CreateEventParticipant>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_event)

        val backView: ImageView = findViewById(R.id.back)
        backView.setOnClickListener {
            finish()
        }

        findViewById<EditText>(R.id.title).inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        mCurrentUserId = FirebaseAuth.getInstance().currentUser!!.uid
        mDatabase = FirebaseFirestore.getInstance()
        mStorage = FirebaseStorage.getInstance()

        initDatePicker(findViewById(R.id.date))
        initHourPicker(findViewById(R.id.hour))
        initParticipantList()
        initCreationButton()
        initAddressAutocomplete()
    }

    private fun initAddressAutocomplete() {
        var bEditedByAutoComple = false
        val result = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if(Activity.RESULT_OK == result.resultCode) {
                result.data?.let {
                    val place = Autocomplete.getPlaceFromIntent(result.data)
                    bEditedByAutoComple = true
                    findViewById<EditText>(R.id.address).setText(place.name)
                    mPlaceId = place.id
                    mPlaceLatitude = place.latLng.latitude
                    mPlaceLongitude = place.latLng.longitude
                    bEditedByAutoComple = false
                }
            }
        }
        findViewById<ImageView>(R.id.address_autocomplete).setOnClickListener {
            Places.initialize(applicationContext, "AIzaSyDx1OzT1RsMRkbBAHHmDAb6z_gPKGrK-LI")
            val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .build(this)
            result.launch(intent)
        }
        findViewById<EditText>(R.id.address).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if(!bEditedByAutoComple) {
                    mPlaceId = ""
                    mPlaceLatitude = 0.0
                    mPlaceLongitude = 0.0
                }
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

    /**
     * If the user is not more connectect
     * go back to the main activity screen
     */
    override fun onResume() {
        super.onResume()
        checkCurrentUser()
    }

    /**
     * If the user is not more connectect
     * go back to the main activity screen
     */
    override fun onStart() {
        super.onStart()
        checkCurrentUser()
    }

    /**
     * When user wants to hide the leyboard, remove the current
     * editext focus to remove the vertical line on it
     */
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

    /**
     * Used for error in the current activity.
     * To avoid crash, send the user to the homepage activity
     */
    private fun goToHomepage() {
        startActivity(Intent(this, HomepageActivity::class.java))
    }

    /**
     * This is the create event button set on click listener initialization.
     * If the title and address are not empty, create a new entry in the
     * database with event information.
     * If creation is successfull
     *
     */
    private fun initCreationButton() {
        val createEventButton = findViewById<TextView>(R.id.create_event_button)
        createEventButton.setOnClickListener {
            val title = findViewById<EditText>(R.id.title).text
            val address = findViewById<EditText>(R.id.address).text
            val hour = findViewById<TextView>(R.id.hour).text
            val description = findViewById<TextView>(R.id.description).text
            if(title.isNotEmpty() && address.isNotEmpty() && title.isNotBlank() && address.isNotBlank()) {
                val currentEventData = hashMapOf(
                    "title" to title.toString(),
                    "address" to address.toString(),
                    "lock" to false,
                    "date" to mDateInMillis,
                    "hour" to hour.toString(),
                    "description" to description.toString(),
                    "organizerId" to mCurrentUserId,
                    "placeId" to mPlaceId,
                    "latitude" to mPlaceLatitude,
                    "longitude" to mPlaceLongitude,
                )

                // Create the event entry in the database
                mDatabase!!.collection("events").add(currentEventData)
                    .addOnSuccessListener { createdEvent ->

                        val data = hashMapOf(
                            "eventId" to createdEvent.id,
                            "notification" to true
                        )

                        // Current user is the organizer of the event, add an entry in his events list.
                        mDatabase!!.collection("users").document(mCurrentUserId.toString()).collection("events").add(data).addOnSuccessListener {
                            if(null == mParticipantList || 0 == mParticipantList!!.size) {
                                finishEventCreation(createdEvent.id, mCurrentUserId.toString())
                            }
                            else {
                                for (participant in mParticipantList!!) {
                                    val participantData = hashMapOf(
                                        "userId" to participant.getUserId(),
                                        "status" to 1, // 0 cancel, 1 pending, 2 accept
                                    )
                                    // For each participant, add document in the created event with the presence status
                                    // AND add a document in the participant user event collection with the event data
                                    // AND send notification to the participant
                                    createdEvent.collection("participants").add(participantData)
                                        .addOnSuccessListener {
                                            mDatabase!!.collection("users")
                                                .document(participant.getUserId()).get()
                                                .addOnSuccessListener { participantUser ->
                                                    participantUser.reference.collection("events")
                                                        .add(data).addOnSuccessListener {
                                                        val notification =
                                                            FirebaseNotificationSender(
                                                                participantUser["notificationToken"].toString(),
                                                                "Invitation",
                                                                userCacheInformation[mCurrentUserId]!!.displayName + " vous invite à l'évènement " + title.toString(),
                                                                this
                                                            )
                                                        notification.SendNotification()
                                                        if (participant == mParticipantList!![mParticipantList!!.size - 1])
                                                            finishEventCreation(
                                                                createdEvent.id,
                                                                mCurrentUserId.toString()
                                                            )
                                                    }
                                                }
                                        }
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        goToHomepage()
                    }
            }
            else {
                // All required fields are not fill
                Toast.makeText(applicationContext, getString(R.string.error_name_or_address), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Make create event button rotation animation and then go to the desired activity
     */
    private fun finishEventCreation(iEventId: String, iOrganizerId: String) {
        finish()
        val intent =
            Intent(applicationContext, EventActivity::class.java)
        intent.putExtra("eventId", iEventId)
        intent.putExtra("organizerId", iOrganizerId)
        startActivity(intent)
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
            .document(mCurrentUserId.toString())
            .collection("friends").get().addOnSuccessListener { documents ->
                Log.i("Database request", "Friend list retrieved in CreateEventActivity::openParticipantSelectionDialog")
                val recyclerView1 = findViewById<RecyclerView>(R.id.participant_list)
                var adapter1 : CreateEventParticipantListAdapter? = null
                if(null != recyclerView1.adapter)
                    adapter1 = recyclerView1.adapter as CreateEventParticipantListAdapter
                for(doc in documents) {
                    if(null == doc) continue
                    if(doc["status"] == 1L) continue
                    val userId = doc.getString("userId").toString()
                    var bIsPresent = false
                    if(null != adapter1)
                        bIsPresent = adapter1.isPresent(userId)
                    participantList.add(CreateEventFriend(userId, bIsPresent))
                }

                val linearLayoutManager = LinearLayoutManager(applicationContext)
                linearLayoutManager.orientation = RecyclerView.VERTICAL
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
    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    private fun initDatePicker(iView: TextView) {
        val today: Date = Calendar.getInstance().time //getting date
        val formatter = SimpleDateFormat("EEEE d MMMM yyyy") //formating according to my need
        val date: String = formatter.format(today)
        mDateInMillis = Calendar.getInstance().timeInMillis
        iView.text = date.substring(0, 1)
            .uppercase(Locale.getDefault()) + date.substring(1)
            .lowercase(Locale.getDefault())

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
            .setTheme(R.style.MaterialCalendarTheme)
            .build()
        datePicker.show(supportFragmentManager, "datePicker")
        datePicker.addOnPositiveButtonClickListener {
            val newDate: Date = Calendar.getInstance().time //getting date
            val formatter = SimpleDateFormat("EEEE d MMMM yyyy") //formating according to my need
            newDate.time = it
            iView.text = formatter.format(newDate)
            mDateInMillis = it
        }
    }

    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    private fun initHourPicker(iView: TextView) {
        val rightNow = Calendar.getInstance()
        val currentHourIn24Format = rightNow[Calendar.HOUR_OF_DAY]
        val currentMinute = rightNow[Calendar.MINUTE]

        if(10 > currentHourIn24Format) {
            if(10 > currentMinute) {
                iView.text = "0$currentHourIn24Format : 0$currentMinute"
            }
            else {
                iView.text = "0$currentHourIn24Format : $currentMinute"
            }
        }
        else {
            if(10 > currentMinute) {
                iView.text = "$currentHourIn24Format : 0$currentMinute"
            }
            else {
                iView.text = "$currentHourIn24Format : $currentMinute"
            }
        }
        // Display hour picker
        iView.setOnClickListener {
            displayHourPicker(iView)
        }
    }

    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    private fun displayHourPicker(iView: TextView) {
        val rightNow = Calendar.getInstance()
        val currentHourIn24Format = rightNow[Calendar.HOUR_OF_DAY]
        val currentMinute = rightNow[Calendar.MINUTE]
        val picker =
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(currentHourIn24Format)
                .setMinute(currentMinute)
                .setTheme(R.style.TimePickerTheme)
                .setTitleText(getString(R.string.seletect_time))
                .build()
        picker.show(supportFragmentManager, "timePicker")
        picker.addOnPositiveButtonClickListener {
            if(10 > picker.hour) {
                if(10 > picker.minute) {
                    iView.text = "0${picker.hour} : 0${picker.minute}"
                }
                else {
                    iView.text = "0${picker.hour} : ${picker.minute}"
                }
            }
            else {
                if(10 > picker.minute) {
                    iView.text = "${picker.hour} : 0${picker.minute}"
                }
                else {
                    iView.text = "${picker.hour} : ${picker.minute}"
                }
            }
        }
    }

    /**
     * Called at the start of the application, check if a user is already connected to the app
     * If not, return directly at the beginning the the call.
     * If user is yet connected, redirect him to the homepage activity.
     */
    private fun checkCurrentUser() {
        if(null == FirebaseAuth.getInstance().currentUser) {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }


}