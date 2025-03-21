package li.nux.hippo.model

import java.util.Base64
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class Album(
    val albumId: String,
    val controlCode: String,
    var title: String = "",
    var description: String = "",
    var coverImage: String = "",
    var subAlbums: List<SubAlbum> = emptyList(),
    val images: List<AlbumImage> = emptyList(),
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
                coverImage = images.first().getReference(),
                subAlbums = emptyList(),
                images = images.map { AlbumImage.from(it) }
            )
        }

        fun rootFolder(contentDirectory: String): Album {
            return Album(
                albumId = "ROOT",
                controlCode = listOf(
                    contentDirectory,
                    "/",
                ).joinToString(",").let { Base64.getEncoder().encodeToString(it.encodeToByteArray()) },
                title = "/",
                description = "Insert description here",
                coverImage = "",
                subAlbums = emptyList(),
                images = emptyList()
            )
        }

        private fun capitalize(name: String) =
            name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

@Serializable
data class SubAlbum(
    val albumId: String,
    val title: String,
    val coverImage: String,
) {
    companion object {
        fun from(album: Album): SubAlbum {
            return SubAlbum(
                albumId = album.albumId,
                title = album.title,
                coverImage = album.coverImage,
            )
        }
    }
}

@Serializable
data class AlbumImage(
    val imageId: String,
    val title: String,
) {
    companion object {
        fun from(image: ImageMetadata): AlbumImage {
            return AlbumImage(
                imageId = image.getReference(),
                title = image.title ?: "Image ${image.getReference()}",
            )
        }
    }
}
