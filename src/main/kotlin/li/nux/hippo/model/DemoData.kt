package li.nux.hippo.model

import kotlinx.serialization.Serializable

@Serializable
data class DemoData(
    val images: List<DemoImage>,
    val keywords: List<String>,
    val cameraMakers: List<String>,
    val cameraModels: List<String>,
)

@Serializable
data class DemoImage(
    val url: String,
    val credit: String,
)
