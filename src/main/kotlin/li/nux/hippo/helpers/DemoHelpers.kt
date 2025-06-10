package li.nux.hippo.helpers

import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.isDirectory
import kotlinx.serialization.json.Json
import li.nux.hippo.HippoParams
import li.nux.hippo.HugoPaths
import li.nux.hippo.model.DemoData
import li.nux.hippo.model.DemoImage

private const val NUMBER_OF_ALBUMS = 3
private const val NUMBER_OF_SUB_ALBUMS = 1
private val albumNames = (1.. NUMBER_OF_ALBUMS).toList().map { "Album-$it" }
private val subAlbum = Pair(albumNames.random(), "Sub Album")
const val NUMBER_OF_KEYWORDS_TO_USE = 5
const val MIN_PARAGRAPHS = 1
const val MAX_PARAGRAPHS = 4

fun deleteDemoFiles(hugoPaths: HugoPaths) {
    albumNames.forEach { albumName ->
        val albumPath = hugoPaths.albums.toAbsolutePath().toString() + File.separator + albumName
        File(albumPath).deleteRecursively()

        val assetsAlbumPath = hugoPaths.assets.toAbsolutePath().toString() + File.separator +
            "images" + File.separator+ albumName
        File(assetsAlbumPath).deleteRecursively()

        val albumsIndex = hugoPaths.albums.toAbsolutePath().toString() + File.separator + "_index.md"
        File(albumsIndex).delete()
    }
}
fun fetchImagesIfDemo(params: HippoParams, hugoPaths: HugoPaths): DemoResponse {
    if (!params.demo) {
        return DemoResponse(isDemo = false)
    } else {
        if (!hugoPaths.albums.isDirectory()) {
            Files.createDirectories(hugoPaths.albums.toAbsolutePath())
        }
        // current dir / data / ...
        val demoDataPath = hugoPaths.theme.toString() + File.separator + "data" + File.separator + "demo.json"
        val allLines = Files.readAllLines(Paths.get(demoDataPath)).joinToString("\n")
        val demoData = Json.decodeFromString<DemoData>(allLines)

        val demoImages = mutableMapOf<String, List<DemoImage>>()
        val picsPerAlbum = demoData.images.size / (NUMBER_OF_ALBUMS + NUMBER_OF_SUB_ALBUMS)
        val shuffled = demoData.images.shuffled()
        val slices = shuffled.chunked(picsPerAlbum)
        slices.forEachIndexed { index, slice ->
            val albumName = getAlbumName(index)
            demoImages[albumName] = slice
        }
        val downloadedImagesMap = mutableMapOf<String, List<DemoImage>>()
        demoImages.forEach { (album, images) ->
            val downloadedImages = mutableListOf<DemoImage>()
            images.forEachIndexed { i, image ->
                val downloadPath = hugoPaths.albums.toAbsolutePath().toString() + File.separator + album
                Files.createDirectories(Paths.get(downloadPath).toAbsolutePath())
                val downloadTo = downloadPath + File.separator + "demo_image_$i.jpg"
                downloadImageAsJpeg(image.url, downloadTo)
                downloadedImages.add(
                    image.copy(url = downloadTo)
                )
            }
            downloadedImagesMap[album] = downloadedImages
        }
        return DemoResponse(
            isDemo = true,
            demoImages = downloadedImagesMap,
            demoData = demoData,
        )
    }
}

fun downloadImageAsJpeg(imageUrl: String, outputPath: String) {
    try {
        // Open the URL stream
        val url = URI(imageUrl).toURL()
        val inputStream = url.openStream()

        // Read the image from the input stream
        val image: BufferedImage = ImageIO.read(inputStream)
        inputStream.close()

        // Save the image as JPEG
        val outputFile = File(outputPath)
        ImageIO.write(image, "jpg", outputFile)

        println("Image saved successfully to $outputPath")
    } catch (e: IOException) {
        println("Failed to download or save image: ${e.message}")
    }
}

fun getAlbumName(index: Int): String {
    return if (index < NUMBER_OF_ALBUMS) {
        albumNames[index]
    } else {
        subAlbum.first + File.separator + subAlbum.second
    }.replace(' ', '_')
}

data class DemoResponse(
    val isDemo: Boolean,
    val demoImages: Map<String, List<DemoImage>> = emptyMap(),
    val demoData: DemoData? = null,
)