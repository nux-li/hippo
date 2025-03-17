package li.nux.hippo

import java.nio.file.Files
import java.nio.file.Path
import li.nux.hippo.TaskResult.ALBUMS_FROM_PREVIOUS_RUN
import li.nux.hippo.TaskResult.CHANGED_FRONT_MATTERS
import li.nux.hippo.TaskResult.CHANGED_IMAGE_FILES
import li.nux.hippo.TaskResult.DELETED_IMAGES
import li.nux.hippo.TaskResult.IMAGES_FROM_PREVIOUS_RUN
import li.nux.hippo.TaskResult.NEW_ALBUM_TOTAL
import li.nux.hippo.TaskResult.NEW_IMAGES
import li.nux.hippo.TaskResult.NEW_IMAGE_TOTAL
import li.nux.hippo.helpers.createOrReplacePages
import li.nux.hippo.helpers.getAllImagesFromDisk
import li.nux.hippo.helpers.getImagesFromFrontMatters
import li.nux.hippo.helpers.getSetOfPaths
import li.nux.hippo.helpers.handleDelete
import li.nux.hippo.helpers.handleNewImage
import li.nux.hippo.helpers.handleUpdate
import li.nux.hippo.helpers.updateAlbumMarkdownDocs
import org.apache.tika.Tika

fun init() {
    StorageService.createTable()
}

fun execute(
    path: Path,
    params: HippoParams,
) {
    val storageService = StorageService()
    val taskResults: MutableMap<TaskResult, Int> = TaskResult.initMap()
    printIf(params, "Searching " + path.fileName.normalize() + " for image files...")
    printIf(
        params,
        "Btw. Verbose: ${params.verbose}, Precedence: ${params.precedence}, format: ${params.frontMatterFormat}"
    )

    synchronizeImages(path, storageService, taskResults, params)

    // DB is updated. Now handle markdown files
    val allImages = storageService.fetchAllImages()
        .also {
            taskResults[NEW_IMAGE_TOTAL] = it.size
            taskResults[NEW_ALBUM_TOTAL] = it.groupBy { im -> im.album }.keys.size
        }
        .groupBy { it.getAlbumId() }
        .also { createOrReplacePages(it, params) }
    updateAlbumMarkdownDocs(allImages, params)
    printResult(taskResults)
}

private fun synchronizeImages(
    path: Path,
    storageService: StorageService,
    taskResults: MutableMap<TaskResult, Int>,
    params: HippoParams
) {
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
    val imagesFromDisk: List<ImageMetadata> = getAllImagesFromDisk(paths, tika, params)

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
    taskResults.forEach {
        println("${it.key.description}: ${it.value}")
    }
}
