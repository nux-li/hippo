package li.nux.hippo

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Optional
import java.util.stream.Collectors
import kotlin.io.path.exists
import com.akuleshov7.ktoml.Toml
import com.charleskorn.kaml.Yaml
import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.ImageProcessingException
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifDirectoryBase.TAG_EXPOSURE_TIME
import com.drew.metadata.exif.ExifDirectoryBase.TAG_FNUMBER
import com.drew.metadata.exif.ExifDirectoryBase.TAG_FOCAL_LENGTH
import com.drew.metadata.exif.ExifDirectoryBase.TAG_ISO_EQUIVALENT
import com.drew.metadata.exif.ExifDirectoryBase.TAG_MAKE
import com.drew.metadata.exif.ExifDirectoryBase.TAG_MODEL
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.iptc.IptcDirectory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import li.nux.hippo.FrontMatterFormat.JSON
import li.nux.hippo.FrontMatterFormat.TOML
import li.nux.hippo.FrontMatterFormat.YAML
import li.nux.hippo.ImageMetadata.Companion.IMG_NAME_PREFIX
import org.apache.tika.Tika

@OptIn(ExperimentalSerializationApi::class)
private val prettyJson = Json { // this returns the JsonBuilder
    prettyPrint = true
    encodeDefaults = false
    prettyPrintIndent = "    "
}

fun execute(
    directory: String,
    params: HippoParams,
) {
    StorageService.createTable()
    val storageService = StorageService()
    val path: Path = Paths.get(directory)
    val name = path.fileName.toString()
    println("Searching " + path.fileName.normalize() + " for image files...")
    println("Btw. Precedence: ${params.precedence}, format: ${params.frontMatterFormat}")

    if (name == "content") {
        val tika = Tika()
        val images: MutableMap<String, ImageMetadata> = mapOf<String, ImageMetadata>().toMutableMap()

        val paths = getSetOfPaths(path).stream().sorted().toList().filter { Files.isRegularFile(it) }
        // Fetch all images from previous run
        images.putAll(storageService.fetchAllImages().map { it.getReference() to it })

        // Read all images from disk
        val imagesFromDisk: List<ImageMetadata> = paths.filter {
            tika.detect(it).let { mimeType -> MediaFormat.fromMimeType(mimeType) == MediaFormat.JPEG }
        }.map { getImageMetadata(it) }

        // Read all markdown front matters from disk
        val imagesFromFrontMatters: List<ImageMetadata> = paths
            .filter { it.fileName.startsWith(IMG_NAME_PREFIX) }
            .filter {
                tika.detect(it).let { mimeType -> MediaFormat.fromMimeType(mimeType) == MediaFormat.MARKDOWN }
            }.map { getImageDataFromFrontMatter(it).toImageMetadata() }

        // Compare and store in database based on params
        val imagesChangedOnDisk: MutableList<ImageMetadata> = listOf<ImageMetadata>().toMutableList()
        val frontMatterChanged: MutableList<ImageMetadata> = listOf<ImageMetadata>().toMutableList()
        val toBeInsertedInDb: MutableMap<String, ImageMetadata> = mapOf<String, ImageMetadata>().toMutableMap()
        val toBeDeletedFromDb: MutableList<ImageMetadata> = listOf<ImageMetadata>().toMutableList()
        imagesFromDisk.forEach { fromDisk ->
            if (images.keys.contains(fromDisk.getReference())) {
                if (images[fromDisk.getReference()].hashCode() != fromDisk.hashCode()) {
                    imagesChangedOnDisk.add(fromDisk)
                }
            } else {
                toBeInsertedInDb.put(fromDisk.getReference(), fromDisk)
            }
        }

        imagesFromFrontMatters.forEach { fromFrontMatter ->
            if (images.keys.contains(fromFrontMatter.getReference())) {
                if (images[fromFrontMatter.getReference()].hashCode() != fromFrontMatter.hashCode()) {
                    frontMatterChanged.add(fromFrontMatter)
                }
            }
        }
        images.forEach {
            if (it.key !in imagesFromDisk.map { it.getReference() }) toBeDeletedFromDb.add(it.value)
        }

        val updateList = getUpdateList(params, imagesChangedOnDisk, frontMatterChanged)

        toBeInsertedInDb.values.forEach { handleNewImage(storageService, it) }
        updateList.forEach { handleUpdate(storageService, images[it.getReference()]?.id!!, it) }
        toBeDeletedFromDb.forEach { handleDelete(storageService, it) }

        // DB is updated. Now handle markdown files
        createOrReplacePages(storageService.fetchAllImages().groupBy { it.getAlbumId() }, params.frontMatterFormat)
    } else {
        println(
            "Parameter was $directory. It should have been the path of the Hugo content folder. No changes done."
        )
    }
}

private fun getUpdateList(
    params: HippoParams,
    imagesChangedOnDisk: MutableList<ImageMetadata>,
    frontMatterChanged: MutableList<ImageMetadata>
) = when (params.changeAcceptance) {
    ChangeAcceptance.ACCEPT_CHANGES_IN_METADATA -> imagesChangedOnDisk
    ChangeAcceptance.ACCEPT_CHANGES_IN_MARKDOWN -> frontMatterChanged
    ChangeAcceptance.ACCEPT_FROM_BOTH -> {
        when (params.precedence) {
            Precedence.FRONT_MATTER -> frontMatterChanged
            Precedence.IMAGE_METADATA -> imagesChangedOnDisk
        }
    }
}

