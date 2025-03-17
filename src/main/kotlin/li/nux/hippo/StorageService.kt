package li.nux.hippo

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.Date
import li.nux.hippo.ImageMetadata.Companion.fromResultSet
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
    @Deprecated("To be removed")
    fun exists(imageId: String): ImageMetadata? {
        val connection = connect()
        val result = try {
            connection.prepareStatement(FIND_BY_IMAGE_ID).use { prepped ->
                prepped.setString(1, imageId)
                val resultSet = prepped.executeQuery()
                while (resultSet.next()) {
                    return fromResultSet(resultSet)
                }
                null
            }
        } catch (e: SQLException) {
            System.err.println(e.message)
            null
        }
        return result
    }

    fun fetchAllImages(): List<ImageMetadata> {
        val images = mutableListOf<ImageMetadata>()
        val connection = connect()
        try {
            connection.createStatement().use { statement ->
                statement.executeQuery(FETCH_ALL).use { resultSet ->
                    getImagesFromResultSet(resultSet, images)
                }
            }
        } catch (e: SQLException) {
            log.error("Failed to fetch images: {}", e.message)
            throw StorageException("Failed to fetch images")
        }
        return images
    }

    private fun getImagesFromResultSet(resultSet: ResultSet, images: MutableList<ImageMetadata>) {
        while (resultSet.next()) {
            images.add(
                fromResultSet(resultSet)
            )
        }
    }

    fun updatePostedImage(id: Int, img: ImageMetadata) {
        val connection = connect()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        var ndx = 0
        try {
            connection.prepareStatement(UPDATE_POSTED_IMAGE).use { prepped ->
                prepped.setInt(ndx++, img.hashCode())
                prepped.setString(ndx++, img.title)
                prepped.setString(ndx++, img.description)
                prepped.setString(ndx++, img.credit)
                prepped.setString(ndx++, img.captureDate)
                prepped.setString(ndx++, img.captureTime)
                prepped.setString(ndx++, java.lang.String.join(", ", img.keywords))
                prepped.setString(ndx++, img.exposureDetails?.focalLength)
                prepped.setString(ndx++, img.exposureDetails?.aperture)
                prepped.setString(ndx++, img.exposureDetails?.exposureTime)
                prepped.setString(ndx++, img.exposureDetails?.iso)
                prepped.setString(ndx++, img.exposureDetails?.cameraMake)
                prepped.setString(ndx++, img.exposureDetails?.cameraModel)
                prepped.setString(ndx++, now)
                prepped.setInt(ndx++, id)
                prepped.executeUpdate()
            }
        } catch (e: SQLException) {
            log.error("Failed to update posted image with id ${img.id}: {}", e.message)
            throw StorageException("Failed to update posted image with id ${img.id}")
        } finally {
            log.trace("[$ndx] updated posted image with id ${img.id}")
        }
    }

    fun insertPostedImage(img: ImageMetadata): Int {
        val connection = connect()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        var ndx = 0
        try {
            connection.prepareStatement(INSERT_POSTED_IMAGE).use { prepped ->
                prepped.setString(ndx++, img.getReference())
                prepped.setString(ndx++, img.path)
                prepped.setString(ndx++, img.album)
                prepped.setString(ndx++, img.filename)
                prepped.setInt(ndx++, img.hashCode())
                prepped.setString(ndx++, img.title)
                prepped.setString(ndx++, img.description)
                prepped.setString(ndx++, img.credit)
                prepped.setString(ndx++, img.captureDate)
                prepped.setString(ndx++, img.captureTime)
                prepped.setString(ndx++, java.lang.String.join(", ", img.keywords))
                prepped.setString(ndx++, img.exposureDetails?.focalLength)
                prepped.setString(ndx++, img.exposureDetails?.aperture)
                prepped.setString(ndx++, img.exposureDetails?.exposureTime)
                prepped.setString(ndx++, img.exposureDetails?.iso)
                prepped.setString(ndx++, img.exposureDetails?.cameraMake)
                prepped.setString(ndx++, img.exposureDetails?.cameraModel)
                prepped.setString(ndx++, now)
                prepped.setString(ndx++, now)
                prepped.executeUpdate()
                return prepped.generatedKeys.getInt(1)
            }
        } catch (e: SQLException) {
            System.err.println(e.message)
            return -1
        } finally {
            log.trace("[$ndx] inserted new posted image with reference ${img.getReference()}")
        }
    }

    fun removePostedImage(id: Int, imageId: String) {
        val connection = connect()
        try {
            connection.prepareStatement(DELETE_POSTED_IMAGE).use { prepped ->
                prepped.setInt(1, id)
                prepped.setString(1, imageId)
                prepped.executeUpdate()
            }
        } catch (e: SQLException) {
            log.error("Failed to delete posted image with id $id: {}", e.message)
            throw StorageException("Failed to delete posted image with id $id")
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
            WHERE id = ?
        """
        private const val FIND_BY_IMAGE_ID = "SELECT * FROM posted_image WHERE image_id = ?"
        private const val FETCH_ALL = "SELECT * FROM posted_image ORDER BY image_id"
        private const val DELETE_POSTED_IMAGE = "DELETE FROM posted_image where id = ? and image_id = ?"
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
