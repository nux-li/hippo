package li.nux.hippo

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice

class Hippo : CliktCommand() {
    private val precedence: String by option("-p", "--precedence")
        .choice("front_matter", "image_metadata")
        .default("front_matter")
        .help("If both the Hugo front matter and the image metadata have changed, which one takes precedence?")
    private val format: String by option("-f", "--format")
        .choice("json", "yaml")
        .default("json")
        .help("The format to be used for front matter segment")
    private val directory: String by argument()
        .help("Path to the content directory for your Hugo website project")

    override fun run() {
        echo(appHeader())
        execute(directory, precedence.toPrecedence(), format.toFrontMatterFormat())
    }
}

fun main(args: Array<String>) {
    Hippo().main(args)
}
