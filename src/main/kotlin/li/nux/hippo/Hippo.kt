package li.nux.hippo

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice

//import com.github.ajalt.clikt.parameters.options.prompt

class Hippo : CliktCommand() {
    val precedence: String by option("-p", "--precedence")
        .choice("front_matter", "image_metadata")
        .default("front_matter")
        .help("If both the Hugo front matter and the image metadata have changed, which one takes precedence?")
    val directory: String by argument()
        .help("Path to the content directory for your Hugo website project")

    override fun run() {
        echo(hippoAppHeader)
        execute(directory, precedence.toPrecedence())
    }
}

fun main(args: Array<String>) {
    Hippo().main(args)
}

enum class Precedence {
    FRONT_MATTER,
    IMAGE_METADATA
}

fun String.toPrecedence(): Precedence {
    return Precedence.valueOf(this.uppercase())
}