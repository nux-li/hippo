package li.nux.hippo.model

enum class ConvertedImage(
    val filenamePostfix: String,
    val reduceToHeight: Int? = null,
    val reduceToWidth: Int? = null,
    val watermarkEnabled: Boolean = true
) {
    THUMBNAIL("_thumb_size.jpg", reduceToHeight = 100, reduceToWidth = null, watermarkEnabled = false),
    SMALL("_small_size.jpg", reduceToHeight = 250, reduceToWidth = null, watermarkEnabled = false),
    MEDIUM("_medium_size.jpg", reduceToHeight = 500),
    LARGE("_large_size.jpg", reduceToHeight = 1000, reduceToWidth = 1500),
    ORIGINAL("_full_size.jpg");

    fun getFilename(imageId: String) = imageId + filenamePostfix

    fun getTypeOfReduceTo(): ResizableBy {
        return reduceToHeight?.let {
            reduceToWidth?.let {
                ResizableBy.BOTH
            } ?: ResizableBy.HEIGHT
        } ?: reduceToWidth?.let {
            ResizableBy.WIDTH
        } ?: ResizableBy.NONE
    }
}

enum class ResizableBy {
    HEIGHT,
    WIDTH,
    BOTH,
    NONE,
}
