package com.zitrouille.anlien

class HomepageEvent(iEventId: String) {
    private var mEventId: String = iEventId

    fun getEventId() : String{
        return mEventId
    }
}