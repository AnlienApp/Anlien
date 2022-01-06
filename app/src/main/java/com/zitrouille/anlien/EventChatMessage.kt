package com.zitrouille.anlien

class EventChatMessage(iUserId: String, iMessage: String, iDateInMilli: Long) {

    private var mUserId: String = iUserId
    private var mMessage: String = iMessage
    private var mDateInMilli: Long = iDateInMilli

    fun getUserId() : String{
        return mUserId
    }
    fun getMessage() : String{
        return mMessage
    }
    fun getDateInMilli() : Long{
        return mDateInMilli
    }

}