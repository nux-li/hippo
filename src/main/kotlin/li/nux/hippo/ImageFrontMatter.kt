package li.nux.hippo

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

@Serializable
data class ImageFrontMatter(
    val imageId: String,
    val title: String,
    val description: String,
    val credit: String? = null,
    val year: String? = null,
    val captureDate: String? = null,
    val captureDateTime: String? = null,
    val keywords: List<String>,
    val exifDetails: ExposureDetails? = null,
) {
    companion object {
        fun from(imageMetadata: ImageMetadata): ImageFrontMatter {
            val captured= getCapturedDateTime(imageMetadata)
            return ImageFrontMatter(
                imageId = imageMetadata.getReference(),
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
