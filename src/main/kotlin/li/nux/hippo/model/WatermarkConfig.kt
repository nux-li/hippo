package li.nux.hippo.model

import java.io.FileNotFoundException
import kotlin.io.path.exists
import li.nux.hippo.HugoPaths
import net.coobird.thumbnailator.geometry.Positions

data class WatermarkConfig(
    val watermarkPlacement: WatermarkPlacement,
    val watermarkPath: String,
    val subtleWatermarkPath: String,
) {
    fun getWatermarkSize(fileName: String, w: Int): Int {
        val factor = if (fileName == watermarkPath) FACTOR_5 else FACTOR_4
        return if (w < WATERMARK_LOWER_THRESHOLD) WATERMARK_MIN_WIDTH else w / factor
    }

    companion object {
        const val WATERMARK_LOWER_THRESHOLD = 1000
        const val WATERMARK_MIN_WIDTH = 200
        const val WATERMARK_BLC_OPACITY = 0.6f
        const val WATERMARK_CTR_OPACITY = 0.05f
        private const val FACTOR_4 = 4
        private const val FACTOR_5 = 5

        fun from(placement: String, hugoPaths: HugoPaths): WatermarkConfig? {
            try {
                return WatermarkConfig(
                    watermarkPlacement = WatermarkPlacement.valueOf(placement),
                    watermarkPath = getMainWatermark(hugoPaths),
                    subtleWatermarkPath = getSubtleWatermark(hugoPaths),
                )
            } catch (e: FileNotFoundException) {
                println("${e.message}")
                return null
            }
        }

        private fun getSubtleWatermark(hugoPaths: HugoPaths): String {
            val path = hugoPaths.assets.resolve("watermarks").resolve("watermark_subtle.png")
             if (path.exists()) {
                 return path.toAbsolutePath().toString()
             } else {
                 throw FileNotFoundException("Watermark file does not exist: " + path.toAbsolutePath().toString())
            }
        }

        private fun getMainWatermark(hugoPaths: HugoPaths) =
            hugoPaths.assets.resolve("watermarks").resolve("watermark_main.png").toAbsolutePath().toString()
    }
}

enum class WatermarkPlacement(
    val positions: Positions,
    val description: String,
    val additionalCenterWatermark: Boolean = false
) {
    UL(Positions.TOP_LEFT, "Watermark in upper left corner"),
    UR(Positions.TOP_RIGHT, "Watermark in upper right corner"),
    LL(Positions.BOTTOM_LEFT, "Watermark in lower left corner"),
    LR(Positions.BOTTOM_RIGHT, "Watermark in lower left corner"),
    UL_C(Positions.TOP_LEFT, "Watermark in upper left corner, with additional subtle center watermark", true),
    UR_C(Positions.TOP_RIGHT, "Watermark in upper right corner, with additional subtle center watermark",true),
    LL_C(Positions.BOTTOM_LEFT, "Watermark in lower left corner, with additional subtle center watermark",true),
    LR_C(Positions.BOTTOM_RIGHT, "Watermark in lower left corner, with additional subtle center watermark", true);

    companion object {
        fun getDescriptions(): String {
            return entries.joinToString("\n") { "${it.name} --> ${it.description}" }
        }
    }
}