package li.nux.hippo

enum class TaskResult(val description: String) {
    IMAGES_FROM_PREVIOUS_RUN("Images from previous run"),
    ALBUMS_FROM_PREVIOUS_RUN("Albums from previous run"),
    NEW_IMAGES("New images"),
    CHANGED_IMAGE_FILES("Changeed image files"),
    CHANGED_FRONT_MATTERS("Changeed front matters"),
    DELETED_IMAGES("Deleted images"),
    NEW_IMAGE_TOTAL("New image count"),
    NEW_ALBUM_TOTAL("New album count");

    companion object {
        fun initMap(): MutableMap<TaskResult, Int> = entries.associateWith { 0 }.toMutableMap()
    }
}