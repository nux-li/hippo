package li.nux.hippo.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class ImageFrontMatter(
    val imageId: String,
    val controlCode: String,
    val title: String,
    val description: String,
    val credit: String? = null,
    val year: String? = null,
    val captureDate: String? = null,
    val captureDateTime: String? = null,
    val keywords: List<String>,
    val exifDetails: ExposureDetails? = null,
) {

    fun toImageMetadata(): ImageMetadata {
        val (path, album, filename) = String(Base64.getDecoder().decode(controlCode)).split(",")
        val localDateTime = captureDateTime?.let { DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(it) }
        return ImageMetadata(
            path = path,
            album = album,
            filename = filename,
            title = title,
            description = description,
            credit = credit,
            captureDate = localDateTime?.let { DateTimeFormatter.ofPattern("yyyyMMdd").format(it) },
            captureTime = localDateTime?.let { DateTimeFormatter.ofPattern("HHmmss").format(it) },
            keywords = keywords,
            exposureDetails = exifDetails,
        )
    }

    companion object {
        fun from(imageMetadata: ImageMetadata): ImageFrontMatter {
            val captured= getCapturedDateTime(imageMetadata)
            return ImageFrontMatter(
                imageId = imageMetadata.getReference(),
                controlCode = listOf(
                    imageMetadata.path,
                    imageMetadata.album,
                    imageMetadata.filename,
                ).joinToString(",").let { Base64.getEncoder().encodeToString(it.encodeToByteArray()) },
                title = imageMetadata.title ?: "Insert title here",
                description = imageMetadata.description ?: "Insert description here",
                credit = imageMetadata.credit,
                year = captured?.year?.toString(),
                captureDate = captured?.toLocalDate()?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                captureDateTime = captured?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                keywords = imageMetadata.keywords,
                exifDetails = imageMetadata.exposureDetails?.ifAnyData()
            )
        }

        private fun getCapturedDateTime(
            imageMetadata: ImageMetadata,
        ): LocalDateTime? {
            val dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            return if (imageMetadata.captureDate != null && imageMetadata.captureTime != null) {
                 LocalDateTime.parse(
                     imageMetadata.let { it.captureDate + it.captureTime!!.split("-", "+", " ").first() },
                     dtf
                 )
            } else {
                null
            }
        }
    }
}
