package com.theguardian.featuredesk

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

val classDefinitionRegex = Regex("^\\s*operator fun invoke")

val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(Locale.UK)
        .withZone(ZoneId.from(ZoneOffset.UTC))

/**
 * TODO
 */
fun main(args: Array<out String>) {
    val rootPath = try {
        args[0]
    } catch (_: ArrayIndexOutOfBoundsException) {
        "."
    }

    val features = listOf(
        fileTypeFeature(
            id = "java_files",
            name = "Java files",
            fileExtension = "java"
        ),
        fileTypeFeature(
            id = "kt_files",
            name = "Kotlin files",
            fileExtension = "kt"
        ),
        lineContainsFeature(
            id = "op_invoke",
            name = "Invokable classes",
            regex = Regex("^\\s*operator fun invoke")
        ).preFilter("Only Kotlin files") { file -> file.extension == "kt" }
    )

    val rootDir = File(rootPath)

    val allOccurrences: List<Occurrence> = commits(rootDir)
        .reversed()
        //.filterIndexed { index, _ -> index % 1000 == 0 }
        .flatMap { commit ->
            println(commit)
            checkout(rootDir, commit.hash)
            trackedFilePaths(rootDir)
                .map { path -> File(rootDir, path) }
                .flatMap { file ->
                    features.flatMap { feature ->
                        feature.locate(file)
                            .map { location ->
                                Occurrence(feature, commit, location)
                            }
                    }
                }
        }

    val ocByCommit = allOccurrences.groupBy { it.commit }

    ocByCommit.forEach { (commit, occurrences) ->
        val ocByCommitAndFeature = occurrences.groupBy { it.feature }
        val report = features.joinToString(", ") { feature ->
            val count = ocByCommitAndFeature[feature]?.size ?: 0
            "$count"
        }
        val formattedDateTime = dateTimeFormatter.format(commit.date)
        println("${commit.hash}, $formattedDateTime, $report")
    }

    //allOccurrences.forEach { println(it)  }

    checkout(rootDir, "master")
}

fun commits(directory: File): List<Commit> {
    return readOutput(directory, "git", "log", "--format=format:%H/%aI")
        .readLines()
        .map { logLine ->
            val split = logLine.split('/')
            Commit(hash = split[0], date = OffsetDateTime.parse(split[1]))
        }
}

fun checkout(directory: File, target: String): Int {
    return newProcess(directory, "git", "checkout", "-q", target).waitFor()
}

fun trackedFilePaths(directory: File): List<String> {
    return readOutput(directory, "git", "ls-tree", "-r", "HEAD", "--name-only")
        .readLines()
}

fun readOutput(directory: File, vararg command: String): BufferedReader {
    return newProcess(directory, *command).inputStream.bufferedReader()
}

fun newProcess(directory: File, vararg command: String): Process {
    println("Running command '${command.joinToString(" ")}' in directory '${directory.path}'")
    return ProcessBuilder(*command)
        .directory(directory)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
}