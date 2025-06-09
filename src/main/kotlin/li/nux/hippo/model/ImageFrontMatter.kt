package li.nux.hippo.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageFrontMatter(
    @kotlinx.serialization.Transient
    val imageId: String = "",
    val controlCode: String,
    @SerialName("imageTitle")
    val title: String,
    val description: String,
    val credit: String? = null,
    val equipment: List<String>,
    val year: String? = null,
    val captureDate: String? = null,
    val captureDateTime: String? = null,
    var imagePaths: GalleryImage? = null,
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
                equipment = imageMetadata.exposureDetails
                    ?.let { listOfNotNull(it.cameraMake, it.cameraModel) } ?: emptyList(),
                year = captured?.year?.toString(),
                captureDate = captured?.toLocalDate()?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                captureDateTime = captured?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                imagePaths = GalleryImage.from(imageMetadata.path, imageMetadata.getReference()),
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

@Serializable
data class AllImages(
    val images: Map<String, ImageFrontMatter>
)

@Serializable
data class AllKeywords(
    val keyword: Map<String, KeywordItem>
)

@Serializable
data class KeywordItem(
    val keyword: String,
    val count: Int,
    val weight: Int = 0,
)