fun createOrReplacePages(albumsWithImages: Map<String, List<ImageMetadata>>, format: FrontMatterFormat) {
    albumsWithImages.forEach {
        println(
            "Album ${it.key} has ${it.value.size} images. Files to create/update:  ${it.key}.md " +
                "${it.value.map { img -> img.getDocumentId() + ".md" }.toList()}"
        )
        it.value.forEach { im ->
            val imFile = Paths.get(im.path + File.separator + im.getReference() + ".md")
            val imf = ImageFrontMatter.from(im)
            val frontMatter = when (format) {
                JSON -> prettyJson.encodeToString(imf)
                TOML -> "+++\n" + Toml.encodeToString(ImageFrontMatter.serializer(), imf) + "\n+++\n"
                YAML -> "---\n" + Yaml.default.encodeToString(ImageFrontMatter.serializer(), imf) +"\n---\n"
            }

            when (imFile.exists()) {
                true -> {
                    println("File $imFile exists")
                }
                false -> {
                    println("File $imFile does not exist")
                    println("Front matter to write: \n$frontMatter")
                }
            }
            Files.write(imFile, frontMatter.toByteArray())
        }
    }
}

private fun getImageDataFromFrontMatter(file: Path): ImageFrontMatter {
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

private fun handleUpdate(
    storageService: StorageService,
    id: Int,
    imageMetadata: ImageMetadata,
) {
    storageService.updatePostedImage(id, imageMetadata)
}

fun handleDelete(storageService: StorageService, imageMetadata: ImageMetadata) {
    storageService.removePostedImage(imageMetadata.id!!, imageMetadata.getReference())
}


private fun handleNewImage(storageService: StorageService, imageMetadata: ImageMetadata): ImageMetadata {
    val insertId: Int = storageService.insertPostedImage(imageMetadata)
    imageMetadata.id = insertId
    println(
        "New Jpeg image " + imageMetadata.getAlbumAndFilename() +
            " found. Metadata: " + imageMetadata + ". HashCode: " + imageMetadata.hashCode()
    )
    return imageMetadata
}

@Throws(IOException::class)
private fun getImageMetadata(file: Path): ImageMetadata {
    var imageMetadata: ImageMetadata
    val path = file.parent.toAbsolutePath().normalize().toString()
    val album = file.parent.fileName.toString().replace("content", "")
    val filename = file.fileName.toString()
    try {
        val metadata: Metadata = getMetadata(file)
        val maybeExif = metadata.getDirectoriesOfType(ExifSubIFDDirectory::class.java).stream().findFirst()
        val focalLength = maybeExif.map { exif -> exif.getDescription(TAG_FOCAL_LENGTH) }.orElse("")
        val fNumber = maybeExif.map { exif -> exif.getDescription(TAG_FNUMBER) }.orElse("")
        val exposureTime = maybeExif.map { exif -> exif.getDescription(TAG_EXPOSURE_TIME) }.orElse("")
        val iso = maybeExif.map { exif -> exif.getDescription(TAG_ISO_EQUIVALENT) }.orElse("")
        val make = maybeExif.map { exif -> exif.getString(TAG_MAKE) }.orElse("")
        val model = maybeExif.map { exif -> exif.getString(TAG_MODEL) }.orElse("")
        println("fStop: $fNumber,exposure: $exposureTime,iso: $iso,Make: $make,Model: $model")

        imageMetadata = metadata.getDirectoriesOfType(IptcDirectory::class.java).stream()
            .findFirst()
            .map { iptcDirectory: IptcDirectory ->
                ImageMetadata(
                    path = path,
                    album = album,
                    filename = filename,
                    title = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_OBJECT_NAME),
                    description = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_CAPTION),
                    credit = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_CREDIT),
                    captureDate = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_DATE_CREATED),
                    captureTime = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_TIME_CREATED),
                    keywords = iptcDirectory.keywords,
                    exposureDetails = ExposureDetails(
                        focalLength = focalLength,
                        aperture = fNumber,
                        exposureTime = exposureTime,
                        iso = iso,
                        cameraMake = make,
                        cameraModel = model,
                    )
                )
            }.orElse(ImageMetadata(path = path, album = album, filename = filename))
    } catch (e: ImageProcessingException) {
        println("Could not get metadata for " + file.toAbsolutePath() + ": " + e.message)
        imageMetadata = ImageMetadata(path = path, album = album, filename = filename)
    }
    return imageMetadata
}

private fun getValueFromIptc(iptcDirectory: IptcDirectory, tagId: Int): String {
    return Optional.ofNullable(iptcDirectory.getObject(tagId)).map { obj: Any -> obj.toString() }
        .orElse("")
}

@Throws(ImageProcessingException::class, IOException::class)
private fun getMetadata(file: Path): Metadata {
    return ImageMetadataReader.readMetadata(DataInputStream(FileInputStream(file.toFile())))
}

private fun getSetOfPaths(path: Path): Set<Path> {
    try {
        Files.walk(path).use { stream ->
            return stream.collect(Collectors.toSet())
        }
    } catch (e: IOException) {
        println("Failed to get files from path ${path.toAbsolutePath()}: " + e.message)
        return setOf()
    }
}
