package com.zitrouille.anlien

class EventParticipant(iUserId: String, iStatus: Long, iEventId: String, ibMoreMenu: Boolean) {

    private var mUserId: String = iUserId
    private var mEventId: String = iEventId
    private var mStatus: Long = iStatus
    private var mMoreMenu: Boolean = ibMoreMenu

    fun getUserId() : String{
        return mUserId
    }
    fun getEventId() : String{
        return mEventId
    }
    fun getStatus() : Long{
        return mStatus
    }
    fun getMoreMenu() : Boolean{
        return mMoreMenu
    }

}