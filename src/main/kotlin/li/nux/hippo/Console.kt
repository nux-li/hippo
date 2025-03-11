package li.nux.hippo

import java.util.Base64

private const val logo = "IF8gICBfIF8gICAgICAgICAgICAgICAgICAgDQp8IHwgfCAoXykgICAgICAgICAgICAgICAgICANCnwgf" +
    "F98IHxfIF8gX18gIF8gX18gICBfX18gIA0KfCAgXyAgfCB8ICdfIFx8ICdfIFwgLyBfIFwgDQp8IHwgfCB8IHwgfF8pIHwgfF8pIHwg" +
    "KF8pIHwNClxffCB8X3xffCAuX18vfCAuX18vIFxfX18vIA0KICAgICAgICB8IHwgICB8IHwgICAgICAgICAgDQogICAgICAgIHxffCAg" +
    "IHxffCAgICAgICAgICA="
private val name = "SHVnbyBJbWFnZSBQcmVwcm9jZXNzb3I="
private val line = "ICAgICAgICAgICAgICAgICAgICAgICAgICAgIOKAviAgICDigL4gICAgIOKAviAg4oC+ICAgICAg4oC+"

fun appHeader() = String(Base64.getDecoder().decode(logo)) + " " +
    String(Base64.getDecoder().decode(name)) + " ~ " + VersionInfo.version() + "\n" +
    String(Base64.getDecoder().decode(line)) + "\n"

enum class Precedence {
    FRONT_MATTER,
    IMAGE_METADATA
}

enum class FrontMatterFormat {
    JSON,
    TOML,
    YAML
}

fun String.toPrecedence(): Precedence {
    return Precedence.valueOf(this.uppercase())
}

fun String.toFrontMatterFormat(): FrontMatterFormat {
    return FrontMatterFormat.valueOf(this.uppercase())
}
