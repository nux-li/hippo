package li.nux.hippo.helpers

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import li.nux.hippo.HugoPaths
import li.nux.hippo.model.ImageFrontMatter.Companion.MARK_DOWN_FILE_EXTENSION

fun clear(hugoPaths: HugoPaths) {
    deleteDemoFiles(hugoPaths)
    File(hugoPaths.theme.toAbsolutePath().toString() + File.separator + "hippo.db").delete()
}

fun regenerate(hugoPaths: HugoPaths) {
    deleteMarkdownFiles(hugoPaths)
    File(hugoPaths.theme.toAbsolutePath().toString() + File.separator + "hippo.db").delete()
}

fun deleteMarkdownFiles(hugoPaths: HugoPaths) {
    Files.walk(hugoPaths.content)
        .filter { path ->
            path.isRegularFile() && path.toString().endsWith(MARK_DOWN_FILE_EXTENSION)
        }
        .forEach { path ->
            try {
                Files.delete(path)
            } catch (e: IOException) {
                println("Failed to remove $path: ${e.message}")
            }
        }
}