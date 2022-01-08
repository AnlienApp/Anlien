package com.zitrouille.anlien

class HomepageFriend(iName: String, iIdentifiant: String, iPendingRequest: Boolean,
                     iShouldBeValid: Boolean, iFriendId: String,
                     ibFindFromSearch: Boolean) {

    private var mName: String = iName
    private var mIdentifiant: String = iIdentifiant
    private var mRequest: Boolean = iPendingRequest
    private var mShouldBeValid: Boolean = iShouldBeValid
    private var mFriendId: String = iFriendId
    private var mFindFromSearch: Boolean = ibFindFromSearch
    private var mAssociatedToFriendRequest: Boolean = false

    fun getName() : String{
        return mName
    }
    fun getIdentifiant() : String{
        return mIdentifiant
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
    fun getFriendFromSearch() : Boolean{
        return mFindFromSearch
    }
    fun getAssociatedToFriendRequest() : Boolean{
        return mAssociatedToFriendRequest
    }


    fun setRequest(iValue: Boolean) {
        mRequest = iValue
    }
    fun setShouldBeValid(iValue: Boolean) {
        mShouldBeValid = iValue
    }
    fun setAssociatedToFriendRequest(iValue: Boolean) {
        mAssociatedToFriendRequest = iValue
    }

}