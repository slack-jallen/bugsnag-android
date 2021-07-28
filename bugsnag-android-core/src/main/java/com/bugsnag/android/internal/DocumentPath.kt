package com.bugsnag.android.internal

import java.lang.IllegalArgumentException

/**
 * DocumentPath records a path into a hierarchical acyclic document, and can be used to modify
 * documents by navigating down the DAG and then applying a modification at that level.
 *
 * Paths follow a dotted notation similar to internet hostnames, such that each path component
 * is separated by a dot '.' If a literal dot is required, use an escape sequence: A backslash
 * character causes the parser to NOT treat the next character specially (regardless of whether
 * it's a special character or not):
 *
 * \. = literal dot
 * \\ = literal backslash
 * \+ = literal plus
 * \z = literal z
 *
 * Path components are either integers or non-integers. Integers refer to the previous path
 * component as a list, providing the index to look up. Non-integers refer to the previous path
 * component as a map, providing the key to look up.
 *
 * For example: the path "foo.bar.3" refers to document root (always a map) with map key "foo",
 * drilled down to map key "bar", drilled down to list index 3. i.e.
 * {
 *     "foo": {
 *         "bar": [
 *             "we expect something to be here (index 0)",
 *             "we expect something to be here (index 1)",
 *             "we expect something to be here (index 2)",
 *             "the thing we're actually interested in (index 3)"
 *         ]
 *     }
 * }
 *
 * Any nonexistent parts of the document are created on-demand as they are encountered while
 * resolving paths (either as a list in the case of integer path components, or as a map for
 * non-integers). In the case of lists, only index 0 of a nonexistent list is valid (in which
 * case the list will be created, and the value stored at index 0).
 *
 * As well, the following special rules apply:
 *
 * - An empty path component refers to the document root, and causes the value to replace
 * the entire document.
 * - The integer path component -1 refers to the last index in the array, or index 0 if the
 * array is empty or nonexistent.
 * - If a path ends in an unescaped dot (e.g. events.exceptions.stacktrace.), it means that the
 * last path entry (e.g. stacktrace) is to be treated as a list, and the value is to be appended
 * to that list.
 * - If a path ends in an unescaped plus (e.g. session.events.handled+), it means that the value
 * must be ADDED to any existing value (or inserted if no value exists yet). Value must be numeric.
 *
 * The document to be modified must be serializable into JSON. The following types are supported:
 * - null
 * - boolean
 * - integer (max 15 digits)
 * - float
 * - string
 * - List(Object)
 * - Map(String, Object)
 */
class DocumentPath(path: String) {
    private val directives: List<DocumentPathDirective<Any>> = toPathDirectives(path)

    /**
     * Modify a document, setting this path in the document to the specified value.
     *
     * @param document The document to modify.
     * @param value    The value to modify with.
     * @return The modified document (may not be the same document that was passed in).
     */
    fun modifyDocument(document: MutableMap<in String, out Any>, value: Any?): MutableMap<String, Any> {
        if (directives.isEmpty()) {
            val concurrentValue = value.asConcurrent()
            require(concurrentValue is MutableMap<*, *>) { "Value replacing document must be a map" }
            @Suppress("UNCHECKED_CAST")
            return concurrentValue as MutableMap<String, Any>
        }
        val filledInDocument = fillInMissingContainers(document, 0)
        require(filledInDocument is MutableMap<*, *>) { "Document path must result in a top level map" }
        @Suppress("UNCHECKED_CAST")
        val updatedDocument = filledInDocument as MutableMap<String, Any>

        applyValueToDocument(value, updatedDocument)
        return updatedDocument
    }

    /**
     * Fill in the document parts that will be navigated, but don't exist yet.
     *
     * For example, the path "foo.bar.1.-1" requires a document consisting of:
     * - Map with key "foo" containing:
     * - Map with key "bar" containing:
     * - List with index 1 present, containing:
     * - List where the last index is what we want to modify.
     *
     * Before we can modify the last index, we need to create all of the parent
     * containers (if they don't exist yet). This function recursively descends
     * the document, creating all necessary containers to make this path navigatable.
     *
     * @param parent The parent container (which might be null if it doesn't exist yet)
     * @param index The index of the directive to use if a new container is necessary.
     * @return The container that should be in place of the parent passed in (a list or a map).
     */
    private fun fillInMissingContainers(parent: Any?, index: Int): Any {
        val directive = directives[index]
        val updatedParent = parent ?: directive.newContainer()

        if (index + 1 < directives.size) {
            val result =
                fillInMissingContainers(directive.getFromContainer(updatedParent), index + 1)
            directive.setInContainer(updatedParent, result)
        }
        return updatedParent
    }

    /**
     * Navigate down into a document's sub-containers, then apply the specified value.
     *
     * @param value The value to apply (null means delete)
     * @param document the document to apply this value to.
     */
    private fun applyValueToDocument(value: Any?, document: MutableMap<String, Any>) {
        // Directives 0 through n-1 navigate to where we want to edit the document.
        var currentContainer: Any = document
        for (i in 0 until directives.size - 1) {
            val directive = directives[i]
            currentContainer = directive.getFromContainer(currentContainer)!!
        }

        // Only the last directive does the actual setting.
        val directive = directives.last()
        directive.setInContainer(currentContainer, value)
    }

