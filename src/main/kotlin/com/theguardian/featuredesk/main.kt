package com.theguardian.featuredesk

import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.BufferedReader
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


val classDefinitionRegex = Regex("^\\s*operator fun invoke")

val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(Locale.UK)
        .withZone(ZoneId.from(ZoneOffset.UTC))

/**
 * TODO
 */
@ExperimentalTime
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

    var treeHits = 0
    var treeCalcs = 0
    var blobHits = 0
    var blobCalcs = 0

    val memoFeatureCounts = mutableMapOf<ObjectId, IntArray>()

    fun IntArray.zipSum(other: IntArray): IntArray {
        return IntArray(size) { pos ->
            this[pos] + other[pos]
        }
    }

    fun countFeaturesInBlob(pathString: String, blobId: ObjectId, objectReader: ObjectReader): IntArray {
        return if (blobId in memoFeatureCounts) {
            blobHits += 1
            memoFeatureCounts[blobId]!!
        } else {
            blobCalcs += 1
            val featureCounts = features2.map { feature ->
                when (feature) {
                    is FilePathFeature -> if (feature.predicate(pathString)) 1 else 0
                }
            }.toIntArray()
            memoFeatureCounts[blobId] = featureCounts
            featureCounts
        }
    }

    fun countFeaturesInTree(treeId: ObjectId): IntArray {
        return if (treeId in memoFeatureCounts) {
            treeHits += 1
            memoFeatureCounts[treeId]!!
        } else {
            treeCalcs += 1
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
            memoFeatureCounts[treeId] = totalCounts
            totalCounts
        }
    }

    val commitFeatureCounts = mutableMapOf<RevCommit, IntArray>()

    val time = measureTime {
        File("output.csv").bufferedWriter().use { output ->
            output.appendln("commit, datetime, ${features2.joinToString(", ") { it.id }}")
            revWalk.iterator().forEach { commit ->
                val dateTime = commit.authorIdent.run {
                    ZonedDateTime.ofInstant(`when`.toInstant(), timeZone.toZoneId())
                }
                output.appendln(
                    "${commit.id.name}, ${dateTime.format(dateTimeFormatter)}, ${
                        countFeaturesInTree(commit.tree).joinToString(
                            ", "
                        )
                    }"
                )
                //commitFeatureCounts[commit] = countFeaturesInTree(commit.tree)
            }
        }
    }

    //commitFeatureCounts.forEach { (revCommit, counts) ->
    //    println("${revCommit.id}, ${counts.joinToString(", ")}")
    //}

    println("Total time: $time")
    println("Memoized objects: ${memoFeatureCounts.size}")
    println("Tree hits/calcs: $treeHits/$treeCalcs")
    println("Blob hits/calcs: $blobHits/$blobCalcs")


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