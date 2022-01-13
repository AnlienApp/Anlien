package com.zitrouille.anlien

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.android.volley.AuthFailureError
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zitrouille.anlien.MainActivity.Companion.updateCurrentUserMessagingToken
import org.json.JSONException
import org.json.JSONObject
import android.app.ActivityManager

class FirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        remoteMessage.notification?.let {
            val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val runningTaskInfo = manager.getRunningTasks(1)
            val componentInfo = runningTaskInfo[0].topActivity
            if(componentInfo!!.className != "com.zitrouille.anlien.EventActivity") {
                sendVisualNotification(remoteMessage.notification)
            }
        }

    }

    override fun onNewToken(token: String) {
        updateCurrentUserMessagingToken(token)
    }


    @SuppressLint("UnspecifiedImmutableFlag", "ResourceAsColor")
    private fun sendVisualNotification(notification: RemoteMessage.Notification?) {

        // Create an Intent that will be shown when user will click on the Notification
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)

        // Create a Channel (Android 8)
        val channelId = "default_notification_channel_id"

        // Build a Notification object
        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(notification!!.title)
            .setContentText(notification.body)
            .setSmallIcon(R.drawable.ic_baseline_notifications_24)
            .setColor(ContextCompat.getColor(applicationContext, R.color.notificationColor))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDefaults(Notification.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Support Version >= Android 8
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName: CharSequence = "Firebase Messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val mChannel = NotificationChannel(channelId, channelName, importance)
            notificationManager.createNotificationChannel(mChannel)
        }

        // Show notification
        notificationManager.notify(0, notificationBuilder.build())
    }
}

/**
 * This class is used to send a notification to another user of multiple users
 */
class FirebaseNotificationSender(iUserFcmToken: String, iTile: String, iBody: String, iActivity: Activity) {

    private val mUserFcmToken = iUserFcmToken
    private val mTitle = iTile
    private val mBody = iBody
    private val mActivity = iActivity

    private var mRequestQueue: RequestQueue? = null
    private val mPostUrl = "https://fcm.googleapis.com/fcm/send"
    private val mServerKey = "AAAArJnIaRs:APA91bFzKLmvQjgmYoKS2PSyETRS-u_SKIEBdx5BvWyLD3gq0OEt2hKQsnrACrC4JhVpc0qLydg8eCxcJJe5aLh7cJtk1RlCiVZEsvdTK4WsI6LQhYhNaNhbpZ1vb92xceaMfZgbCQc9"

    /**
     * Notification will be send to the desired user(s)
     */
    fun sendNotification() {
        mRequestQueue = Volley.newRequestQueue(mActivity)
        val mainObject = JSONObject()
        try  {
            mainObject.put("to", mUserFcmToken)
            val notificationObject = JSONObject()
            notificationObject.put("title", mTitle)
            notificationObject.put("body", mBody)
            notificationObject.put("icon", R.drawable.checked) // Change to set the right icon when needed
            mainObject.put("notification", notificationObject)

            val accessTokenRequest: JsonObjectRequest = object : JsonObjectRequest(
                Method.POST, mPostUrl, mainObject,
                Response.Listener { response ->
                    val str = response.toString()
                    Log.d("TAG","response: $str")
                }, Response.ErrorListener { error ->
                    Log.d("TAG", "error response: ${error.message}")
                }) {
                @Throws(AuthFailureError::class)
                override fun getHeaders(): Map<String, String> {
                    val params = HashMap<String, String>()
                    params["content-type"] = "application/json"
                    params["authorization"] = "key=$mServerKey"
                    return params
                }
            }
            mRequestQueue!!.add(accessTokenRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }



}