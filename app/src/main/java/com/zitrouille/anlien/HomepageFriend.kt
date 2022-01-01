package com.zitrouille.anlien

class HomepageFriend(iName: String, iPendingRequest: Boolean,
                     iShouldBeValid: Boolean, iFriendId: String) {
    private var mName: String = iName
    private var mRequest: Boolean = iPendingRequest
    private var mShouldBeValid: Boolean = iShouldBeValid
    private var mFriendId: String = iFriendId

    fun getName() : String{
        return mName
    }

    fun getRequest() : Boolean{
        return mRequest
    }
    fun getShouldBeValid() : Boolean{
        return mShouldBeValid
    }
    fun getFriendId() : String{
        return mFriendId
    }

}