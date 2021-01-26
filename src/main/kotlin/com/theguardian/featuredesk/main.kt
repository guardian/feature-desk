package com.theguardian.featuredesk

import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


val operatorFunInvokeRegex = Regex("^\\s*operator fun invoke")

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
    val features = listOf(
        FilePathFeature("java_files") { it.endsWith(".java") },
        FilePathFeature("kt_files") { it.endsWith(".kt") },
        LineFeature(
            "synthetics",
            fileFilter = { it.endsWith(".kt") },
            linePredicate = { it.startsWith("import kotlinx.android.synthetic.") }
        )
    )

    val repo = FileRepository(gitDir)

    val revWalk = RevWalk(repo)

    val headCommit = revWalk.parseCommit(repo.resolve("HEAD"))

    println(headCommit.id.toString() + " " + headCommit.shortMessage)

    revWalk.markStart(headCommit)

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
            val featureCounts = features.map { feature ->
                when (feature) {
                    is FilePathFeature -> if (feature.predicate(pathString)) 1 else 0
                    is LineFeature -> if (feature.fileFilter(pathString)) {
                        objectReader.open(blobId).openStream().bufferedReader().useLines { lines ->
                            lines.count(feature.linePredicate)
                        }
                    } else {
                        0
                    }
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
            var totalCounts = IntArray(features.size)
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

    var commits = 0

    val time = measureTime {
        File("output.csv").bufferedWriter().use { output ->
            output.appendln("commit, datetime, ${features.joinToString(", ") { it.id }}")
            revWalk.iterator().forEach { commit ->
                commits += 1
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
            }
        }
    }

    println("Total commits: $commits")
    println("Total time: $time")
    println("Memoized objects: ${memoFeatureCounts.size}")
    println("Tree hits/calcs: $treeHits/$treeCalcs")
    println("Blob hits/calcs: $blobHits/$blobCalcs")
}