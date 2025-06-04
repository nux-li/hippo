package li.nux.hippo.helpers

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors
import kotlin.io.path.isDirectory
import li.nux.hippo.HippoParams
import li.nux.hippo.HugoPaths
import li.nux.hippo.MAX_DIRECTORY_DEPTH
import li.nux.hippo.printError
import li.nux.hippo.printIf

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

fun isDirectSubfolder(subfolder: String, albumPath: String): Boolean {
    return subfolder != albumPath &&
        subfolder.startsWith(albumPath) &&
        !subfolder.removePrefix(albumPath + File.separator).contains(File.separator)
}

fun sanitizeDirectoryNames(hugoPaths: HugoPaths, params: HippoParams) {
    if (!hugoPaths.albums.isDirectory()) {
        Files.createDirectories(hugoPaths.albums.toAbsolutePath())
    }
    Files.walk(hugoPaths.albums, MAX_DIRECTORY_DEPTH)
        .filter { p -> Files.isDirectory(p) }
        .collect(Collectors.toList())
        .forEach {
            val (needFix, newName) = getUrlFriendlyName(it.fileName.toString())
            if (needFix) {
                val pathBuilder: MutableList<String> = it.toAbsolutePath().toString()
                    .split(File.separator)
                    .toMutableList()
                pathBuilder.removeLast()
                pathBuilder.add(newName)
                val toPath = pathBuilder.joinToString(File.separator)
                Files.move(it, Paths.get(toPath), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    printIf(params, "Done checking folder names...")
}

fun getUrlFriendlyName(old: String): Pair<Boolean, String> {
    val renamed = old.toCharArray()
        .map { if(it == ' ' || it == '&' || it == '+') '_' else it }
        .map { if(allowedChars.contains(it)) it else '_' }
        .joinToString("")
    return Pair(old != renamed, renamed)
}

val allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789()-_,.;[]".toCharArray().toList()