    companion object {

        private const val ESCAPE_CHAR = '\\'
        private const val PATH_SEPARATOR = '.'
        private const val ADD_OPERATOR = '+'

        private enum class PathType {
            PATH_TYPE_NORMAL,
            PATH_TYPE_LIST_INSERT,
            PATH_TYPE_ADD,
        }

        /**
         * Generate path directives from a path string.
         */
        internal fun toPathDirectives(path: String): List<DocumentPathDirective<Any>> {
            if (path.isEmpty()) {
                return emptyList()
            }
            val pathType = getPathType(path)
            val trimmedPath = trimPath(path, pathType)
            val directives = ArrayList<DocumentPathDirective<*>>()
            var requiresEscaping = false
            var strBegin = 0

            val buildPathComponent = fun(endIndex: Int): String {
                return when {
                    requiresEscaping -> unescape(trimmedPath, strBegin, endIndex)
                    else -> trimmedPath.substring(strBegin, endIndex)
                }
            }

            var i = 0
            while (i < trimmedPath.length) {
                when (trimmedPath[i]) {
                    ESCAPE_CHAR -> {
                        requiresEscaping = true
                        i++
                    }
                    PATH_SEPARATOR -> {
                        directives.add(makeDirectiveFromPathComponent(buildPathComponent(i)))
                        strBegin = i + 1
                        requiresEscaping = false
                    }
                    else -> {
                    }
                }
                i++
            }
            if (strBegin < trimmedPath.length) {
                val pathComponent = buildPathComponent(trimmedPath.length)
                val directive = when (pathType) {
                    PathType.PATH_TYPE_ADD -> makeAddDirectiveFromPathComponent(pathComponent)
                    else -> makeDirectiveFromPathComponent(pathComponent)
                }
                directives.add(directive)
            }

            if (pathType == PathType.PATH_TYPE_LIST_INSERT) {
                directives.add(DocumentPathDirective.ListInsertDirective())
            }

            @Suppress("UNCHECKED_CAST")
            return directives as MutableList<DocumentPathDirective<Any>>
        }

        /**
         * Unescape a string.
         */
        private fun unescape(str: String, startOffset: Int, endOffset: Int): String {
            val buff = StringBuilder(endOffset - startOffset)
            var i = startOffset
            while (i < endOffset) {
                val ch = str[i]
                if (ch == ESCAPE_CHAR) {
                    i++
                    require(i < endOffset) { "Path cannot end on an escape char '\\'" }
                    buff.append(str[i])
                } else {
                    buff.append(ch)
                }
                i++
            }
            return buff.toString()
        }

        private fun getPathType(path: String): PathType {
            if (path.length == 0) {
                return PathType.PATH_TYPE_NORMAL
            }

            if (path.length > 1 && path[path.lastIndex - 1] == ESCAPE_CHAR) {
                return PathType.PATH_TYPE_NORMAL
            }

            return when (path[path.lastIndex]) {
                PATH_SEPARATOR -> PathType.PATH_TYPE_LIST_INSERT
                ADD_OPERATOR -> PathType.PATH_TYPE_ADD
                else -> PathType.PATH_TYPE_NORMAL
            }
        }

        private fun trimPath(path: String, pathType: PathType): String {
            val trimmedPath = when (pathType) {
                PathType.PATH_TYPE_ADD -> path.substring(0, path.lastIndex)
                else -> path
            }
            if (path.length != trimmedPath.length) {
                if (trimmedPath.isEmpty()) {
                    throw IllegalArgumentException("Path component cannot consist solely of an operator")
                }
                if (trimmedPath[trimmedPath.lastIndex] == PATH_SEPARATOR) {
                    if (!(trimmedPath.length > 1 && trimmedPath[trimmedPath.lastIndex - 1] == ESCAPE_CHAR)) {
                        throw IllegalArgumentException("Path component cannot consist solely of an operator")
                    }
                }
            }
            return trimmedPath
        }

        /**
         * Generate a path directive based on the data type in a path component:
         *
         * - String: Map key directive
         * - Integer: List index directive
         * - Value -1: Last list index directive
         */
        private fun makeDirectiveFromPathComponent(pathComponent: String): DocumentPathDirective<out Any> {
            require(pathComponent.isNotEmpty(), { "Path component cannot be empty" })

            val index = pathComponent.toIntOrNull()
            if (index != null) {
                return if (index == -1) {
                    DocumentPathDirective.ListLastIndexDirective()
                } else {
                    DocumentPathDirective.ListIndexDirective(index)
                }
            }
            return DocumentPathDirective.MapKeyDirective(pathComponent)
        }

        private fun makeAddDirectiveFromPathComponent(pathComponent: String): DocumentPathDirective<out Any> {
            require(pathComponent.isNotEmpty(), { "Path component cannot be empty" })

            val index = pathComponent.toIntOrNull()
            if (index != null) {
                return if (index == -1) {
                    DocumentPathDirective.ListLastIndexAddDirective()
                } else {
                    DocumentPathDirective.ListIndexAddDirective(index)
                }
            }
            return DocumentPathDirective.MapKeyAddDirective(pathComponent)
        }
    }
}