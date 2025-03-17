package li.nux.hippo.helpers

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import li.nux.hippo.printError

fun getSetOfPaths(path: Path): Set<Path> {
    try {
        Files.walk(path).use { stream ->
            return stream.collect(Collectors.toSet())
        }
    } catch (e: IOException) {
        printError("Failed to get files from path ${path.toAbsolutePath()}: " + e.message)
        return setOf()
    }
}