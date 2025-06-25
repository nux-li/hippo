package li.nux.hippo.model

import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Objects
import kotlinx.serialization.Serializable
import li.nux.hippo.helpers.prettyJson

data class ImageMetadata(
    var id: Int? = null,
    val path: String,
    val album: String,
    val filename: String,
    val title: String? = null,
    val description: String? = null,
    val credit: String? = null,
    val year: List<String> = emptyList(),
    val captureDate: String? = null,
    val captureTime: String? = null,
    val keywords: List<String> = emptyList(),
    val exposureDetails: ExposureDetails? = null,
    val created: LocalDateTime? = null,
    val updated: LocalDateTime? = null,
    val stockImageSite: List<String> = emptyList(),
    val extra: Map<String, String> = emptyMap(),
) {
    fun getReference() = IMG_NAME_PREFIX + getDocumentId()

    fun getAlbumId(): String {
        return album.hashCode().toString(RADIX).encodeNegIndicator()
    }

    fun getDocumentId(): String {
        return getAlbumId() + "_" + filename.hashCode().toString(RADIX).encodeNegIndicator()
    }

    fun getAlbumAndFilename(): String {
        return "$album/$filename"
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
            exposureDetails,
            stockImageSite,
            extra,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageMetadata
        return path == other.path &&
            album == other.album &&
            filename == other.filename &&
            title == other.title &&
            description == other.description &&
            credit == other.credit &&
            captureDate == other.captureDate &&
            captureTime == other.captureTime &&
            keywords == other.keywords &&
            exposureDetails == other.exposureDetails &&
            stockImageSite == other.stockImageSite &&
            extra == other.extra
    }

    companion object {
        const val RADIX = 35
        const val IMG_NAME_PREFIX = "fo2_"
        private val df: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        fun fromResultSet(resultSet: ResultSet) = ImageMetadata(
            id = resultSet.getInt("id"),
            path = resultSet.getString("path"),
            album = resultSet.getString("album_name"),
            filename = resultSet.getString("photo_filename"),
            title = resultSet.getString("title"),
            description = resultSet.getString("description"),
            credit = resultSet.getString("credit"),
            year = getYearFrom(resultSet.getString("capture_date"))?.let { listOf(it) } ?: emptyList(),
            captureDate = resultSet.getString("capture_date"),
            captureTime = resultSet.getString("capture_time"),
            keywords = resultSet.getString("keywords").split(", ", "; ", ",", ";").distinct(),
            exposureDetails = ExposureDetails(
                focalLength = resultSet.getString("focal_length"),
                aperture = resultSet.getString("f_number"),
                exposureTime = resultSet.getString("exposure_time"),
                iso = resultSet.getString("iso"),
                cameraMake = resultSet.getString("camera_make"),
                cameraModel = resultSet.getString("camera_model"),
            ),
            stockImageSite = getStockImageSiteFrom(resultSet),
            extra = resultSet.getString("extra_fields").let { prettyJson.decodeFromString(it) },
            created = resultSet.getTimestamp("created").toLocalDateTime(),
            updated = resultSet.getTimestamp("updated").toLocalDateTime(),
        )

        private fun getStockImageSiteFrom(resultSet: ResultSet): List<String> {
            val sis = resultSet.getString("stock_image_site")
            return if (resultSet.wasNull()) emptyList() else listOf(sis)
        }

        private fun getYearFrom(capturedDate: String?): String? {
            return capturedDate?.let {
                LocalDate.parse(it, df ).year.toString()
            }
        }
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
