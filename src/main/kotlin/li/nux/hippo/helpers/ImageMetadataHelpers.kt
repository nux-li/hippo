package li.nux.hippo.helpers

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.Optional
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
import com.drew.metadata.xmp.XmpDirectory
import com.thedeanda.lorem.LoremIpsum
import li.nux.hippo.HippoParams
import li.nux.hippo.model.ExposureDetails
import li.nux.hippo.model.ImageMetadata
import li.nux.hippo.printError
import li.nux.hippo.printIf

fun getDemoImageMetadata(file: Path, demoResponse: DemoResponse): ImageMetadata {
    val path = file.parent.toAbsolutePath().normalize().toString()
    val album = file.parent.fileName.toString().replace("content", "")
    val images = demoResponse.demoImages.values.flatten()
        .groupBy { it.url }.mapKeys { it.key.split(File.separator).last() }
    val filename = file.fileName.toString()
    val demoImage = images[filename]
    val randomDateString = getRandomDate()
    return ImageMetadata(
        path = path,
        album = album,
        filename = filename,
        title = filename.replace("demo_image_", "Image title ").replace(".jpg", ""),
        description = LoremIpsum.getInstance().getParagraphs(MIN_PARAGRAPHS, MAX_PARAGRAPHS),
        credit = demoImage?.first()?.credit ?: "Unknown",
        keywords = demoResponse.demoData?.keywords?.shuffled()?.take(NUMBER_OF_KEYWORDS_TO_USE) ?: emptyList(),
        captureDate = randomDateString.split("T").first(),
        captureTime = randomDateString.split("T").last(),
        exposureDetails = ExposureDetails(
            cameraMake = demoResponse.demoData?.cameraMakers?.shuffled()?.first(),
            cameraModel = demoResponse.demoData?.cameraModels?.shuffled()?.first(),
        )
    )
}

@Throws(IOException::class)
fun getImageMetadata(file: Path, params: HippoParams): ImageMetadata {
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
        printIf(params, "fStop: $fNumber,exposure: $exposureTime,iso: $iso,Make: $make,Model: $model")

        val exifDirectory = maybeExif.orElse(null)
        val exposureDetails = ExposureDetails(
            focalLength = focalLength,
            aperture = fNumber,
            exposureTime = exposureTime,
            iso = iso,
            cameraMake = make,
            cameraModel = model,
        )

        imageMetadata = metadata.getDirectoriesOfType(IptcDirectory::class.java).stream()
            .findFirst()
            .map { iptcDirectory: IptcDirectory ->
                val iptcDate = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_DATE_CREATED)
                val iptcTime = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_TIME_CREATED)
                val fallback = if (iptcDate.isBlank() || iptcTime.isBlank()) {
                    getCaptureDateTimeFromExifOrXmp(metadata, exifDirectory)
                } else null
                val captureDate = iptcDate.ifBlank { fallback?.first }
                val captureTime = iptcTime.ifBlank { fallback?.second }
                ImageMetadata(
                    path = path,
                    album = album,
                    filename = filename,
                    title = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_OBJECT_NAME),
                    description = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_CAPTION),
                    credit = getValueFromIptc(iptcDirectory, IptcDirectory.TAG_CREDIT),
                    year = listOfNotNull(captureDate),
                    captureDate = captureDate,
                    captureTime = captureTime,
                    keywords = Optional.ofNullable(iptcDirectory.keywords)
                        .orElse(emptyList())
                        .map { it.replace("\"", "") }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }),
                    exposureDetails = exposureDetails,
                    extra = mapOf("parameter_name" to "parameter_value"),
                )
            }.orElseGet {
                val fallback = getCaptureDateTimeFromExifOrXmp(metadata, exifDirectory)
                ImageMetadata(
                    path = path,
                    album = album,
                    filename = filename,
                    year = listOfNotNull(fallback?.first),
                    captureDate = fallback?.first,
                    captureTime = fallback?.second,
                    exposureDetails = exposureDetails.ifAnyData(),
                )
            }
    } catch (e: ImageProcessingException) {
        printError("Could not get metadata for " + file.toAbsolutePath() + ": " + e.message)
        imageMetadata = ImageMetadata(path = path, album = album, filename = filename)
    }
    return imageMetadata
}

