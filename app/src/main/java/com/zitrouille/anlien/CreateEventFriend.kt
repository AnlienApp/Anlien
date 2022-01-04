package com.zitrouille.anlien

class CreateEventFriend(iUserId: String, iPresent: Boolean) {

    private var mUserId: String = iUserId
    private var mSelected: Boolean = iPresent

    fun getUserId() : String{
        return mUserId
    }
    fun getSelected() : Boolean{
        return mSelected
    }

    fun setSelected(iValue: Boolean){
        mSelected = iValue
    }

}