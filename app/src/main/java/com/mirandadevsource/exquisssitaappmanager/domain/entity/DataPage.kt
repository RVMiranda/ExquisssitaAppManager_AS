package com.mirandadevsource.exquisssitaappmanager.domain.entity

import java.time.Instant

/** Offset request used by feature repositories while UI list contracts migrate to cursors. */
data class PageRequest(val offset: Int = 0, val limit: Int = DEFAULT_LIMIT) {
    init {
        require(offset >= 0) { "El desplazamiento no puede ser negativo." }
        require(limit in 1..MAX_LIMIT) { "El tamaño de página debe estar entre 1 y $MAX_LIMIT." }
        require(offset <= Int.MAX_VALUE - limit - 1) { "El rango de página excede el límite permitido." }
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 100
    }
}

data class DataPage<T>(
    val items: List<T>,
    val nextOffset: Int?,
    val hasMore: Boolean,
    val scope: String,
    /** Null means the local cache has no recorded remote fetch timestamp yet. */
    val fetchedAt: Instant?,
) {
    val isLastPage: Boolean get() = !hasMore && nextOffset == null
}

fun <T> pageFromFetchedWindow(items: List<T>, request: PageRequest, scope: String, fetchedAt: Instant? = Instant.now()): DataPage<T> {
    val hasMore = items.size > request.limit
    val pageItems = items.take(request.limit)
    val nextOffset = if (hasMore) request.offset + pageItems.size else null
    return DataPage(pageItems, nextOffset, hasMore, scope, fetchedAt)
}

fun <T> pageFromCompleteList(items: List<T>, request: PageRequest, scope: String, fetchedAt: Instant? = Instant.now()): DataPage<T> {
    val window = items.drop(request.offset).take(request.limit + 1)
    val hasMore = window.size > request.limit
    val pageItems = window.take(request.limit)
    return DataPage(pageItems, if (hasMore) request.offset + pageItems.size else null, hasMore, scope, fetchedAt)
}
