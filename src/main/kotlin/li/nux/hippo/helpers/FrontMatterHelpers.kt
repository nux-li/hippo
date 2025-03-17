package li.nux.hippo.helpers

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import com.akuleshov7.ktoml.Toml
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import li.nux.hippo.FrontMatterFormat
import li.nux.hippo.FrontMatterFormat.JSON
import li.nux.hippo.FrontMatterFormat.TOML
import li.nux.hippo.FrontMatterFormat.YAML
import li.nux.hippo.HippoParams
import li.nux.hippo.ImageFrontMatter
import li.nux.hippo.ImageMetadata
import li.nux.hippo.ImageMetadata.Companion.IMG_NAME_PREFIX
import li.nux.hippo.MediaFormat
import li.nux.hippo.printIf
import org.apache.tika.Tika

@OptIn(ExperimentalSerializationApi::class)
private val prettyJson = Json { // this returns the JsonBuilder
    prettyPrint = true
    encodeDefaults = false
    prettyPrintIndent = "    "
}

private const val TOML_WRAPPING = "+++\n"
private const val YAML_WRAPPING = "---\n"
private fun tomlWrap(string: String): String = TOML_WRAPPING + string + "\n$TOML_WRAPPING"
private fun yamlWrap(string: String): String = YAML_WRAPPING + string + "\n$YAML_WRAPPING"

fun updateAlbumMarkdownDocs(allImages: Map<String, List<ImageMetadata>>, params: HippoParams) {
    printIf(params, "(Re-)creating album markdown files for ${allImages.keys.size} albums")
}

fun createOrReplacePages(albumsWithImages: Map<String, List<ImageMetadata>>, params: HippoParams) {
    albumsWithImages.forEach {
        printIf(params,
            "Album ${it.key} has ${it.value.size} images. Files to create/update:  ${it.key}.md " +
                "${it.value.map { img -> img.getDocumentId() + ".md" }.toList()}"
        )
        it.value.forEach { im ->
            val imFile = Paths.get(im.path + File.separator + im.getReference() + ".md")
            val imf = ImageFrontMatter.from(im)
            val frontMatter = when (params.frontMatterFormat) {
                JSON -> prettyJson.encodeToString(imf)
                TOML -> tomlWrap(Toml.encodeToString(ImageFrontMatter.serializer(), imf))
                YAML -> yamlWrap(Yaml.default.encodeToString(ImageFrontMatter.serializer(), imf))
            }

            when (imFile.exists()) {
                true -> {
                    printIf(params, "File $imFile exists")
                }
                false -> {
                    printIf(params, "File $imFile does not exist")
                    printIf(params, "Front matter to write: \n$frontMatter")
                }
            }
            Files.write(imFile, frontMatter.toByteArray())
        }
    }
}


fun getImagesFromFrontMatters(
    paths: List<Path>,
    tika: Tika
) = paths
    .filter { it.fileName.startsWith(IMG_NAME_PREFIX) }
    .filter {
        tika.detect(it).let { mimeType -> MediaFormat.fromMimeType(mimeType) == MediaFormat.MARKDOWN }
    }.map { getImageDataFromFrontMatter(it).toImageMetadata() }

fun getImageDataFromFrontMatter(file: Path): ImageFrontMatter {
    val allLines = Files.readAllLines(file)
    return when (val fmf = FrontMatterFormat.fromFirstLine(allLines.first())) {
        JSON -> Json.decodeFromString<ImageFrontMatter>(getFrontMatterPart(fmf, allLines))
        TOML -> Toml.decodeFromString(
            serializer(),
            getFrontMatterPart(fmf, allLines)
        )
        YAML -> Yaml.default.decodeFromString(
            ImageFrontMatter.serializer(),
            getFrontMatterPart(fmf, allLines)
        )
    }
}

fun getFrontMatterPart(frontMatterFormat: FrontMatterFormat, lines: List<String>): String {
    val endIndex = lines.lastIndexOf(frontMatterFormat.lastLine)
    val offset = if (frontMatterFormat.excludeWrappers) 1 else 0
    return lines.subList(0 + offset, endIndex + 1 - offset).joinToString("\n")
}
