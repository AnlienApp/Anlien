package com.zitrouille.anlien

class EventParticipant(iParticipantDoc: String, iUserId: String, iStatus: Long, iEventId: String, iEventName: String, ibMoreMenu: Boolean, iRole: Long) {

    private var mParticipantDoc: String = iParticipantDoc
    private var mUserId: String = iUserId
    private var mEventId: String = iEventId
    private var mEventName: String = iEventName
    private var mStatus: Long = iStatus
    private var mMoreMenu: Boolean = ibMoreMenu
    private var mRole: Long = iRole

    fun getParticipantDoc() : String{
        return mParticipantDoc
    }
    fun getUserId() : String{
        return mUserId
    }
    fun getEventId() : String{
        return mEventId
    }
    fun getEventName() : String{
        return mEventName
    }
    fun getStatus() : Long{
        return mStatus
    }
    fun getMoreMenu() : Boolean{
        return mMoreMenu
    }
    fun getRole() : Long{
        return mRole
    }

}