package li.nux.hippo

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.imageio.IIOException
import javax.imageio.ImageIO
import kotlin.math.min
import li.nux.hippo.TaskResult.ALBUMS_FROM_PREVIOUS_RUN
import li.nux.hippo.TaskResult.CHANGED_FRONT_MATTERS
import li.nux.hippo.TaskResult.CHANGED_IMAGE_FILES
import li.nux.hippo.TaskResult.DELETED_IMAGES
import li.nux.hippo.TaskResult.IMAGES_FROM_PREVIOUS_RUN
import li.nux.hippo.TaskResult.NEW_ALBUM_TOTAL
import li.nux.hippo.TaskResult.NEW_IMAGES
import li.nux.hippo.TaskResult.NEW_IMAGE_TOTAL
import li.nux.hippo.helpers.DemoResponse
import li.nux.hippo.helpers.createOrReplacePages
import li.nux.hippo.helpers.fetchImagesIfDemo
import li.nux.hippo.helpers.getAllImagesFromDisk
import li.nux.hippo.helpers.getImagesFromFrontMatters
import li.nux.hippo.helpers.getSetOfPaths
import li.nux.hippo.helpers.handleDelete
import li.nux.hippo.helpers.handleNewImage
import li.nux.hippo.helpers.handleUpdate
import li.nux.hippo.helpers.sanitizeDirectoryNames
import li.nux.hippo.helpers.updateAlbumMarkdownDocs
import li.nux.hippo.model.ConvertedImage
import li.nux.hippo.model.ImageChanges
import li.nux.hippo.model.ImageMetadata
import li.nux.hippo.model.ResizableBy
import net.coobird.thumbnailator.filters.Watermark
import net.coobird.thumbnailator.geometry.Positions
import org.apache.tika.Tika
import org.imgscalr.Scalr

const val MAX_DIRECTORY_DEPTH = 10
const val WATERMARK_LOWER_THRESHOLD = 1000
const val WATERMARK_MIN_WIDTH = 200
const val FIFTH = 5
const val WATERMARK_OPACITY = 0.3f

fun init() {
    StorageService.createTable()
}

fun execute(
    hugoPaths: HugoPaths,
    params: HippoParams,
) {
    val path = hugoPaths.content
    val storageService = StorageService()
    val taskResults: MutableMap<TaskResult, Int> = TaskResult.initMap()

    printIf(params, "Searching " + path.fileName.normalize() + " for image files...")
    sanitizeDirectoryNames(hugoPaths, params)

    // TODO if demo flag is set - check whether albums dir is empty - if not: fail
    //      when empty download images specified in dummy.json and place them
    //      in random subfolders under albums directory
    val demoResponse = fetchImagesIfDemo(params, hugoPaths)

    printIf(
        params,
        "Btw. Verbose: ${params.verbose}, Precedence: ${params.precedence}, format: ${params.frontMatterFormat}"
    )

    val imageChanges = synchronizeImages(path, storageService, taskResults, params, demoResponse)

    // DB is updated. Now handle markdown files
    val allImages = storageService.fetchAllImages()
        .also {
            taskResults[NEW_IMAGE_TOTAL] = it.size
            taskResults[NEW_ALBUM_TOTAL] = it.groupBy { im -> im.album }.keys.size
        }
        .groupBy { it.getAlbumId() }
        .also { createOrReplacePages(it, params, hugoPaths) }
    updateAlbumMarkdownDocs(allImages, params, hugoPaths)
    val newAndChangedImages = imageChanges.changedOnDisk.plus(imageChanges.inserted.values)
    createImageFiles(newAndChangedImages.groupBy { it.getAlbumId() }, params)
    printResult(taskResults)
}

