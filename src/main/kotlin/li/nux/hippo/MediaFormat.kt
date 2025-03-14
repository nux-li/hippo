package li.nux.hippo

enum class MediaFormat(val mimeType: String) {
    JPEG("image/jpeg"),
    MARKDOWN("text/x-web-markdown"),
    UNKNOWN("unknown");

    companion object {
        fun fromMimeType(mimeType: String): MediaFormat {
            val matches = entries.filter { it.mimeType == mimeType }
            return if (matches.size == 1) {
                matches.first()
            } else {
                UNKNOWN
            }
        }
    }
}
