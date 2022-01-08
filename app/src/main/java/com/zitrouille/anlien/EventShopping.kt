package com.zitrouille.anlien

class EventShopping(iEventId: String, iEventName: String, iEventOrganizerId: String, iItemId: String, iName: String, iOwner: String, iCreatorId: String, iMoreMenu: Boolean) {

    private var mName: String = iName
    private var mEventName: String = iEventName
    private var mId: String = iItemId
    private var mEventId: String = iEventId
    private var mEventOrganizerId: String = iEventOrganizerId
    private var mOwner: String = iOwner
    private var mCreatorId: String = iCreatorId
    private var mMoreMenu: Boolean = iMoreMenu

    fun getName() : String{
        return mName
    }
    fun getId() : String{
        return mId
    }
    fun getEventName() : String{
        return mEventName
    }
    fun getEventId() : String{
        return mEventId
    }
    fun getEventOrganizerId() : String{
        return mEventOrganizerId
    }
    fun getCreatorId() : String{
        return mCreatorId
    }
    fun getOwner() : String{
        return mOwner
    }
    fun setOwner(iValue: String){
        mOwner = iValue
    }

    fun getMoreMenu() : Boolean{
        return mMoreMenu
    }

}