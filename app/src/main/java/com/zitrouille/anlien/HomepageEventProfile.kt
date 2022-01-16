package com.zitrouille.anlien

class HomepageEventProfile(iUserId: String, ibLightVisu: Boolean){
    private var mRemainingProfile = 0
    private var mUserId = iUserId
    private var ibLightVisu = ibLightVisu

    fun getRemainingProfile() : Int {
        return mRemainingProfile
    }
    fun getUserId() : String {
        return mUserId
    }
    fun getLightVisu() : Boolean {
        return ibLightVisu
    }
    fun setRemainingProfile(iRemainingProfile : Int) {
        mRemainingProfile = iRemainingProfile
    }
}