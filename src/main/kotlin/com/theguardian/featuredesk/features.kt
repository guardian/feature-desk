package com.theguardian.featuredesk

typealias FilePathPredicate = (String) -> Boolean

sealed class Feature(val id: String)

class FilePathFeature(id: String, val predicate: FilePathPredicate) : Feature(id)