package li.nux.hippo.model

enum class ConvertedImage(
    val filenamePostfix: String,
    val reduceTo: Int? = null,
    val watermarkEnabled: Boolean = true
) {
    THUMBNAIL("_thumb_size.jpg", 100, false),
    SMALL("_small_size.jpg", 250, false),
    MEDIUM("_medium_size.jpg", 500),
    LARGE("_large_size.jpg", 1000),
    ORIGINAL("_full_size.jpg");

    fun getFilename(imageId: String) = imageId + filenamePostfix
}