fun createImageFiles(
    imageByAlbum: Map<String, List<ImageMetadata>>,
    params: HippoParams
) {
    if (imageByAlbum.keys.isNotEmpty()) {
        println(
            "Creating resized image sets for ${imageByAlbum.keys.size} albums:"
        )
    }
    imageByAlbum.forEach { (albumId, imageMetadataList) ->
        val totalFiles = imageMetadataList.size
        imageMetadataList.forEachIndexed { index, imageMetadata ->
            val destinationFolder = imageMetadata.path.replace("content/albums", "assets/images")
            val destination = Path.of(destinationFolder)
            Files.createDirectories(destination)
            print("AlbumId $albumId - Image sets created: $index / $totalFiles...")
            ConvertedImage.entries.forEach { convertedImageSize ->
                when (val watermarkFilename = if (convertedImageSize.watermarkEnabled) params.watermark else null) {
                    null -> createResizedImageSetsWithoutWatermarks(
                        imageMetadata,
                        convertedImageSize,
                        destination,
                        destinationFolder
                    )
                    else -> createResizedImageSetsWithWatermarks(
                        imageMetadata,
                        convertedImageSize,
                        watermarkFilename,
                        destinationFolder
                    )
                }
                print("\r")
            }
        }
        println("AlbumId $albumId - Image sets created: $totalFiles / $totalFiles... Done")
    }
}

private fun createResizedImageSetsWithWatermarks(
    imageMetadata: ImageMetadata,
    convertedImageSize: ConvertedImage,
    watermarkFilename: String,
    destinationFolder: String
) {
    val albumPath = imageMetadata.path + File.separator
    val originalImage = getImageResizedIfNeeded(convertedImageSize, albumPath, imageMetadata)
    val wmDim = originalImage.width.let { w -> if (w < WATERMARK_LOWER_THRESHOLD) WATERMARK_MIN_WIDTH else w / FIFTH }
    val watermark = getAdjustedWatermark(watermarkFilename, wmDim)

    val watermarkFilter = Watermark(Positions.BOTTOM_LEFT, watermark, WATERMARK_OPACITY)
    val watermarked = watermarkFilter.apply(originalImage)
    ImageIO.write(
        watermarked,
        "jpg",
        File(
            destinationFolder +
                File.separator +
                imageMetadata.getReference() + convertedImageSize.filenamePostfix
        )
    )
}

private fun createResizedImageSetsWithoutWatermarks(
    imageMetadata: ImageMetadata,
    convertedImageSize: ConvertedImage,
    destination: Path,
    destinationFolder: String
) {
    val albumPath = imageMetadata.path + File.separator
    val originalImage = ImageIO.read(File(albumPath + imageMetadata.filename))
    val reduceInfo = ReduceInfo.from(convertedImageSize, originalImage.width, originalImage.height)

    when (reduceInfo.needResize) {
        false -> {
            val imageFrom = Path.of(albumPath + imageMetadata.filename)
            Files.copy(
                imageFrom,
                destination.resolve(
                    imageMetadata.getReference() + convertedImageSize.filenamePostfix
                )
            )
        }

        true -> {
            try {
                val resized = Scalr.resize(
                    originalImage,
                    Scalr.Method.ULTRA_QUALITY,
                    reduceInfo.scaleMode,
                    if (reduceInfo.scaleMode == Scalr.Mode.FIT_TO_HEIGHT) {
                        min(originalImage.height, reduceInfo.reduceTo)
                    } else {
                        min(originalImage.width, reduceInfo.reduceTo)
                    }
                )
                ImageIO.write(
                    resized,
                    "jpg",
                    File(
                        destinationFolder +
                            File.separator +
                            imageMetadata.getReference() + convertedImageSize.filenamePostfix
                    )
                )
            } catch (e: IIOException) {
                println("Error writing to " + albumPath + imageMetadata.filename + ": " + e.message)
                throw e
            }
        }
    }
}

private fun getAdjustedWatermark(watermarkFilename: String, wmDim: Int): BufferedImage {
    val watermarkImage
        : BufferedImage = ImageIO.read(File(watermarkFilename))
    val resizedWatermark = Scalr.resize(
        watermarkImage,
        Scalr.Method.ULTRA_QUALITY,
        Scalr.Mode.FIT_TO_WIDTH,
        wmDim
    )
    return resizedWatermark
}

