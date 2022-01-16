package com.zitrouille.anlien

class HomepageEvent(iEventId: String, iNotification : Boolean, iMessageNotification : Boolean, iDate: Long, ibLightVisu: Boolean) {
    private var mEventId: String = iEventId
    private var mNotification: Boolean = iNotification
    private var mMessageNotification: Boolean = iMessageNotification
    private var mDate: Long = iDate
    private var mLightVisu: Boolean = ibLightVisu

    fun getEventId() : String{
        return mEventId
    }

    fun getNotification(): Boolean {
        return mNotification
    }
    fun getMessageNotification() : Boolean {
        return mMessageNotification
    }

    fun getDate() : Long {
        return mDate
    }
    fun getLightVisu() : Boolean {
        return mLightVisu
    }
}