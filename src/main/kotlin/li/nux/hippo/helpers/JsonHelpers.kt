package li.nux.hippo.helpers

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import li.nux.hippo.HugoPaths
import li.nux.hippo.model.ImageFrontMatter
import li.nux.hippo.model.KeywordItem

@OptIn(ExperimentalSerializationApi::class)
val prettyJson = Json { // this returns the JsonBuilder
    prettyPrint = true
    encodeDefaults = false
    prettyPrintIndent = "    "
}

fun createDatafileWithAllImages(
    hugoPaths: HugoPaths,
    imfMap: MutableMap<String, ImageFrontMatter>
) {
    val datafile = Paths.get(
        hugoPaths.root.toAbsolutePath().toString() +
            File.separator + "data" + File.separator + "foto" + File.separator + "images.json"
    )
    val content = prettyJson.encodeToString(imfMap)
    Files.write(datafile, content.toByteArray())
}

fun createDatafileWithAllKeywords(
    hugoPaths: HugoPaths,
    imfMap: MutableMap<String, ImageFrontMatter>
) {
    val datafile = Paths.get(
        hugoPaths.root.toAbsolutePath().toString() +
            File.separator + "data" + File.separator + "foto" + File.separator + "keywords.json"
    )
    val keywordItems = imfMap.values.map { it.keywords }
        .flatten()
        .groupBy { it }
        .map { KeywordItem(it.key, it.value.size) }
    val content = prettyJson.encodeToString(keywordItems.sortedByDescending { it.count })
    Files.write(datafile, content.toByteArray())
}