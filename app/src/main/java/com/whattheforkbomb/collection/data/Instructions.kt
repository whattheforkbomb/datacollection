package com.whattheforkbomb.collection.data

import android.os.Parcel
import android.os.Parcelable

data class Instructions(val videoId: Int, val directionId: Int, val instructionText: String, val alignmentGuideTextId: Int, val alignmentGuideImageId: Int) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(videoId)
        parcel.writeInt(directionId)
        parcel.writeString(instructionText)
        parcel.writeInt(alignmentGuideImageId)
        parcel.writeInt(alignmentGuideTextId)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Instructions> {
        override fun createFromParcel(parcel: Parcel): Instructions {
            return Instructions(parcel)
        }

        override fun newArray(size: Int): Array<Instructions?> {
            return arrayOfNulls(size)
        }
    }
}
