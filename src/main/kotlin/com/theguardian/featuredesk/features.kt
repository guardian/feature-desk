package com.theguardian.featuredesk

typealias FilePathPredicate = (String) -> Boolean
typealias LinePredicate = (String) -> Boolean

sealed class Feature(val id: String)
class FilePathFeature(id: String, val predicate: FilePathPredicate) : Feature(id)
class LineFeature(id: String, val fileFilter: FilePathPredicate, val linePredicate: LinePredicate) : Feature(id)