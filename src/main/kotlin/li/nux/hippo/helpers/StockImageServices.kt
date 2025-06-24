package li.nux.hippo.helpers

enum class StockImageServices(
    val urlPart: String,
    val displayName: String,
) {
    ADOBE("stock.adobe.com/", "Adobe Stock"),
    ALAMY("alamy.com", "Alamy"),
    DEPOSITPHOTOS("depositphotos.com", "Depositphotos"),
    DREAMSTIME("dreamstime.com", "Dreamstime"),
    GETTY_IMAGES("gettyimages", "Getty Images"),
    ISTOCK("istockphoto.com", "iStock by Getty Images"),
    MOSTPHOTOS("mostphotos.com", "Mostphotos"),
    PICFAIR("picfair.com", "Picfair"),
    POND5("pond5.com", "Pond5"),
    SHUTTERSTOCK("shutterstock.com", "Shutterstock"),
    STOCKSY("stocksy.com", "Stocksy");

    companion object {
        fun fromUrl(url: String): StockImageServices? {
            return entries.find { url.contains(it.urlPart) }
        }
    }
}