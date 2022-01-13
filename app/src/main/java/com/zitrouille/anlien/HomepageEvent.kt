package com.zitrouille.anlien

class HomepageEvent(iEventId: String, iNotification : Boolean, iDate: Long) {
    private var mEventId: String = iEventId
    private var mNotification: Boolean = iNotification
    private var mDate: Long = iDate

    fun getEventId() : String{
        return mEventId
    }

    fun getNotification(): Boolean {
        return mNotification
    }

    fun getDate() : Long {
        return mDate
    }
}