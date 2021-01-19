package com.theguardian.featuredesk

import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.BufferedReader
import java.io.File
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
    val gitDir = try {
        args[0]
    } catch (_: ArrayIndexOutOfBoundsException) {
        "."
    }

    /*
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
    */
    val features2 = listOf<Feature2>(
        FilePathFeature("java_files") { it.endsWith(".java") },
        FilePathFeature("kt_files") { it.endsWith(".kt") }
    )

    val repo = FileRepository(gitDir)

    val revWalk = RevWalk(repo)

    val headCommit = revWalk.parseCommit(repo.resolve("HEAD"))

    println(headCommit.id.toString() + " " + headCommit.shortMessage)

    revWalk.markStart(headCommit)
    var totalObjects = 0
    val uniqueObjectIds = mutableSetOf<String>()

    val memoFeatureCounts = mutableMapOf<ObjectId, IntArray>()

    fun IntArray.zipSum(other: IntArray): IntArray {
        return IntArray(size) { pos ->
            this[pos] + other[pos]
        }
    }

    fun countFeaturesInBlob(pathString: String, blobId: ObjectId, objectReader: ObjectReader): IntArray {
        return memoFeatureCounts.getOrElse(blobId) {
            // objectReader.open(blobId).openStream().bufferedReader()
            return features2.map { feature ->
                when (feature) {
                    is FilePathFeature -> if (feature.predicate(pathString)) 1 else 0
                }
            }.toIntArray()
        }
    }

    fun countFeaturesInTree(treeId: ObjectId): IntArray {
        return memoFeatureCounts.getOrElse(treeId) {
            var totalCounts = IntArray(features2.size)
            TreeWalk(repo).apply {
                isRecursive = false
                addTree(treeId)
                while (next()) {
                    val itemCounts: IntArray = if (isSubtree) {
                        countFeaturesInTree(getObjectId(0))
                    } else {
                        countFeaturesInBlob(pathString, getObjectId(0), objectReader)
                    }
                    totalCounts = totalCounts.zipSum(itemCounts)
                }
            }
            return totalCounts
        }
    }

    val commitFeatureCounts = mutableMapOf<RevCommit, IntArray>()

    revWalk.iterator().forEach { commit ->
        commitFeatureCounts[commit] = countFeaturesInTree(commit.tree)
    }

    commitFeatureCounts.forEach { (revCommit, counts) ->
        println("${revCommit.id}, ${counts.joinToString(", ")}")
    }

    println("Total objects: $totalObjects")
    println("Unique objects: ${uniqueObjectIds.size}")


    /*

        println(it.id.toString() + " " + it.shortMessage)

    }
     */

    /*
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
    */
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