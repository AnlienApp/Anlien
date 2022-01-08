package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baoyz.widget.PullRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.zitrouille.anlien.MainActivity.Companion.userCacheInformation
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import android.widget.LinearLayout

class EventActivity : AppCompatActivity() {

    private var mEventId: String = ""

    private var mPresence: Boolean = false
    private var mStatus: Number = 1
    private var bOrganizer: Boolean = false

    private var mCurrentDateInMillis = 0L
    private var mStartDateInMillis = 0L

    private var mEventTitle = ""
    private var mPlaceId = ""
    private var mPlaceLatitude = 0.0
    private var mPlaceLongitude = 0.0
    private var mLock = false

    private var mOrganizerNotificationToken = ""

    private var mCurrentUserId: String? = ""
    private var mDatabase: FirebaseFirestore? = null

    private var mMessageListener: ListenerRegistration? = null
    private var mMessageArrayList: ArrayList<EventChatMessage>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)

        mCurrentUserId = FirebaseAuth.getInstance().currentUser!!.uid
        mDatabase = FirebaseFirestore.getInstance()
        mEventId = intent.extras!!["eventId"].toString()

        if(mCurrentUserId == intent.extras!!["organizerId"].toString()) {
            mPresence = true
            bOrganizer = true
        }

        findViewById<EditText>(R.id.message_input).doOnTextChanged { _, _, _, _ ->
            val recyclerView: RecyclerView = findViewById(R.id.message_list)
            if(null != mMessageArrayList && 0 != mMessageArrayList!!.size)
                recyclerView.smoothScrollToPosition(mMessageArrayList!!.size-1)
        }

        findViewById<ImageView>(R.id.back).setOnClickListener {
            if(View.VISIBLE ==  findViewById<RelativeLayout>(R.id.chatPage).visibility) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(findViewById<EditText>(R.id.message_input).windowToken, 0)

                val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
                val informationView: View = bottomMenu.findViewById(R.id.nav_info)
                informationView.performClick()
            }
            else {
                finish()
            }
        }

        FirebaseFirestore.getInstance()
            .collection("events")
            .document(mEventId)
            .collection("participants")
            .whereEqualTo("userId", mCurrentUserId!!).get().addOnSuccessListener { docs ->
                for(doc in docs) {
                    if(null == doc) {
                        continue
                    }
                    mStatus = doc.get("status") as Number
                    if(0L == mStatus) {
                        mPresence = true
                    }
                    initializeBottomNavigation()
                }
            }
        initializeBottomNavigation()
        initializeSwipeRefresh()
        initMapIcon()
    }

    /**
     * At the beginning of the activity, check if user is well connected.
     */
    override fun onStart() {
        super.onStart()
        checkCurrentUser()
    }

    override fun onPause() {
        super.onPause()
        if(null != mMessageListener) {
            mMessageListener!!.remove()
            mMessageListener = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(null != mMessageListener) {
            mMessageListener!!.remove()
            mMessageListener = null
        }
    }

    override fun onResume() {
        super.onResume()
        checkCurrentUser()
        initChat()
        initChatSnapshotListener()
    }

    /**
     * At the right position of the adress, creator of the event can click on the icon
     * to modify the adresse with google adress.
     * For participant, this button has a different icon and open google map to create travel
     * If the adresse was not define, with the google place API, the button is not available for
     * participant.
     */
    private fun initMapIcon() {
        val mapButton = findViewById<ImageView>(R.id.go_to_map)
        val addressButton = findViewById<ImageView>(R.id.address_google)
        if(bOrganizer) {
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
            addressButton.setOnClickListener {
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
        else {
            addressButton.setOnClickListener {
                Toast.makeText(applicationContext, getString(R.string.address_modification_forbiden), Toast.LENGTH_SHORT).show()
            }
        }

        mapButton.setOnClickListener {
            if(mPlaceId.isNotBlank() && mPlaceId.isNotEmpty()) {
                try {
                    // Launch Waze if it exists
                    val url = "https://waze.com/ul?q=66%20Acacia%20Avenue&ll=$mPlaceLatitude,$mPlaceLongitude&navigate=yes"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (ex: ActivityNotFoundException) {
                    try {
                        // Try to start google map
                        val gmmIntentUri = Uri.parse("google.navigation:q=$mPlaceLatitude,$mPlaceLongitude")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        startActivity(mapIntent)
                    } catch (ex2: ActivityNotFoundException) {
                        Toast.makeText(applicationContext, "Aucune application GPS n'a pu être lancée", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else {
                Toast.makeText(
                    applicationContext,
                    "La géolocalisation n'est pas disponible pour cet évènement",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }
    }

    /**
     * Used to initialized swipe refresh callback for friend list and event list.
     */
    private fun initializeSwipeRefresh() {
        val listSwipe: PullRefreshLayout = findViewById(R.id.swipeRefreshListParticipant)
        listSwipe.setOnRefreshListener {
            retrieveParticpantList()
            listSwipe.setRefreshing(false)
        }

        val shoppingListSwipe: PullRefreshLayout = findViewById(R.id.swipeRefreshListShopping)
        shoppingListSwipe.setOnRefreshListener {
            retrieveShoppingList()
            shoppingListSwipe.setRefreshing(false)
        }
    }

    private fun initializeBottomNavigation() {
        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomMenu.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_info -> {
                    displayInformationPage()
                }
                R.id.nav_participant -> {
                    if(mPresence)
                        displayParticipantPage()
                    else
                        Toast.makeText(applicationContext, "Vous ne participez pas (encore) à l'évènement", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_shopping -> {
                    if(mPresence)
                        displayShoppingPage()
                    else
                        Toast.makeText(applicationContext, "Vous ne participez pas (encore) à l'évènement", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_pot -> {
                    if(mPresence)
                        displayPotPage()
                    else
                        Toast.makeText(applicationContext, "Vous ne participez pas (encore) à l'évènement", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_chat -> {
                    if(mPresence)
                        displayChatPage()
                    else
                        Toast.makeText(applicationContext, "Vous ne participez pas (encore) à l'évènement", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        val informationView: View = bottomMenu.findViewById(R.id.nav_info)
        informationView.performClick()
    }

    private fun displayInformationPage() {
        val addButton = findViewById<ImageView>(R.id.add_button)
        addButton.visibility = View.GONE

        val manageButton = findViewById<ImageView>(R.id.manage_participant)
        manageButton.visibility = View.GONE

        val informationLayout = findViewById<RelativeLayout>(R.id.informationPage)
        informationLayout.visibility = View.VISIBLE

        val participantLayout = findViewById<RelativeLayout>(R.id.participantPage)
        participantLayout.visibility = View.GONE

        val shoppingLayout = findViewById<RelativeLayout>(R.id.shoppingPage)
        shoppingLayout.visibility = View.GONE

        val potLayout = findViewById<RelativeLayout>(R.id.potPage)
        potLayout.visibility = View.GONE

        val chatLayout = findViewById<RelativeLayout>(R.id.chatPage)
        chatLayout.visibility = View.GONE

        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomMenu.visibility = View.VISIBLE

        findViewById<TextView>(R.id.my_activity).text = getString(R.string.information)

        if(!bOrganizer) {
            // Create callback
            val presentButton = findViewById<ImageView>(R.id.presentButton)
            val questionButton = findViewById<ImageView>(R.id.questionButton)
            val cancelButton = findViewById<ImageView>(R.id.cancelButton)

            when (mStatus) {
                0L -> {
                    presentButton.alpha = 0.9f
                    questionButton.alpha = 0.3f
                    cancelButton.alpha = 0.3f
                }
                1L -> {
                    presentButton.alpha = 0.3f
                    questionButton.alpha = 0.9f
                    cancelButton.alpha = 0.3f
                }
                2L -> {
                    presentButton.alpha = 0.3f
                    questionButton.alpha = 0.3f
                    cancelButton.alpha = 0.9f
                }
            }

            presentButton.setOnClickListener {
                presentButton.animate().alpha(0.9f).start()
                questionButton.animate().alpha(0.3f).start()
                cancelButton.animate().alpha(0.3f).start()
                updatePresence(0L)
            }
            questionButton.setOnClickListener {
                presentButton.animate().alpha(0.3f).start()
                questionButton.animate().alpha(0.9f).start()
                cancelButton.animate().alpha(0.3f).start()
                updatePresence(1L)
            }
            cancelButton.setOnClickListener {
                presentButton.animate().alpha(0.3f).start()
                questionButton.animate().alpha(0.3f).start()
                cancelButton.animate().alpha(0.9f).start()
                updatePresence(2L)
            }
        }
        retrieveEventInformation()
    }

    private fun updatePresence(iPresence: Long) {
        mStatus = iPresence
        mPresence = false
        if(0L == iPresence) {
            mPresence = true
        }

        val data = hashMapOf(
            "status" to iPresence,
        )
        FirebaseFirestore.getInstance()
            .collection("events")
            .document(mEventId)
            .collection("participants")
            .whereEqualTo("userId", mCurrentUserId).get().addOnSuccessListener { docs ->
                for(doc in docs) {
                    if(null == doc) {
                        continue
                    }
                    FirebaseFirestore.getInstance()
                        .collection("events")
                        .document(mEventId)
                        .collection("participants")
                        .document(doc.id).update(data as Map<String, Any>).addOnSuccessListener {
                            if(0L == iPresence) {
                                Toast.makeText(
                                    applicationContext,
                                    getString(R.string.member_of_event),
                                    Toast.LENGTH_SHORT
                                ).show()
                                val notification =
                                    FirebaseNotificationSender(
                                        mOrganizerNotificationToken,
                                        "Invitation acceptée",
                                        userCacheInformation[mCurrentUserId]!!.displayName + " participe à l'évènement " + mEventTitle,
                                        this
                                    )
                                notification.SendNotification()
                            }
                            else {
                                 Toast.makeText(
                                    applicationContext,
                                    getString(R.string.not_member_of_event),
                                    Toast.LENGTH_SHORT
                                ).show()
                                val notification =
                                    FirebaseNotificationSender(
                                        mOrganizerNotificationToken,
                                        "Invitation refusée",
                                        userCacheInformation[mCurrentUserId]!!.displayName + " ne participe pas à l'évènement " + mEventTitle,
                                        this
                                    )
                                notification.SendNotification()
                            }
                        }
                }
            }
    }

    private fun displayParticipantPage() {
        val addButton = findViewById<ImageView>(R.id.add_button)
        addButton.visibility = View.GONE

        if(bOrganizer) {
            val manageButton = findViewById<ImageView>(R.id.manage_participant)
            manageButton.visibility = View.VISIBLE
            manageButton.setOnClickListener {
                openParticipantSelectionDialog()
            }
        }

        val informationLayout = findViewById<RelativeLayout>(R.id.informationPage)
        informationLayout.visibility = View.GONE

        val participantLayout = findViewById<RelativeLayout>(R.id.participantPage)
        participantLayout.visibility = View.VISIBLE

        val shoppingLayout = findViewById<RelativeLayout>(R.id.shoppingPage)
        shoppingLayout.visibility = View.GONE

        val potLayout = findViewById<RelativeLayout>(R.id.potPage)
        potLayout.visibility = View.GONE

        val chatLayout = findViewById<RelativeLayout>(R.id.chatPage)
        chatLayout.visibility = View.GONE

        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomMenu.visibility = View.VISIBLE

        findViewById<TextView>(R.id.my_activity).text = getString(R.string.participant_title)

        retrieveParticpantList()
    }

    private fun displayShoppingPage() {
        val addButton = findViewById<ImageView>(R.id.add_button)
        addButton.visibility = View.VISIBLE

        val manageButton = findViewById<ImageView>(R.id.manage_participant)
        manageButton.visibility = View.GONE

        val informationLayout = findViewById<RelativeLayout>(R.id.informationPage)
        informationLayout.visibility = View.GONE

        val participantLayout = findViewById<RelativeLayout>(R.id.participantPage)
        participantLayout.visibility = View.GONE

        val shoppingLayout = findViewById<RelativeLayout>(R.id.shoppingPage)
        shoppingLayout.visibility = View.VISIBLE

        val potLayout = findViewById<RelativeLayout>(R.id.potPage)
        potLayout.visibility = View.GONE

        val chatLayout = findViewById<RelativeLayout>(R.id.chatPage)
        chatLayout.visibility = View.GONE

        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomMenu.visibility = View.VISIBLE

        findViewById<TextView>(R.id.my_activity).text = getString(R.string.shopping)

        retrieveShoppingList()
    }

    private fun displayPotPage() {
        val addButton = findViewById<ImageView>(R.id.add_button)
        addButton.visibility = View.GONE

        val manageButton = findViewById<ImageView>(R.id.manage_participant)
        manageButton.visibility = View.GONE

        val informationLayout = findViewById<RelativeLayout>(R.id.informationPage)
        informationLayout.visibility = View.GONE

        val participantLayout = findViewById<RelativeLayout>(R.id.participantPage)
        participantLayout.visibility = View.GONE

        val shoppingLayout = findViewById<RelativeLayout>(R.id.shoppingPage)
        shoppingLayout.visibility = View.GONE

        val potLayout = findViewById<RelativeLayout>(R.id.potPage)
        potLayout.visibility = View.VISIBLE

        val chatLayout = findViewById<RelativeLayout>(R.id.chatPage)
        chatLayout.visibility = View.GONE

        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomMenu.visibility = View.VISIBLE

        findViewById<TextView>(R.id.my_activity).text = getString(R.string.pot)

        retrievePot()
    }

    private fun displayChatPage() {
        val addButton = findViewById<ImageView>(R.id.add_button)
        addButton.visibility = View.GONE

        val manageButton = findViewById<ImageView>(R.id.manage_participant)
        manageButton.visibility = View.GONE

        val informationLayout = findViewById<RelativeLayout>(R.id.informationPage)
        informationLayout.visibility = View.GONE

        val participantLayout = findViewById<RelativeLayout>(R.id.participantPage)
        participantLayout.visibility = View.GONE

        val shoppingLayout = findViewById<RelativeLayout>(R.id.shoppingPage)
        shoppingLayout.visibility = View.GONE

        val potLayout = findViewById<RelativeLayout>(R.id.potPage)
        potLayout.visibility = View.GONE

        val chatLayout = findViewById<RelativeLayout>(R.id.chatPage)
        chatLayout.visibility = View.VISIBLE

        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomMenu.visibility = View.GONE

        findViewById<TextView>(R.id.my_activity).text = getString(R.string.discussion)
    }

    @SuppressLint("SetTextI18n", "CutPasteId", "SimpleDateFormat")
    private fun retrieveEventInformation() {
        FirebaseFirestore.getInstance()
            .collection("events")
            .document(mEventId).get().addOnSuccessListener { doc ->
                if(null != doc) {

                    if(doc.contains("placeId")) {
                        mPlaceId = doc["placeId"].toString()
                        mPlaceLatitude = doc["latitude"] as Double
                        mPlaceLongitude = doc["longitude"] as Double
                    }

                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(intent.extras!!["organizerId"].toString())
                        .get().addOnSuccessListener { organizerData ->
                            mOrganizerNotificationToken = organizerData["notificationToken"].toString()
                        }

                    mEventTitle = doc["title"].toString()
                    findViewById<EditText>(R.id.title).setText(mEventTitle)
                    val newDate: Date = Calendar.getInstance().time //getting date
                    val formatter = SimpleDateFormat("EEEE d MMMM yyyy") //formating according to my need
                    mStartDateInMillis = doc.getLong("date")!!
                    mCurrentDateInMillis = mStartDateInMillis
                    newDate.time = mCurrentDateInMillis
                    findViewById<TextView>(R.id.date).text  = formatter.format(newDate).toString().substring(0, 1)
                        .uppercase(Locale.getDefault()) + formatter.format(newDate).toString().substring(1)
                        .lowercase(Locale.getDefault())
                    findViewById<TextView>(R.id.hour).text = doc["hour"].toString()
                    findViewById<EditText>(R.id.address).setText(doc["address"].toString())
                    findViewById<EditText>(R.id.description).setText(doc["description"].toString())

                    if(!bOrganizer) {
                        findViewById<EditText>(R.id.title).isFocusableInTouchMode = false
                        findViewById<EditText>(R.id.address).isFocusableInTouchMode = false
                        findViewById<EditText>(R.id.description).isFocusableInTouchMode = false

                        findViewById<ConstraintLayout>(R.id.user_menu).visibility = View.VISIBLE
                        findViewById<ConstraintLayout>(R.id.organizer_menu).visibility = View.GONE
                    }
                    else {
                        findViewById<TextView>(R.id.textView3).text = getString(R.string.manage)

                        initDatePicker(findViewById(R.id.date))
                        initHourPicker(findViewById(R.id.hour))

                        findViewById<ConstraintLayout>(R.id.user_menu).visibility = View.GONE
                        findViewById<ConstraintLayout>(R.id.organizer_menu).visibility = View.VISIBLE

                        // Button callback for organizer
                        findViewById<ImageView>(R.id.delete_button).setOnClickListener {
                            deleteEvent()
                        }

                        val database = FirebaseFirestore.getInstance()

                        mLock = doc["lock"] as Boolean
                        if(mLock) {
                            findViewById<ImageView>(R.id.lock_unlock).setImageDrawable(
                                ContextCompat.getDrawable(this, R.drawable.lock)
                            )
                        }
                        else {
                            findViewById<ImageView>(R.id.lock_unlock).setImageDrawable(
                                ContextCompat.getDrawable(this, R.drawable.unlock)
                            )
                        }
                        findViewById<ImageView>(R.id.lock_unlock).setOnClickListener {
                            mLock = !mLock
                            database.collection("events")
                                .document(mEventId)
                                .update("lock", mLock).addOnSuccessListener {
                                    if(mLock) {
                                        findViewById<ImageView>(R.id.lock_unlock).setImageDrawable(
                                            ContextCompat.getDrawable(this, R.drawable.lock)
                                        )
                                        Toast.makeText(applicationContext, getString(R.string.private_event), Toast.LENGTH_SHORT).show()
                                    }
                                    else {
                                        findViewById<ImageView>(R.id.lock_unlock).setImageDrawable(
                                            ContextCompat.getDrawable(this, R.drawable.unlock)
                                        )
                                        Toast.makeText(applicationContext, getString(R.string.public_event), Toast.LENGTH_SHORT).show()
                                    }
                            }

                        }
                        findViewById<ImageView>(R.id.update_button).setOnClickListener {
                            updateEvent()
                        }
                    }
                }
        }
    }

    private fun updateEvent() {
        val database = FirebaseFirestore.getInstance()
        val currentEventData = hashMapOf(
            "title" to findViewById<EditText>(R.id.title).text.toString(),
            "address" to findViewById<EditText>(R.id.address).text.toString(),
            "lock" to mLock,
            "date" to mCurrentDateInMillis,
            "hour" to findViewById<TextView>(R.id.hour).text.toString(),
            "placeId" to mPlaceId,
            "latitude" to mPlaceLatitude,
            "longitude" to mPlaceLongitude,
            "description" to findViewById<EditText>(R.id.description).text.toString(),
            "organizerId" to mCurrentUserId
        )
        database.collection("events").document(mEventId).set(currentEventData).addOnSuccessListener {
            findViewById<ImageView>(R.id.update_button).animate().rotation(findViewById<ImageView>(R.id.update_button).rotation+360.0f).withEndAction {
                Toast.makeText(applicationContext, getString(R.string.event_update_success), Toast.LENGTH_SHORT).show()
            }
            if(mStartDateInMillis != mCurrentDateInMillis) {
                database.collection("events")
                    .document(mEventId)
                    .collection("partipcipants").get()
                    .addOnSuccessListener { docs1 ->
                        for (doc1 in docs1) {
                            if (null == doc1) continue
                            database.collection("users")
                                .document(doc1["userId"].toString())
                                .collection("events")
                                .whereEqualTo("eventId", mEventId).get()
                                .addOnSuccessListener { docs2 ->
                                    for (doc2 in docs2) {
                                        if (null == doc2) continue
                                        database.collection("users")
                                            .document(doc1["userId"].toString())
                                            .collection("events")
                                            .document(doc2.id).update(
                                                "eventDateInMilli",
                                                mCurrentDateInMillis
                                            )
                                    }
                                }
                        }
                    }
            }
        }
    }

    /**
     * TO remake
     */
    private fun deleteEvent() {
        val database = FirebaseFirestore.getInstance()

        // Organizer management
        database.collection("users")
            .document(intent.extras!!["organizerId"].toString())
            .collection("events")
            .whereEqualTo("eventId", mEventId).get().addOnSuccessListener { organizerDocs ->
                for(organizerDoc in organizerDocs) {
                    if(null == organizerDoc) continue
                    database.collection("users")
                        .document(intent.extras!!["organizerId"].toString())
                        .collection("events")
                        .document(organizerDoc.id).delete()
                }
            }

        database.collection("events")
            .document(mEventId)
            .collection("participants")
            .get().addOnSuccessListener { participants ->
                for(participant in participants) {
                    if(null == participant) continue
                    database.collection("users")
                        .document(participant["userId"].toString())
                        .collection("events")
                        .whereEqualTo("eventId", mEventId).get().addOnSuccessListener { userEvents ->
                            for(event in userEvents) {
                                if(null == event) continue

                                database.collection("events")
                                    .document(mEventId)
                                    .collection("participants").document(participant.id).delete()

                                database.collection("users")
                                    .document(participant["userId"].toString())
                                    .collection("events")
                                    .document(event.id).delete().addOnSuccessListener {
                                        if(participant == participants.documents[participants.size()-1] && event == userEvents.documents[userEvents.size()-1]) {
                                            // Delete shopping list of the event
                                            database.collection("events")
                                                .document(mEventId)
                                                .collection("shopping").get().addOnSuccessListener { shoppings ->
                                                    if(shoppings.documents.size != 0) {
                                                        for (shopping in shoppings) {
                                                            shopping.reference.delete()
                                                            if (shopping == shoppings.documents[shoppings.documents.size - 1]) {
                                                                // delete messages of the event
                                                                database.collection("events")
                                                                    .document(mEventId)
                                                                    .collection("messages").get()
                                                                    .addOnSuccessListener { messages ->
                                                                        if (messages.documents.size != 0) {
                                                                            for (message in messages) {
                                                                                message.reference.delete()
                                                                                if (message == messages.documents[messages.documents.size - 1]) {
                                                                                    database.collection("events")
                                                                                        .document(mEventId)
                                                                                        .delete().addOnSuccessListener {
                                                                                            findViewById<ImageView>(R.id.delete_button).animate()
                                                                                                .rotation(
                                                                                                    findViewById<ImageView>(
                                                                                                        R.id.delete_button
                                                                                                    ).rotation + 360.0f
                                                                                                ).withEndAction {
                                                                                                    Toast.makeText(
                                                                                                        applicationContext,
                                                                                                        getString(R.string.event_delete_success),
                                                                                                        Toast.LENGTH_SHORT
                                                                                                    ).show()
                                                                                                    finish()
                                                                                                }
                                                                                        }
                                                                                }
                                                                            }
                                                                        } else {
                                                                            findViewById<ImageView>(R.id.delete_button).animate()
                                                                                .rotation(findViewById<ImageView>(R.id.delete_button).rotation + 360.0f)
                                                                                .withEndAction {
                                                                                    Toast.makeText(
                                                                                        applicationContext,
                                                                                        getString(R.string.event_delete_success),
                                                                                        Toast.LENGTH_SHORT
                                                                                    ).show()
                                                                                    finish()
                                                                                }
                                                                        }
                                                                    }
                                                            }
                                                        }
                                                    }
                                                    else {
                                                        findViewById<ImageView>(R.id.delete_button).animate()
                                                            .rotation(findViewById<ImageView>(R.id.delete_button).rotation + 360.0f)
                                                            .withEndAction {
                                                                Toast.makeText(
                                                                    applicationContext,
                                                                    getString(R.string.event_delete_success),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                                finish()
                                                            }
                                                    }
                                                }
                                        }
                                    }
                            }
                    }
                }
                if(0 == participants.size()) {
                    // Delete shopping list of the event
                    database.collection("events")
                        .document(mEventId)
                        .collection("shopping").get().addOnSuccessListener { shoppings ->
                            if(shoppings.documents.size != 0) {
                                for (shopping in shoppings) {
                                    shopping.reference.delete()
                                    if (shopping == shoppings.documents[shoppings.documents.size - 1]) {
                                        // delete messages of the event
                                        database.collection("events")
                                            .document(mEventId)
                                            .collection("messages").get()
                                            .addOnSuccessListener { messages ->
                                                if (messages.documents.size != 0) {
                                                    for (message in messages) {
                                                        message.reference.delete()
                                                        if (message == messages.documents[messages.documents.size - 1]) {
                                                            database.collection("events")
                                                                .document(mEventId)
                                                                .delete().addOnSuccessListener {
                                                                    findViewById<ImageView>(R.id.delete_button).animate()
                                                                        .rotation(
                                                                            findViewById<ImageView>(
                                                                                R.id.delete_button
                                                                            ).rotation + 360.0f
                                                                        ).withEndAction {
                                                                            Toast.makeText(
                                                                                applicationContext,
                                                                                getString(R.string.event_delete_success),
                                                                                Toast.LENGTH_SHORT
                                                                            ).show()
                                                                            finish()
                                                                        }
                                                                }
                                                        }
                                                    }
                                                } else {
                                                    findViewById<ImageView>(R.id.delete_button).animate()
                                                        .rotation(findViewById<ImageView>(R.id.delete_button).rotation + 360.0f)
                                                        .withEndAction {
                                                            Toast.makeText(
                                                                applicationContext,
                                                                getString(R.string.event_delete_success),
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            finish()
                                                        }
                                                }
                                            }
                                    }
                                }
                            }
                            else {
                                findViewById<ImageView>(R.id.delete_button).animate()
                                    .rotation(findViewById<ImageView>(R.id.delete_button).rotation + 360.0f)
                                    .withEndAction {
                                        Toast.makeText(
                                            applicationContext,
                                            getString(R.string.event_delete_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        finish()
                                    }
                            }
                        }
                }
        }
    }


    /**
     * Set the current date to the date picker
     * and initialize date picker callback
     */
    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    private fun initDatePicker(iView: TextView) {
        iView.setOnClickListener {
            displayDatePicker(iView)
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun displayDatePicker(iView: TextView) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.choose_date))
            .setSelection(mCurrentDateInMillis)
            .setTheme(R.style.MaterialCalendarTheme)
            .build()
        datePicker.show(supportFragmentManager, "datePicker")
        datePicker.addOnPositiveButtonClickListener {
            val newDate: Date = Calendar.getInstance().time //getting date
            val formatter = SimpleDateFormat("EEEE d MMMM yyyy") //formating according to my need
            newDate.time = it
            iView.text = formatter.format(newDate)
            mCurrentDateInMillis = it
        }
    }

    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    private fun initHourPicker(iView: TextView) {
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
                .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                .build()
        picker.show(supportFragmentManager, "timePicker")
        picker.addOnPositiveButtonClickListener {
            validSelectedHour(picker, iView)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun validSelectedHour(iPicker: MaterialTimePicker, iView: TextView) {
        if(10 > iPicker.hour) {
            if(10 > iPicker.minute) {
                iView.text = "0${iPicker.hour} : 0${iPicker.minute}"
            }
            else {
                iView.text = "0${iPicker.hour} : ${iPicker.minute}"
            }
        }
        else {
            if(10 > iPicker.minute) {
                iView.text = "${iPicker.hour} : 0${iPicker.minute}"
            }
            else {
                iView.text = "${iPicker.hour} : ${iPicker.minute}"
            }
        }
    }

    private fun retrievePot() {

        val potLayout = findViewById<ConstraintLayout>(R.id.potEmpty)
        potLayout.visibility = View.VISIBLE

    }

    private fun retrieveShoppingList() {

        FirebaseFirestore.getInstance().collection("events")
            .document(mEventId)
            .collection("shopping").get().addOnSuccessListener { documents ->
            if(0 == documents.size()) {
                val emptyShoppingLayout = findViewById<ConstraintLayout>(R.id.shoppingListEmpty)
                emptyShoppingLayout.visibility = View.VISIBLE
                val shoppingLayout = findViewById<PullRefreshLayout>(R.id.swipeRefreshListShopping)
                shoppingLayout.visibility = View.GONE
                findViewById<ConstraintLayout>(R.id.shoppingListEmpty).setOnClickListener {
                    createAddShoppingDialog()
                }
            }
            else {

                val recyclerView: RecyclerView = findViewById(R.id.shopping_list)
                val linearLayoutManager = LinearLayoutManager(applicationContext)
                linearLayoutManager.orientation = RecyclerView.VERTICAL
                recyclerView.adapter = null
                recyclerView.layoutManager = linearLayoutManager
                recyclerView.setHasFixedSize(true)

                val shoppingList = ArrayList<EventShopping>()

                val emptyShoppingLayout = findViewById<ConstraintLayout>(R.id.shoppingListEmpty)
                emptyShoppingLayout.visibility = View.GONE
                val shoppingLayout = findViewById<PullRefreshLayout>(R.id.swipeRefreshListShopping)
                shoppingLayout.visibility = View.VISIBLE
                for(doc in documents) {
                    if(null == doc) continue
                    shoppingList.add(EventShopping(
                        mEventId,
                        mEventTitle,
                        intent.extras!!["organizerId"].toString(),
                        doc.id,
                        doc["name"].toString(),
                        doc["owner"].toString(),
                        doc["creator"].toString(),
                        bOrganizer,
                    ))
                    if(doc == documents.documents[documents.size()-1]) {
                        recyclerView.adapter = EventShoppingListAdapter(shoppingList)
                    }
                }
            }
        }

        findViewById<ImageView>(R.id.add_button).setOnClickListener {
            createAddShoppingDialog()
        }

    }

    private fun createAddShoppingDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setContentView(R.layout.dialog_event_create_item)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)

        val validButton = dialog.findViewById<ImageView>(R.id.valid)
        val nameEditText = dialog.findViewById<EditText>(R.id.name)
        val takeItImageView = dialog.findViewById<ImageView>(R.id.i_take_it)
        val takeItRoundedImageView = dialog.findViewById<ImageView>(R.id.i_take_it_rounded)
        takeItImageView.setOnClickListener {
            val currentUserId = FirebaseAuth.getInstance().currentUser!!.uid
            if(userCacheInformation.containsKey(currentUserId)) {
                Glide.with(applicationContext).load(userCacheInformation[currentUserId]!!.uri).into(takeItRoundedImageView)
                takeItRoundedImageView.visibility = View.VISIBLE
                takeItRoundedImageView.animate().alpha(1.0f).rotation(takeItRoundedImageView.rotation+360.0f)
                Toast.makeText(dialog.context, getString(R.string.for_me), Toast.LENGTH_SHORT).show()
            }
            else {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId).get().addOnSuccessListener { doc ->
                        if(doc.exists()) {
                            val userCache = MainActivity.Companion.UserInformation()
                            userCache.displayName = doc["displayName"].toString()
                            userCache.identifiant = doc["identifiant"].toString()
                            userCache.notificationToken = doc["notificationToken"].toString()
                            userCacheInformation[currentUserId] = userCache
                            val storageRef: StorageReference = FirebaseStorage.getInstance().reference
                                .child("profileImages")
                                .child("$currentUserId.jpeg")
                            storageRef.downloadUrl.addOnSuccessListener {
                                Glide.with(applicationContext).load(it).into(takeItRoundedImageView)
                                userCache.uri = it
                            }
                        }
                    }
            }
        }

        takeItRoundedImageView.setOnClickListener {
            takeItRoundedImageView.animate().alpha(0.0f).rotation(takeItRoundedImageView.rotation+360.0f).withEndAction {
                takeItRoundedImageView.visibility = View.GONE
                Toast.makeText(dialog.context, getString(R.string.not_for_me), Toast.LENGTH_SHORT).show()
            }
        }

        // Add the current item to the shopping list
        validButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if(name.isNotEmpty() && name.isNotBlank()) {
                var owner = ""
                if (View.VISIBLE == takeItRoundedImageView.visibility) {
                    owner = FirebaseAuth.getInstance().currentUser!!.uid
                }
                val shoppingItemData = hashMapOf(
                    "name" to name,
                    "owner" to owner,
                    "creator" to FirebaseAuth.getInstance().currentUser!!.uid
                )
                mDatabase!!.collection("events")
                    .document(mEventId)
                    .collection("shopping")
                    .add(shoppingItemData).addOnSuccessListener {
                        dialog.dismiss()
                        retrieveShoppingList()
                    }
            }
        }

        val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        nameEditText.postDelayed({
            nameEditText.requestFocus()
            imm.showSoftInput(nameEditText, 0)
        }, 100)
        nameEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        dialog.show()
    }

    private fun retrieveParticpantList() {
        val recyclerView: RecyclerView = findViewById(R.id.participant_list)
        val linearLayoutManager = LinearLayoutManager(applicationContext)
        linearLayoutManager.orientation = RecyclerView.VERTICAL
        recyclerView.adapter = null
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.setHasFixedSize(true)

        val participantList = ArrayList<EventParticipant>()

        FirebaseFirestore.getInstance()
            .collection("events")
            .document(mEventId)
            .collection("participants").get().addOnSuccessListener { documents ->
                for(doc in documents) {
                    if(null == doc) continue
                    participantList.add(EventParticipant(
                        doc["userId"].toString(),
                        doc.getLong("status")!!,
                        mEventId,
                        bOrganizer,
                    ))
                    if(doc == documents.documents[documents.size()-1]) {
                        recyclerView.adapter = EventParticipantListAdapter(participantList)
                    }
                }
                if(0 == documents.size()) {
                    findViewById<ConstraintLayout>(R.id.participantListEmpty).visibility = View.VISIBLE
                    findViewById<PullRefreshLayout>(R.id.swipeRefreshListParticipant).visibility = View.GONE
                    findViewById<ConstraintLayout>(R.id.participantListEmpty).setOnClickListener {
                        openParticipantSelectionDialog()
                    }
                }
                else {
                    findViewById<ConstraintLayout>(R.id.participantListEmpty).visibility = View.GONE
                    findViewById<PullRefreshLayout>(R.id.swipeRefreshListParticipant).visibility = View.VISIBLE
                }

        }

    }


    /**
     * Retrieve 50 last messages from the chat
     * Create callback listener to update the chat when message is send
     */
    private fun initChat() {
        mMessageArrayList = ArrayList()
        val recyclerView: RecyclerView = findViewById(R.id.message_list)
        val linearLayoutManager = LinearLayoutManager(applicationContext)
        linearLayoutManager.orientation = RecyclerView.VERTICAL
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = EventChatMessageListAdapter(mMessageArrayList!!)

        val messageEditText = findViewById<EditText>(R.id.message_input)
        messageEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        messageEditText.maxLines = 6

        val bundle = intent.extras
        if(null != bundle!!.getString("eventId")) {
            mEventId = bundle.getString("eventId")!!

            // Init the behavior when user click on send message
            val sendMessageButton = findViewById<CardView>(R.id.send_message_button)
            sendMessageButton.setOnClickListener {
                if(messageEditText.text.isNotEmpty() && messageEditText.text.isNotBlank()) {
                    val messageData = hashMapOf(
                        "text" to messageEditText.text.toString(),
                        "sender" to mCurrentUserId,
                        "date" to System.currentTimeMillis(),
                    )
                    mDatabase!!.collection("events").document(mEventId).collection("messages")
                        .add(messageData).addOnSuccessListener {
                        messageEditText.text.clear()
                    }
                }
            }
        }
    }

    private fun initChatSnapshotListener() {
        // Init the listener on message list
        mMessageListener = mDatabase!!.collection("events")
            .document(mEventId).collection("messages")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Toast.makeText(this, getString(R.string.chat_listen_failed), Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (value != null) {
                    for(doc in value.documentChanges) {
                        if(DocumentChange.Type.ADDED != doc.type) continue
                        val message = EventChatMessage(doc.document["sender"].toString(),
                            doc.document["text"].toString(),
                            doc.document.getLong("date")!!
                        )
                        addMessageToChat(message)
                    }

                } else {
                    Toast.makeText(this, getString(R.string.chat_data_empty), Toast.LENGTH_SHORT).show()
                }
                return@addSnapshotListener
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addMessageToChat(iMessage: EventChatMessage) {
        mMessageArrayList!!.add(iMessage)
        mMessageArrayList!!.sortBy { it.getDateInMilli() }
        val recyclerView: RecyclerView = findViewById(R.id.message_list)
        recyclerView.adapter!!.notifyDataSetChanged()
        if(null != mMessageArrayList && 0 != mMessageArrayList!!.size)
            recyclerView.smoothScrollToPosition(mMessageArrayList!!.size-1)
    }

    /**
     * This diaolg display all user friend and set them to green if they are already selected
     * When user click on a friend, switch the participation status of the friend
     */
    private fun openParticipantSelectionDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setContentView(R.layout.dialog_create_event_friend_list)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        dialog.findViewById<LinearLayout>(R.id.main_layout).animate().alpha(1.0F).start()

        //Retrieve list of all current user friends
        val recyclerView: RecyclerView = dialog.findViewById(R.id.friend_list)
        val participantList: ArrayList<CreateEventFriend> = ArrayList()
        mDatabase!!.collection("users")
            .document(mCurrentUserId.toString())
            .collection("friends").get().addOnSuccessListener { documents ->
                Log.i("Database request", "Friend list retrieved in EventActivity::openParticipantSelectionDialog")
                val recyclerView1 = findViewById<RecyclerView>(R.id.participant_list)
                var adapter1 : EventParticipantListAdapter? = null
                if(null != recyclerView1.adapter)
                    adapter1 = recyclerView1.adapter as EventParticipantListAdapter
                for(doc in documents) {
                    if(null == doc) continue
                    if(doc["request"] == true) continue
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

        dialog.findViewById<ImageView>(R.id.valid_current_list).setOnClickListener {
            // Retrieve all friends from the list
            var nbEvent = 0
            for (ii in 0 until participantList.size) {
                val participant = participantList[ii]
                val friendStatus = participant.getSelected()
                // We want this friend attached to the current event
                // At first check if the user was already present in the participant list
                FirebaseFirestore.getInstance()
                    .collection("events")
                    .document(mEventId).collection("participants")
                    .whereEqualTo("userId", participant.getUserId()).get().addOnSuccessListener { docs ->
                        Log.i("Database request", "Friend list from participant list retrieved in EventActivity::openParticipantSelectionDialog")
                        if(0 == docs.size() && friendStatus) {
                            // At him to the event
                            val participantData = hashMapOf(
                                    "userId" to participant.getUserId(),
                                    "status" to 1, // 0 cancel, 1 pending, 2 accept
                                )
                                nbEvent++
                                FirebaseFirestore.getInstance()
                                    .collection("events")
                                    .document(mEventId).collection("participants")
                                    .add(participantData).addOnSuccessListener {
                                        val currentEventData = hashMapOf(
                                            "eventId" to mEventId,
                                            "eventDateInMilli" to mCurrentDateInMillis,
                                        )
                                        FirebaseFirestore.getInstance()
                                            .collection("users")
                                            .document(participant.getUserId())
                                            .collection("events")
                                            .add(currentEventData)
                                        nbEvent--
                                        if(0 == nbEvent)
                                            retrieveParticpantList()
                                    }
                        }
                        else if(0 != docs.size() && !friendStatus) {
                            // Remove the friend from the list
                            for(doc in docs) {
                                nbEvent++
                                val docId = doc.id
                                FirebaseFirestore.getInstance()
                                    .collection("events")
                                    .document(mEventId).collection("participants").document(docId).delete().addOnSuccessListener {
                                        FirebaseFirestore.getInstance()
                                            .collection("users")
                                            .document(doc["userId"].toString())
                                            .collection("events").whereEqualTo("eventId", mEventId).get().addOnSuccessListener { docs2 ->
                                                Log.i("Database request", "Friend list retrieved in EventActivity::openParticipantSelectionDialog")
                                                for(doc2 in docs2) {
                                                    if(null == doc2) continue
                                                    FirebaseFirestore.getInstance()
                                                        .collection("users")
                                                        .document(doc["userId"].toString())
                                                        .collection("events")
                                                        .document(doc2.id).delete()
                                                }
                                            }
                                        nbEvent--
                                        if(0 == nbEvent)
                                            retrieveParticpantList()
                                    }
                            }
                        }
                    }
                }
            dialog.dismiss()
            retrieveParticpantList()
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