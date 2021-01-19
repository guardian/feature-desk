package com.theguardian.featuredesk

import java.io.File
import java.io.IOException
import java.time.OffsetDateTime

typealias FeatureLocator = (File) -> List<Location>

class Feature(
    val id: String,
    val name: String,
    val description: String,
    val locate: FeatureLocator
) {
    override fun toString() = id
}

typealias FilePathPredicate = (String) -> Boolean
sealed class Feature2(val id: String)
class FilePathFeature(id: String, val predicate: FilePathPredicate) : Feature2(id)

data class Commit(val hash: String, val date: OffsetDateTime)

data class Location(val file: File, val line: Int? = null) {
    override fun toString() = "$file" + (line?.let { "#L$it" } ?: "")
}

data class Occurrence(val feature: Feature, val commit: Commit, val location: Location)

typealias FilePredicate = (File) -> Boolean

fun fileFeature(id: String, name: String, description: String, predicate: FilePredicate) =
    Feature(id, name, description) { file ->
        if (predicate(file)) {
            listOf(Location(file))
        } else {
            emptyList()
        }
    }

fun fileTypeFeature(id: String, name: String, fileExtension: String) =
    fileFeature(id, name, description = "Files with a '.$fileExtension' extension") { file ->
        file.extension == fileExtension
    }

typealias LinePredicate = (String) -> Boolean

@OptIn(ExperimentalStdlibApi::class)
fun lineFeature(id: String, name: String, description: String, predicate: LinePredicate) =
    Feature(id, name, description) { file ->
        try {
            var lineNumber = 1
            buildList {
                file.forEachLine { line ->
                    if (predicate(line)) {
                        add(Location(file, lineNumber + 1))
                    }
                    lineNumber += 1
                }
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

fun lineContainsFeature(id: String, name: String, what: String, ignoreCase: Boolean = false) =
    lineFeature(id, name, description = "Lines containing the string '$what'") { line ->
        line.contains(what, ignoreCase)
    }

fun lineContainsFeature(id: String, name: String, regex: Regex) =
    lineFeature(id, name, description = "Lines containing a match for the regular expression '$regex'") { line ->
        regex.containsMatchIn(line)
    }

fun Feature.preFilter(filterDescription: String, fileFilter: FilePredicate) =
    Feature(this.id, this.name, "${this.description} (filtered: $filterDescription)") { file ->
        if (fileFilter(file)) {
            this.locate(file)
        } else {
            emptyList()
        }
    }