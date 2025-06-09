package li.nux.hippo.helpers

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import li.nux.hippo.HugoPaths

private const val TAXONOMY_SEARCH_STRING = "[taxonomies]"
private const val TAXONOMY_CONFIG =
    "\n[taxonomies]\n  tag = \"tags\"\n  category = \"categories\"\n  " +
        "keyword = \"keywords\"\n  year = \"year\"\n  equipment = \"equipment\""

fun addTaxonomiesToHugoConfig(hugoPaths: HugoPaths) {
    val file = Paths.get(hugoPaths.root.toAbsolutePath().toString() + File.separator + "hugo.toml")
    val linesInConfigFile = Files.readAllLines(file)
    if (linesInConfigFile.none { it.contains(TAXONOMY_SEARCH_STRING) }) {
        val newConfigLines = mutableListOf<String>()
        newConfigLines.addAll(linesInConfigFile)
        newConfigLines.addAll(TAXONOMY_CONFIG.split("\n"))
        Files.write(file, newConfigLines)
    }
}