package com.zitrouille.anlien

class EventShopping(iEventId: String, iItemId: String, iName: String, iOwner: String, iCreatorId: String, iMoreMenu: Boolean) {

    private var mName: String = iName
    private var mId: String = iItemId
    private var mEventId: String = iEventId
    private var mOwner: String = iOwner
    private var mCreatorId: String = iCreatorId
    private var mMoreMenu: Boolean = iMoreMenu

    fun getName() : String{
        return mName
    }
    fun getId() : String{
        return mId
    }
    fun getEventId() : String{
        return mEventId
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