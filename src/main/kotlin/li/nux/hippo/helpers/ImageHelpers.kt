package li.nux.hippo.helpers

import java.nio.file.Path
import li.nux.hippo.HippoParams
import li.nux.hippo.model.ImageMetadata
import li.nux.hippo.MediaFormat
import li.nux.hippo.StorageService
import li.nux.hippo.printIf
import org.apache.tika.Tika

fun getAllImagesFromDisk(
    paths: List<Path>,
    tika: Tika,
    params: HippoParams,
): List<ImageMetadata> {
    return paths
        .filter { isJpeg(tika, it) }
        .filterNot { it.fileName.startsWith(ImageMetadata.IMG_NAME_PREFIX) }
        .map { getImageMetadata(it, params) }
}

private fun isJpeg(tika: Tika, it: Path): Boolean {
    return tika.detect(it).let { mimeType -> MediaFormat.fromMimeType(mimeType) == MediaFormat.JPEG }
}

fun handleUpdate(
    storageService: StorageService,
    id: Int,
    imageMetadata: ImageMetadata,
) {
    storageService.updatePostedImage(id, imageMetadata)
}

fun handleDelete(storageService: StorageService, imageMetadata: ImageMetadata) {
    storageService.removePostedImage(imageMetadata.id!!, imageMetadata.getReference())
}

fun handleNewImage(
    storageService: StorageService,
    imageMetadata: ImageMetadata,
    params: HippoParams
): ImageMetadata {
    val insertId: Int = storageService.insertPostedImage(imageMetadata)
    imageMetadata.id = insertId
    printIf(
        params,
        "New Jpeg image " + imageMetadata.getAlbumAndFilename() +
            " found. Metadata: " + imageMetadata + ". HashCode: " + imageMetadata.hashCode()
    )
    return imageMetadata
}
