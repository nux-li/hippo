package li.nux.hippo

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import com.github.ajalt.clikt.parameters.types.choice
import li.nux.hippo.model.HugoSubfolder

class Hippo : CliktCommand() {
    private val changeStrategy: String by option("-c", "--changes")
        .choice("front_matter", "image_metadata", "both")
        .default("both")
        .help("From which source should changes be accepted?")
    private val demo: String? by option("-d", "--demo")
        .optionalValue("true")
        .default("false")
        .help("If specified demo photos will be used")
    private val format: String by option("-f", "--format")
        .choice("json", "yaml") // , "toml"
        .default("yaml")
        .help("The format to be used for front matter segment")
    private val precedence: String by option("-p", "--precedence")
        .choice("front_matter", "image_metadata")
        .default("front_matter")
        .help("If both the Hugo front matter and the image metadata have changed, which one takes precedence?")
    private val watermark: String? by option("-w", "--watermark")
        .help("Specify this to add a watermark to the photos")
    private val verbose: String by option("--verbose")
        .optionalValue("true")
        .default("false")
        .help("Log detailed information")
    private val directory: String by argument()
        .help("Path to the content directory for your Hugo website project")

    override fun run() {
        echo(appHeader())
        val params = HippoParams(
            changeAcceptance = changeStrategy.toChangeAcceptance(),
            precedence = precedence.toPrecedence(),
            frontMatterFormat = format.toFrontMatterFormat(),
            watermark = watermark,
            verbose = verbose.toBoolean(),
            demo = demo.toBoolean(),
            contentDirectory = directory,
        )
        isHugoSiteDirectory(directory)?.let { paths ->
            init()
            printIf(params, "HugoPaths: $paths")
            execute(
                paths,
                params,
            )
        } ?: {
            println(
                "Parameter was $directory. It should have been the path of the Hugo content folder. No changes done."
            )
        }
    }

    private fun isHugoSiteDirectory(directory: String): HugoPaths? {
        val path: Path = Paths.get(directory)
        val subfolders = Files.walk(path, 1)
            .filter(Files::isDirectory)
            .collect(Collectors.toList())
            .map { it.fileName.toString() }
        return if (HugoSubfolder.entries.map { it.folderName }.map(subfolders::contains).none { false }) {
            HugoPaths(
                root = path.toRealPath(LinkOption.NOFOLLOW_LINKS),
                content = Paths.get(path.toString() + File.separator + HugoSubfolder.CONTENT.folderName)
                    .toRealPath(LinkOption.NOFOLLOW_LINKS),
                assets = Paths.get(path.toString() + File.separator + HugoSubfolder.ASSETS.folderName)
                    .toRealPath(LinkOption.NOFOLLOW_LINKS),
                theme = Paths.get("").toAbsolutePath(),
            )
        } else {
            null
        }
    }
}

fun main(args: Array<String>) {
    Hippo().main(args)
}
