package com.whattheforkbomb.collection.data

data class Resolution(val width: Int, val height: Int)

data class InputImageData(
    val frontFacingImage: ByteArray,
    val frontFacingResolution: Resolution,
    val rearFacingImage: ByteArray,
    val rearFacingResolution: Resolution
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InputImageData

        if (!frontFacingImage.contentEquals(other.frontFacingImage)) return false
        if (frontFacingResolution != other.frontFacingResolution) return false
        if (!rearFacingImage.contentEquals(other.rearFacingImage)) return false
        if (rearFacingResolution != other.rearFacingResolution) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frontFacingImage.contentHashCode()
        result = 31 * result + frontFacingResolution.hashCode()
        result = 31 * result + rearFacingImage.contentHashCode()
        result = 31 * result + rearFacingResolution.hashCode()
        return result
    }
}
