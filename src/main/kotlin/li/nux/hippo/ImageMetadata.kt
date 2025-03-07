package li.nux.hippo

import java.time.LocalDateTime
import java.util.Objects

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
    fun getReference() = "PIC_" + getDocumentId()

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

    companion object {
        const val RADIX = 35
    }
}

data class ExposureDetails(
    val focalLength: String? = null,
    val fStop: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
)

fun String.encodeNegIndicator() = this.replace("-", "z")