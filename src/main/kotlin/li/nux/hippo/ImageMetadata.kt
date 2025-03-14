package li.nux.hippo

import java.time.LocalDateTime
import java.util.Objects
import kotlinx.serialization.Serializable

data class ImageMetadata(
    var id: Int? = null,
    val path: String,
    val album: String,
    val filename: String,
    val title: String? = null,
    val description: String? = null,
    val credit: String? = null,
    val captureDate: String? = null,
    val captureTime: String? = null,
    val keywords: List<String> = emptyList(),
    val exposureDetails: ExposureDetails? = null,
    val created: LocalDateTime? = null,
    val updated: LocalDateTime? = null,
) {
    fun getReference() = IMG_NAME_PREFIX + getDocumentId()

    fun getAlbumId(): String {
        return album.hashCode().toString(RADIX).encodeNegIndicator()
    }

    fun getDocumentId(): String {
        return getAlbumId() + "_" + filename.hashCode().toString(RADIX).encodeNegIndicator()
    }

    fun getAlbumAndFilename(): String {
        return album + "/" + filename
    }

    override fun hashCode(): Int {
        return Objects.hash(
            path,
            album,
            filename,
            title,
            description,
            credit,
            captureDate,
            captureTime,
            keywords,
            exposureDetails
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageMetadata

        if (path != other.path) return false
        if (album != other.album) return false
        if (filename != other.filename) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (credit != other.credit) return false
        if (captureDate != other.captureDate) return false
        if (captureTime != other.captureTime) return false
        if (keywords != other.keywords) return false
        if (exposureDetails != other.exposureDetails) return false

        return true
    }

    companion object {
        const val RADIX = 35
        const val IMG_NAME_PREFIX = "PIC_"
    }
}

@Serializable
data class ExposureDetails(
    val focalLength: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
) {
    fun ifAnyData(): ExposureDetails? {
        val nonNulls = listOfNotNull(focalLength, aperture, exposureTime, iso, cameraMake, cameraModel)
        return if (nonNulls.isNotEmpty()) this else null
    }
}

fun String.encodeNegIndicator() = this.replace("-", "z")
