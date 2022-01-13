package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat

class HomepageEventListAdapter(private val iContext : Activity, private val iArrayList: ArrayList<HomepageEvent>):
    ArrayAdapter<HomepageEvent>(iContext, R.layout.item_homepage_event, iArrayList) {

    private var mEventProfileArrayList: ArrayList<HomepageEventProfile>? = null
    private var mListView: ArrayList<View> = ArrayList()


    @SuppressLint("ViewHolder", "InflateParams", "SetTextI18n", "NotifyDataSetChanged",
        "SimpleDateFormat"
    )
    override fun getView(iPosition: Int, iConvertView: View?, iParent: ViewGroup): View {
        val inflater: LayoutInflater = LayoutInflater.from(iContext)
        val view: View = inflater.inflate(R.layout.item_homepage_event, null)

        if(iArrayList[iPosition].getNotification()) {
            view.findViewById<ImageView>(R.id.notification).visibility = View.VISIBLE
        }

        mListView.add(view)

        val eventId = iArrayList[iPosition].getEventId()
        FirebaseFirestore.getInstance().collection("events")
            .document(eventId).get().addOnSuccessListener { eventDoc ->
                Log.i("Database request", "Event retrieved in HomepageEventListAdapter::getView - "+eventDoc.id)
                view.findViewById<TextView>(R.id.title).text  = eventDoc["title"].toString().substring(0, 1)
                    .uppercase(Locale.getDefault()) + eventDoc["title"].toString().substring(1)
                    .lowercase(Locale.getDefault())

                // Retrieve date from milliseconds
                val newDate: Date = Calendar.getInstance().time //getting date
                val formatter = SimpleDateFormat("EEEE d MMMM yyyy") //formating according to my need
                newDate.time = eventDoc.getLong("date")!!
                view.findViewById<TextView>(R.id.date).text  = formatter.format(newDate).toString().substring(0, 1)
                    .uppercase(Locale.getDefault()) + formatter.format(newDate).toString().substring(1)
                    .lowercase(Locale.getDefault())

                FirebaseFirestore.getInstance().collection("events")
                      .document(eventId).collection("participants").get().addOnSuccessListener { participants ->
                        Log.i("Database request", "Event participant list retrieved in HomepageEventListAdapter::getView")
                        mEventProfileArrayList = null
                        mEventProfileArrayList = ArrayList()
                        mEventProfileArrayList!!.add(HomepageEventProfile(eventDoc["organizerId"].toString())) // Organizer
                        if(participants.size() != 0) {
                            for (ii in 0 until participants.size()) {
                                val participant = participants.documents[ii] ?: continue
                                val eventProfile = HomepageEventProfile(participant["userId"].toString()) // User
                                if (ii in 4 until participants.size()) {
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

                        initFlipAnimation(view, bLock)
                        view.findViewById<ImageView>(R.id.back_layout).setImageBitmap(generateQRCode(eventId))

                        view.setOnClickListener {
                            view.findViewById<ImageView>(R.id.notification).visibility = View.GONE
                            val intent = Intent(view.context.applicationContext, EventActivity::class.java)
                            intent.putExtra("eventId", eventId)
                            intent.putExtra("organizerId", eventDoc["organizerId"].toString())
                            view.context.startActivity(intent)
                        }
                      }
            }
        return view
    }

    /**
     * Called to init the flip animation between event information and QR code to share it
     * with other users.
     */
    private fun initFlipAnimation(iView : View, iPrivateEvent: Boolean) {
        val recyclerView: RecyclerView = iView.findViewById(R.id.eventProfileList)
        val linearLayoutManager = LinearLayoutManager(iView.context)
        linearLayoutManager.orientation = RecyclerView.HORIZONTAL
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = HomepageEventProfileListAdapter(mEventProfileArrayList!!)

        if(!iPrivateEvent) {
            iView.setOnLongClickListener {
                iView.findViewById<ConstraintLayout>(R.id.front_layout).animate().apply {
                    duration = 500
                    //rotationYBy(180f)

                    if (0F == iView.findViewById<ImageView>(R.id.back_layout).alpha) {
                        iView.findViewById<TextView>(R.id.title).animate().apply {
                            alpha(0F)
                        }.start()
                        iView.findViewById<TextView>(R.id.date).animate().apply {
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
                        iView.findViewById<TextView>(R.id.date).animate().apply {
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