package com.zitrouille.anlien

import android.R.attr
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import kotlin.collections.ArrayList
import com.google.zxing.WriterException
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import android.util.Log
import android.view.*
import android.widget.*
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams


class HomepageEventListAdapter(private val iContext : Activity, private val iArrayList: ArrayList<HomepageEvent>):
    ArrayAdapter<HomepageEvent>(iContext, R.layout.item_homepage_event, iArrayList) {

    private var mEventProfileArrayList: ArrayList<HomepageEventProfile>? = null
    private var mListView: ArrayList<View> = ArrayList()


    @SuppressLint("ViewHolder", "InflateParams", "SetTextI18n", "NotifyDataSetChanged",
        "SimpleDateFormat", "CutPasteId"
    )
    override fun getView(iPosition: Int, iConvertView: View?, iParent: ViewGroup): View {
        val inflater: LayoutInflater = LayoutInflater.from(iContext)


        val bLightVisu = iArrayList[iPosition].getLightVisu()

        val view = if(!bLightVisu)
            inflater.inflate(R.layout.item_homepage_event, null)
        else
            inflater.inflate(R.layout.item_homepage_event_light, null)

        if(iArrayList[iPosition].getNotification()) {
            view!!.findViewById<ImageView>(R.id.notification).visibility = View.VISIBLE
        }
        if(iArrayList[iPosition].getMessageNotification()) {
            view!!.findViewById<ImageView>(R.id.chat_notification).visibility = View.VISIBLE
        }

        mListView.add(view!!)

        val eventId = iArrayList[iPosition].getEventId()
        FirebaseFirestore.getInstance().collection("events")
            .document(eventId).get().addOnSuccessListener { eventDoc ->
                Log.i("Database request", "Event retrieved in HomepageEventListAdapter::getView - "+eventDoc.id)
                view.findViewById<TextView>(R.id.title).text  = eventDoc["title"].toString().substring(0, 1)
                    .uppercase(Locale.getDefault()) + eventDoc["title"].toString().substring(1)
                    .lowercase(Locale.getDefault())

                view.findViewById<ConstraintLayout>(R.id.chat).visibility = View.VISIBLE

                // Check if the current user is the organizer
                if(eventDoc.getString("organizerId") == MainActivity.applicationCurrentUserId)
                {
                    Glide.with(view.context)
                        .load(R.drawable.organizer)
                        .into(view.findViewById(R.id.presence))
                    view.findViewById<ImageView>(R.id.presence).visibility = View.VISIBLE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        view.findViewById<ImageView>(R.id.presence).tooltipText = context.getString(
                                                    R.string.event_organizer)
                    }
                }

                // Check if the current event can be used with GPS
                if(eventDoc.contains("placeId")) {
                    if(eventDoc["placeId"].toString().isNotBlank() && eventDoc["placeId"].toString().isNotEmpty())
                    {
                        setMarginToChatButton(view.findViewById<ImageView>(R.id.chat))
                        val placeLatitude = eventDoc["latitude"] as Double
                        val placeLongitude = eventDoc["longitude"] as Double
                        val mapButton = view.findViewById<ImageView>(R.id.waze)
                        mapButton.visibility = View.VISIBLE
                        mapButton.setOnClickListener {
                            try {
                                // Launch Waze if it exists
                                val url =
                                    "https://waze.com/ul?q=66%20Acacia%20Avenue&ll=$placeLatitude,$placeLongitude&navigate=yes"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                view.context.startActivity(intent)
                            } catch (ex: ActivityNotFoundException) {
                                try {
                                    // Try to start google map
                                    val gmmIntentUri =
                                        Uri.parse("google.navigation:q=$placeLatitude,$placeLongitude")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    mapIntent.setPackage("com.google.android.apps.maps")
                                    view.context.startActivity(mapIntent)
                                } catch (ex2: ActivityNotFoundException) {
                                    Toast.makeText(
                                        view.context,
                                        "Aucune application GPS n'a pu être lancée",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mapButton.tooltipText = context.getString(R.string.start_travel)
                        }
                    }
                }

                // Retrieve date from milliseconds
                if(!bLightVisu) {
                    val newDate: Date = Calendar.getInstance().time //getting date
                    val formatter =
                        SimpleDateFormat("EEEE d MMMM yyyy") //formating according to my need
                    newDate.time = eventDoc.getLong("date")!!
                    view.findViewById<TextView>(R.id.date).text =
                        formatter.format(newDate).toString().substring(0, 1)
                            .uppercase(Locale.getDefault()) + formatter.format(newDate).toString()
                            .substring(1)
                            .lowercase(Locale.getDefault())
                }

                view.findViewById<TextView>(R.id.hour).text =  eventDoc.getString("hour")!!

                FirebaseFirestore.getInstance().collection("events")
                      .document(eventId).collection("participants").get().addOnSuccessListener { participants ->
                        Log.i("Database request", "Event participant list retrieved in HomepageEventListAdapter::getView")
                        mEventProfileArrayList = null
                        mEventProfileArrayList = ArrayList()
                        mEventProfileArrayList!!.add(HomepageEventProfile(eventDoc["organizerId"].toString(), bLightVisu)) // Organizer
                        if(participants.size() != 0) {
                            for (ii in 0 until participants.size()) {
                                val participant = participants.documents[ii] ?: continue

                                // Check for each participant if it correspond to the current user
                                // Whe you find him, check his presence status to the event
                                // and update the dedicated icon with the right status
                                val participantPresence = participant["status"] as Long
                                if(participant["userId"].toString() == MainActivity.applicationCurrentUserId)
                                {
                                    val presentImageView = view.findViewById<ImageView>(R.id.presence)
                                    if(1L == participantPresence) // Not decided
                                    {
                                        Glide.with(view.context)
                                            .load(R.drawable.question)
                                            .into(presentImageView)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            presentImageView.tooltipText = context.getString(R.string.no_yet_participant)
                                        }
                                    }
                                    else if(2L == participantPresence) // Refuse
                                    {
                                        Glide.with(view.context)
                                            .load(R.drawable.cancel)
                                            .into(presentImageView)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            presentImageView.tooltipText = context.getString(R.string.event_not_particpant)
                                        }
                                    }
                                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        presentImageView.tooltipText = context.getString(R.string.event_participant)
                                    }
                                    presentImageView.visibility = View.VISIBLE
                                }

                                if(participantPresence != 0L) continue

                                val eventProfile = HomepageEventProfile(participant["userId"].toString(), bLightVisu) // User
                                if (mEventProfileArrayList!!.size > 3) {
                                    eventProfile.setRemainingProfile(participants.size() - ii)
                                    mEventProfileArrayList!!.add(eventProfile)
                                    break
                                } else {
                                    mEventProfileArrayList!!.add(eventProfile)
                                }
                            }
                        }

                        var bLock = eventDoc["lock"] as Boolean
                        if(eventDoc["organizerId"].toString() == FirebaseAuth.getInstance().currentUser!!.uid)
                            bLock = false

                        val recyclerView: RecyclerView = view.findViewById(R.id.eventProfileList)
                        val linearLayoutManager = LinearLayoutManager(context)
                        linearLayoutManager.orientation = RecyclerView.HORIZONTAL
                        recyclerView.layoutManager = linearLayoutManager
                        recyclerView.setHasFixedSize(true)
                        recyclerView.adapter = HomepageEventProfileListAdapter(mEventProfileArrayList!!)

                        if(!bLightVisu) {
                            initFlipAnimation(view, bLock)
                            view.findViewById<ImageView>(R.id.back_layout)
                                .setImageBitmap(generateQRCode(eventId))
                        }

                        // Go to the selected event
                        view.setOnClickListener {
                            view.findViewById<ImageView>(R.id.notification).visibility = View.GONE
                            view.findViewById<ImageView>(R.id.chat_notification).visibility = View.GONE
                            val intent = Intent(view.context.applicationContext, EventActivity::class.java)
                            intent.putExtra("eventId", eventId)
                            intent.putExtra("page", "info")
                            intent.putExtra("organizerId", eventDoc["organizerId"].toString())
                            view.context.startActivity(intent)
                        }

                        view.findViewById<ImageView>(R.id.chat_button).setOnClickListener {
                            view.findViewById<ImageView>(R.id.notification).visibility = View.GONE
                            view.findViewById<ImageView>(R.id.chat_notification).visibility = View.GONE
                            val intent = Intent(view.context.applicationContext, EventActivity::class.java)
                            intent.putExtra("eventId", eventId)
                            intent.putExtra("page", "chat")
                            intent.putExtra("organizerId", eventDoc["organizerId"].toString())
                            view.context.startActivity(intent)
                        }

                      }
            }
        return view
    }

    private fun setMarginToChatButton(iView: View) {
        if (iView.layoutParams is MarginLayoutParams) {
            (iView.layoutParams as MarginLayoutParams).setMargins(0, 0, 35, 0)
            iView.requestLayout()
        }
    }

    /**
     * Called to init the flip animation between event information and QR code to share it
     * with other users.
     */
    private fun initFlipAnimation(iView : View, iPrivateEvent: Boolean) {
        if(!iPrivateEvent) {
            iView.setOnLongClickListener {
                iView.findViewById<ConstraintLayout>(R.id.front_layout).animate().apply {
                    duration = 500
                    //rotationYBy(180f)

                    if (0F == iView.findViewById<ImageView>(R.id.back_layout).alpha) {
                        iView.findViewById<TextView>(R.id.title).animate().apply {
                            alpha(0F)
                        }.start()
                        iView.findViewById<TextView>(R.id.presence).animate().apply {
                            alpha(0F)
                        }.start()
                        iView.findViewById<TextView>(R.id.notification).animate().apply {
                            alpha(0F)
                        }.start()
                        iView.findViewById<LinearLayout>(R.id.date_layout).animate().apply {
                            alpha(0F)
                        }.start()
                        iView.findViewById<RecyclerView>(R.id.eventProfileList).animate().apply {
                            alpha(0F)
                        }.start()
                        iView.findViewById<ImageView>(R.id.back_layout).animate().apply {
                            alpha(1F)
                        }.start()

                        // Hide all other view
                        for (view in mListView) {
                            if (view == iView) continue
                            view.alpha = 0.0f
                        }

                    } else {
                        iView.findViewById<TextView>(R.id.title).animate().apply {
                            alpha(1F)
                        }.start()
                        iView.findViewById<TextView>(R.id.presence).animate().apply {
                            alpha(1F)
                        }.start()
                        iView.findViewById<TextView>(R.id.notification).animate().apply {
                            alpha(1F)
                        }.start()
                        iView.findViewById<LinearLayout>(R.id.date_layout).animate().apply {
                            alpha(1F)
                        }.start()
                        iView.findViewById<RecyclerView>(R.id.eventProfileList).animate().apply {
                            alpha(1F)
                        }.start()
                        iView.findViewById<ImageView>(R.id.back_layout).animate().apply {
                            alpha(0F)
                        }.start()

                        // Hide all other view
                        for (view in mListView) {
                            if (view == iView) continue
                            view.alpha = 1f
                        }
                    }
                }.start()
                true
            }
        }
    }

    /**
     * This function is used to generate a bitmap image qr code from
     * the input string
     */
    private fun generateQRCode(iData : String) : Bitmap {
        var bitmap : Bitmap? = null
        val qrgEncoder = QRGEncoder(iData, null, QRGContents.Type.TEXT, 500)
        try {
            bitmap = qrgEncoder.encodeAsBitmap()
        } catch (e: WriterException) {
            Log.e("Tag", e.toString())
        }
        return bitmap!!
    }

}