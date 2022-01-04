package com.zitrouille.anlien

class HomepageEventProfile(iUserId: String){
    private var mRemainingProfile = 0
    private var mUserId = iUserId

    fun getRemainingProfile() : Int {
        return mRemainingProfile
    }
    fun getUserId() : String {
        return mUserId
    }
    fun setRemainingProfile(iRemainingProfile : Int) {
        mRemainingProfile = iRemainingProfile
    }
}