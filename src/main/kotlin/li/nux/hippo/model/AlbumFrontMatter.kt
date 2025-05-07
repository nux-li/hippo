package li.nux.hippo.model

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import kotlinx.serialization.Serializable

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Serializable
data class Album(
    val pageType: String = "album",
    val albumId: String,
    val controlCode: String,
    var title: String = "",
    var description: String = "",
    var coverImage: GalleryImage? = null,
    var subAlbums: List<SubAlbum> = emptyList(),
    val images: List<AlbumImage> = emptyList(),
//    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: String = formatter.format(LocalDateTime.now()),
) {
    companion object {
        fun from(path: String, images: List<ImageMetadata>): Album {
            return Album(
                albumId = images.first().getAlbumId(),
                controlCode = listOf(
                    path,
                    images.first().album,
                ).joinToString(",").let { Base64.getEncoder().encodeToString(it.encodeToByteArray()) },
                title = capitalize(images.first().album),
                description = "Insert album description here",
                coverImage = GalleryImage.from(path, images.first().getReference()),
                subAlbums = emptyList(),
                images = images.map { AlbumImage.from(it) },
                lastUpdated = formatter.format(
                    images.map { it.created }.sortedByDescending { it }.first() ?: LocalDateTime.now()
                )
            )
        }

        fun rootFolder(contentDirectory: String): Album {
            return Album(
                pageType = "albumIndex",
                albumId = "ROOT",
                controlCode = listOf(
                    contentDirectory,
                    "/",
                ).joinToString(",").let { Base64.getEncoder().encodeToString(it.encodeToByteArray()) },
                title = "Albums",
                description = "Insert description here",
                coverImage = null,
                subAlbums = emptyList(),
                images = emptyList(),
            )
        }

        private fun capitalize(name: String) =
            name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                .replace('_', ' ')
    }
}

@Serializable
data class SubAlbum(
    val albumId: String,
    val title: String,
    val coverImage: GalleryImage? = null,
    val imageCount: Int? = null,
    val path: String,
//    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: String = formatter.format(LocalDateTime.now()),
) {
    companion object {
        fun from(album: Album, realPath: String): SubAlbum {
            return SubAlbum(
                albumId = album.albumId,
                title = album.title,
                coverImage = album.coverImage,
                imageCount = album.images.size.plus (album.subAlbums.sumOf { it.imageCount ?: 0}),
                path = (realPath.lowercase().split("/content").last() + File.separator + album.title.lowercase())
                    .replace(' ', '_'),
                lastUpdated = album.images
                    .map { it.lastUpdated }
                    .maxByOrNull { it }
                    ?: formatter.format(LocalDateTime.now())
            )
        }
    }
}

@Serializable
data class AlbumImage(
    val imageId: String,
    val imagePaths: GalleryImage,
    val imageTitle: String,
//    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: String = formatter.format(LocalDateTime.now())
) {
    companion object {
        fun from(image: ImageMetadata): AlbumImage {
            return AlbumImage(
                imageId = image.getReference(),
                imagePaths = GalleryImage.from(
                    image.path,
                    image.getReference(),
                ),
                imageTitle = image.title ?: "Image ${image.getReference()}",
                lastUpdated = formatter.format(image.updated ?: LocalDateTime.now()),
            )
        }
    }
}

@Serializable
data class GalleryImage(
    @kotlinx.serialization.Transient
    val albumPath: String = "",
    val imageId: String,
    val thumbnail: String,
    val smallImage: String,
    val featuredImage: String,
    val largeImage: String,
    val originalSize: String,
) {
    companion object {
        private const val IMAGE_ROOT = "images/"

        fun from(path: String, imageId: String): GalleryImage {
            val modifiedPath = path.split("content/albums/").last()
            val imagePath = IMAGE_ROOT + modifiedPath + File.separator
            return GalleryImage(
                albumPath = modifiedPath,
                imageId = imageId,
                thumbnail = imagePath + ConvertedImage.THUMBNAIL.getFilename(imageId),
                smallImage = imagePath + ConvertedImage.SMALL.getFilename(imageId),
                featuredImage = imagePath + ConvertedImage.MEDIUM.getFilename(imageId),
                largeImage = imagePath + ConvertedImage.LARGE.getFilename(imageId),
                originalSize = imagePath + ConvertedImage.ORIGINAL.getFilename(imageId),
            )
        }
    }
}
