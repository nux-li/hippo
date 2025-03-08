package li.nux.hippo

import java.io.DataInputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Optional
import java.util.function.Consumer
import java.util.stream.Collectors
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
import org.apache.tika.Tika

fun execute(directory: String, precedence: Precedence, format: FrontMatterFormat) {
    StorageService.createTable()
    val storageService = StorageService()
    val path: Path = Paths.get(directory)
    val name = path.fileName.toString()
    println("Searching " + path.fileName.normalize() + " for image files...")
    println("Btw. Precedence: $precedence, format: $format")

    if (name == "content") {
        val imagesWithMetadata: MutableList<ImageMetadata> = ArrayList()

        getSetOfPaths(path).stream().sorted().toList().forEach(Consumer { file: Path ->
            if (Files.isRegularFile(file)) {
                handleFile(file, storageService, imagesWithMetadata, precedence)
            }
        })
        val albumMap: Map<String, List<ImageMetadata>> = imagesWithMetadata.groupBy { it.album }
        albumMap.forEach {
            println(
                "Album ${it.key} has ${it.value.size} images. Files to create/update:  ${it.key}.md " +
                    "${it.value.map { img -> img.getDocumentId() + ".md" }.toList()}"
            )
        }
    } else {
        println(
            "Parameter was $directory. It should have been the path of the Hugo content folder. No changes done."
        )
    }
}

private fun handleFile(
    file: Path,
    storageService: StorageService,
    imagesWithMetadata: MutableList<ImageMetadata>,
    precedence: Precedence
) {
    val tika = Tika()
    try {
        when (MediaFormat.fromMimeType(tika.detect(file))) {
            MediaFormat.JPEG -> {
                val imageMetadata: ImageMetadata = getImageMetadata(file)
                when (val existing = storageService.exists(imageMetadata.getReference())) {
                    null -> handleNewImage(storageService, imageMetadata)
                    else -> handleExistingImage(existing, imageMetadata, precedence)
                }

                imagesWithMetadata.add(imageMetadata)
            }

            else -> println("Ignored ${file.fileName} due to unsupported format")
        }
    } catch (e: IOException) {
        println(file.toAbsolutePath().toString() + " could not be detected: " + e.message)
    }
}

private fun handleExistingImage(existing: ImageMetadata, imageMetadata: ImageMetadata, precedence: Precedence) {
    if (existing.hashCode() != imageMetadata.hashCode()) {
        println("Detected updated image: ${imageMetadata.getAlbumAndFilename()}")
        // TODO update image
    } else {
        println("No change for image: ${imageMetadata.getAlbumAndFilename()}")
    }
}

private fun handleNewImage(storageService: StorageService, imageMetadata: ImageMetadata) {
    val insertId: Int = storageService.insertPostedImage(imageMetadata)
    imageMetadata.id = insertId
    println(
        "New Jpeg image " + imageMetadata.getAlbumAndFilename() +
            " found. Metadata: " + imageMetadata + ". HashCode: " + imageMetadata.hashCode()
    )
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
                        fStop = fNumber,
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