private fun getImageResizedIfNeeded(
    convertedImageSize: ConvertedImage,
    albumPath: String,
    imageMetadata: ImageMetadata
): BufferedImage {
    val originalImage = ImageIO.read(File(albumPath + imageMetadata.filename))
    val reduceInfo = ReduceInfo.from(convertedImageSize, originalImage.width, originalImage.height)
    return when (reduceInfo.needResize) {
        false -> originalImage
        true -> Scalr.resize(
            originalImage,
            Scalr.Method.ULTRA_QUALITY,
            reduceInfo.scaleMode,
            if (reduceInfo.scaleMode == Scalr.Mode.FIT_TO_HEIGHT) {
                min(originalImage.height, reduceInfo.reduceTo)
            } else {
                min(originalImage.width, reduceInfo.reduceTo)
            }
        )
    }
}

private fun synchronizeImages(
    path: Path,
    storageService: StorageService,
    taskResults: MutableMap<TaskResult, Int>,
    params: HippoParams,
    demoResponse: DemoResponse
): ImageChanges {
    val tika = Tika()
    val images: MutableMap<String, ImageMetadata> = mapOf<String, ImageMetadata>().toMutableMap()

    val paths = getSetOfPaths(path).stream().sorted().toList().filter { Files.isRegularFile(it) }
    // Fetch all images from previous run
    images.putAll(
        storageService.fetchAllImages()
            .also {
                taskResults[IMAGES_FROM_PREVIOUS_RUN] = it.size
                taskResults[ALBUMS_FROM_PREVIOUS_RUN] = it.groupBy { im -> im.album }.keys.size
            }
            .map { it.getReference() to it }
    )

    // Read all images from disk
    val imagesFromDisk: List<ImageMetadata> = getAllImagesFromDisk(paths, tika, params, demoResponse)

    // Read all markdown front matters from disk
    val imagesFromFrontMatters: List<ImageMetadata> = getImagesFromFrontMatters(paths, tika)

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
            toBeInsertedInDb[fromDisk.getReference()] = fromDisk
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
        if (it.key !in imagesFromDisk.map { im -> im.getReference() }) toBeDeletedFromDb.add(it.value)
    }
    taskResults[CHANGED_IMAGE_FILES] = imagesChangedOnDisk.size
    taskResults[CHANGED_FRONT_MATTERS] = frontMatterChanged.size
    taskResults[NEW_IMAGES] = toBeInsertedInDb.size
    taskResults[DELETED_IMAGES] = toBeDeletedFromDb.size
    val updateList = getUpdateList(params, imagesChangedOnDisk, frontMatterChanged)

    toBeInsertedInDb.values.forEach { handleNewImage(storageService, it, params) }
    updateList.forEach { handleUpdate(storageService, images[it.getReference()]?.id!!, it) }
    toBeDeletedFromDb.forEach { handleDelete(storageService, it) }

    return ImageChanges(
        imagesChangedOnDisk,
        frontMatterChanged,
        toBeInsertedInDb,
        toBeDeletedFromDb
    )
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

fun printResult(taskResults: MutableMap<TaskResult, Int>) {
    taskResults.keys.maxOfOrNull { it.description.length }?.let { len ->
        println("=".repeat(len*2))
        taskResults.forEach {
            val str = String.format(Locale.getDefault(), "%${len}s : %d", it.key.description, it.value)
            println(str)
        }
        println("=".repeat(len*2))
    }
}

data class ReduceInfo(
    val needResize: Boolean,
    val scaleMode: Scalr.Mode,
    val reduceTo: Int,
) {
    companion object {
        fun from(convertedImageSize: ConvertedImage, width: Int, height: Int): ReduceInfo {
            return when (convertedImageSize.getTypeOfReduceTo()) {
                ResizableBy.HEIGHT -> ReduceInfo(true, Scalr.Mode.FIT_TO_HEIGHT, convertedImageSize.reduceToHeight!!)
                ResizableBy.WIDTH -> ReduceInfo(true, Scalr.Mode.FIT_TO_WIDTH, convertedImageSize.reduceToWidth!!)
                ResizableBy.BOTH -> when (width > height) {
                    true -> ReduceInfo(true, Scalr.Mode.FIT_TO_WIDTH, convertedImageSize.reduceToWidth!!)
                    false -> ReduceInfo(true, Scalr.Mode.FIT_TO_HEIGHT, convertedImageSize.reduceToHeight!!)
                }
                ResizableBy.NONE -> ReduceInfo(false, Scalr.Mode.FIT_TO_HEIGHT, 1)
            }
        }
    }
}
