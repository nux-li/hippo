package li.nux.hippo

import java.nio.file.Path
import java.nio.file.Paths
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice

class Hippo : CliktCommand() {
    private val changeStrategy: String by option("-c", "--changes")
        .choice("front_matter", "image_metadata", "both")
        .default("both")
        .help("From which source should changes be accepted?")
    private val precedence: String by option("-p", "--precedence")
        .choice("front_matter", "image_metadata")
        .default("front_matter")
        .help("If both the Hugo front matter and the image metadata have changed, which one takes precedence?")
    private val format: String by option("-f", "--format")
        .choice("json", "toml", "yaml")
        .default("toml")
        .help("The format to be used for front matter segment")
    private val directory: String by argument()
        .help("Path to the content directory for your Hugo website project")

    override fun run() {
        echo(appHeader())
        directoryIsCorrect(directory)?.let { path ->
            init()
            execute(
                path,
                HippoParams(
                    changeStrategy.toChangeAcceptance(),
                    precedence.toPrecedence(),
                    format.toFrontMatterFormat(),
                )
            )
        } ?: {
            println(
                "Parameter was $directory. It should have been the path of the Hugo content folder. No changes done."
            )
        }
    }

    private fun directoryIsCorrect(directory: String): Path? {
        val path: Path = Paths.get(directory)
        val name = path.fileName.toString()
        return if (name == "content") path else null
    }
}

fun main(args: Array<String>) {
    Hippo().main(args)
}
