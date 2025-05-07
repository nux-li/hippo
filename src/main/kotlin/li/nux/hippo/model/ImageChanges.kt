package li.nux.hippo.model

data class ImageChanges(
    val changedOnDisk: MutableList<ImageMetadata>,
    val frontMatterChanged: MutableList<ImageMetadata>,
    val inserted: MutableMap<String, ImageMetadata>,
    val deleted: MutableList<ImageMetadata>,
)
