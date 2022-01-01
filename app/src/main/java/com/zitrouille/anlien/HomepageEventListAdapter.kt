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

class HomepageEventListAdapter(private val iContext : Activity, private val iArrayList: ArrayList<HomepageEvent>):
    ArrayAdapter<HomepageEvent>(iContext, R.layout.item_homepage_event, iArrayList) {

    private var mEventProfileArrayList: ArrayList<HomepageEventProfile>? = null


    @SuppressLint("ViewHolder", "InflateParams")
    override fun getView(iPosition: Int, iConvertView: View?, iParent: ViewGroup): View {
        val inflater: LayoutInflater = LayoutInflater.from(iContext)
        val view: View = inflater.inflate(R.layout.item_homepage_event, null)

        val sTitle = iArrayList[iPosition].getTitle()

        view.findViewById<TextView>(R.id.title).text = sTitle

        /**
         * Initialize list of profile picture displayed on an event
         */
        val nbElement = Random().nextInt(10)
        mEventProfileArrayList = ArrayList()
        for (ii in 0..nbElement) {
            val eventProfile = HomepageEventProfile()
            if(ii in 4 until nbElement) {
                eventProfile.setRemainingProfile(nbElement-ii)
                mEventProfileArrayList!!.add(eventProfile)
                break
            }
            else {
                mEventProfileArrayList!!.add(eventProfile)
            }
        }

        initFlipAnimation(view)
        view.findViewById<ImageView>(R.id.back_layout).setImageBitmap(generateQRCode(sTitle))

        view.setOnClickListener {
            val intent = Intent(view.context.applicationContext, EventActivity::class.java)
            intent.putExtra("Title", sTitle)
            view.context.startActivity(intent)
        }

        return view
    }

    /**
     * Called to init the flip animation between event information and QR code to share it
     * with other users.
     */
    private fun initFlipAnimation(iView : View) {
        val recyclerView: RecyclerView = iView.findViewById(R.id.eventProfileList)
        val linearLayoutManager = LinearLayoutManager(iView.context)
        linearLayoutManager.orientation = RecyclerView.HORIZONTAL
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = HomepageEventProfileListAdapter(mEventProfileArrayList!!)

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
                }
            }.start()
            true
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