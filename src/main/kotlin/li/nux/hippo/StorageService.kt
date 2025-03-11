package li.nux.hippo

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.Date
import mu.KotlinLogging

private const val DATABASE_URL = "jdbc:sqlite:hippo.db"
val log = KotlinLogging.logger {}

fun connect(): Connection {
    return try {
        DriverManager.getConnection(DATABASE_URL)
    } catch (e: SQLException) {
        log.error("Could not get connection to db: {}", e.message)
        throw e
    }
}

class StorageService {
    fun exists(imageId: String): ImageMetadata? {
        val connection = connect()
        val result = try {
            connection.prepareStatement(FIND_BY_IMAGE_ID).use { prepped ->
                prepped.setString(1, imageId)
                val resultSet = prepped.executeQuery()
                while (resultSet.next()) {
                    return ImageMetadata(
                        id = resultSet.getInt("id"),
                        path = resultSet.getString("path"),
                        album = resultSet.getString("album_name"),
                        filename = resultSet.getString("photo_filename"),
                        title = resultSet.getString("title"),
                        description = resultSet.getString("description"),
                        credit = resultSet.getString("credit"),
                        captureDate = resultSet.getString("capture_date"),
                        captureTime = resultSet.getString("capture_time"),
                        keywords = resultSet.getString("keywords").split(", ", "; ", ",", ";"),
                        exposureDetails = ExposureDetails(
                            focalLength = resultSet.getString("focal_length"),
                            aperture = resultSet.getString("f_number"),
                            exposureTime = resultSet.getString("exposure_time"),
                            iso = resultSet.getString("iso"),
                            cameraMake = resultSet.getString("camera_make"),
                            cameraModel = resultSet.getString("camera_model"),
                        ),
                        created = resultSet.getTimestamp("created").toLocalDateTime(),
                        updated = resultSet.getTimestamp("updated").toLocalDateTime(),
                    )
                }
                null
            }
        } catch (e: SQLException) {
            System.err.println(e.message)
            null
        }
        return result
    }

    fun updatePostedImage(img: ImageMetadata) {
        when (val id = img.id) {
            null -> throw StorageException("Cannot update posted image. ID is missing")
            else -> {
                val connection = connect()
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
                try {
                    connection.prepareStatement(UPDATE_POSTED_IMAGE).use { prepped ->
                        prepped.setInt(1, img.hashCode())
                        prepped.setString(2, img.title)
                        prepped.setString(3, img.description)
                        prepped.setString(4, img.credit)
                        prepped.setString(5, img.captureDate)
                        prepped.setString(6, img.captureTime)
                        prepped.setString(7, java.lang.String.join(", ", img.keywords))
                        prepped.setString(8, img.exposureDetails?.focalLength)
                        prepped.setString(9, img.exposureDetails?.aperture)
                        prepped.setString(10, img.exposureDetails?.exposureTime)
                        prepped.setString(11, img.exposureDetails?.iso)
                        prepped.setString(12, img.exposureDetails?.cameraMake)
                        prepped.setString(13, img.exposureDetails?.cameraModel)
                        prepped.setString(14, now)
                        prepped.executeUpdate()
                    }
                } catch (e: SQLException) {
                    log.error("Failed to update posted image with id ${img.id}: {}", e.message)
                    throw StorageException("Failed to update posted image with id ${img.id}")
                }
            }
        }
    }

    fun insertPostedImage(img: ImageMetadata): Int {
        val connection = connect()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        try {
            connection.prepareStatement(INSERT_POSTED_IMAGE).use { prepped ->
                prepped.setString(1, img.getReference())
                prepped.setString(2, img.path)
                prepped.setString(3, img.album)
                prepped.setString(4, img.filename)
                prepped.setInt(5, img.hashCode())
                prepped.setString(6, img.title)
                prepped.setString(7, img.description)
                prepped.setString(8, img.credit)
                prepped.setString(9, img.captureDate)
                prepped.setString(10, img.captureTime)
                prepped.setString(11, java.lang.String.join(", ", img.keywords))
                prepped.setString(12, img.exposureDetails?.focalLength)
                prepped.setString(13, img.exposureDetails?.aperture)
                prepped.setString(14, img.exposureDetails?.exposureTime)
                prepped.setString(15, img.exposureDetails?.iso)
                prepped.setString(16, img.exposureDetails?.cameraMake)
                prepped.setString(17, img.exposureDetails?.cameraModel)
                prepped.setString(18, now)
                prepped.setString(19, now)
                prepped.executeUpdate()
                return prepped.generatedKeys.getInt(1)
            }
        } catch (e: SQLException) {
            System.err.println(e.message)
            return -1
        }
    }

    companion object {
        private const val CREATE_TABLE_POSTED_IMAGE = """
            CREATE TABLE IF NOT EXISTS posted_image (
                id INTEGER PRIMARY KEY, 
                image_id TEXT NOT NULL, 
                path TEXT NOT NULL, 
                album_name TEXT NOT NULL, 
                photo_filename TEXT NOT NULL, 
                hash_code INTEGER NOT NULL, 
                title TEXT, 
                description TEXT, 
                credit TEXT, 
                capture_date TEXT, 
                capture_time TEXT, 
                keywords TEXT, 
                focal_length TEXT,
                f_number TEXT,
                exposure_time TEXT,
                iso TEXT,
                camera_make TEXT,
                camera_model TEXT,
                created DATETIME NOT NULL, 
                updated DATETIME NOT NULL
            );"""

        private const val UPDATE_POSTED_IMAGE = """
            UPDATE posted_image SET 
                hash_code = ?,
                title = ?, 
                description = ?, 
                credit = ?, 
                capture_date = ?, 
                capture_time = ?, 
                keywords = ?, 
                focal_length = ?, 
                f_number = ?, 
                exposure_time = ?, 
                iso = ?, 
                camera_make = ?, 
                camera_model = ?,
                updated = ?
        """
        private const val FIND_BY_IMAGE_ID = "SELECT * FROM posted_image WHERE image_id = ?"
        private const val INSERT_POSTED_IMAGE = """
            INSERT INTO posted_image (
                image_id, 
                path, 
                album_name, 
                photo_filename, 
                hash_code, 
                title, 
                description, 
                credit, 
                capture_date, 
                capture_time, 
                keywords, 
                focal_length, 
                f_number, 
                exposure_time, 
                iso, 
                camera_make, 
                camera_model, 
                created, 
                updated
            ) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """

        fun createTable() {
            val connection = connect()
            try {
                connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute(CREATE_TABLE_POSTED_IMAGE)
                    }
                }
            } catch (e: SQLException) {
                println(e.message)
            }
        }
    }
}

class StorageException(message: String): RuntimeException(message)