/**
 * IPTC "Date/Time Created" is often left empty by modern editing tools (e.g. darktable), which instead
 * write the capture date to EXIF DateTimeOriginal/Digitized or, failing that, to the XMP packet
 * (what Finder/Photos label "Content Created"). Falls back to those sources so images that only carry
 * XMP date metadata still get a capture date instead of an empty string that fails downstream parsing.
 */
private fun getCaptureDateTimeFromExifOrXmp(metadata: Metadata, exifDirectory: ExifSubIFDDirectory?): Pair<String, String>? {
    val exifDate = exifDirectory?.dateOriginal ?: exifDirectory?.dateDigitized
    if (exifDate != null) {
        return formatCaptureDateTime(exifDate)
    }

    val xmpValue = metadata.getDirectoriesOfType(XmpDirectory::class.java).stream().findFirst()
        .flatMap { xmpDirectory ->
            Optional.ofNullable(xmpDirectory.xmpProperties)
                .flatMap { props ->
                    Optional.ofNullable(
                        props["exif:DateTimeOriginal"] ?: props["photoshop:DateCreated"] ?: props["xmp:CreateDate"]
                    )
                }
        }
    return xmpValue.map { parseFlexibleDateTime(it) }.orElse(null)
}

private fun formatCaptureDateTime(date: Date): Pair<String, String> {
    val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
    return dateFormat.format(date) to timeFormat.format(date)
}

private val xmpDateTimePattern = Regex("""(\d{4})[:-](\d{2})[:-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})""")

private fun parseFlexibleDateTime(value: String): Pair<String, String>? {
    val groups = xmpDateTimePattern.find(value)?.groupValues ?: return null
    val (year, month, day) = Triple(groups[1], groups[2], groups[3])
    val (hour, minute, second) = Triple(groups[4], groups[5], groups[6])
    return "$year$month$day" to "$hour$minute$second"
}

private fun getRandomDate(): String {
    val lastYear = Calendar.getInstance().get(Calendar.YEAR) - 1
    val dfDateTime = SimpleDateFormat("yyyyMMdd'T'hhmmss", Locale.getDefault())
    val year: Int = randBetween(lastYear - NUM_YEARS_TO_USE, lastYear) // Here you can set Range of years you need
    val month: Int = randBetween(0, MAX_MONTH)
    val hour: Int = randBetween(MIN_HOUR, MAX_HOUR)
    val min: Int = randBetween(0, MAX_MIN_SEC)
    val sec: Int = randBetween(0, MAX_MIN_SEC)

    val gc = GregorianCalendar(year, month, 1)
    val day: Int = randBetween(1, gc.getActualMaximum(Calendar.DAY_OF_MONTH))

    gc[year, month, day, hour, min] = sec

    return dfDateTime.format(gc.time)
}

fun randBetween(start: Int, end: Int): Int {
    return start + Math.round(Math.random() * (end - start)).toInt()
}

private fun getValueFromIptc(iptcDirectory: IptcDirectory, tagId: Int): String {
    return Optional.ofNullable(iptcDirectory.getObject(tagId)).map { obj: Any -> obj.toString() }
        .orElse("")
}

@Throws(ImageProcessingException::class, IOException::class)
private fun getMetadata(file: Path): Metadata {
    return ImageMetadataReader.readMetadata(DataInputStream(FileInputStream(file.toFile())))
}

private const val NUM_YEARS_TO_USE = 7
private const val MAX_MONTH = 11
private const val MIN_HOUR = 6
private const val MAX_HOUR = 19
private const val MAX_MIN_SEC = 59
