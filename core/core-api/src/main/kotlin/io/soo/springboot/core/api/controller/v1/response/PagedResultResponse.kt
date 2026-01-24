package io.soo.springboot.core.api.controller.v1.response

import org.springframework.data.domain.Page

data class PagedResult<T>(
    val items: List<T>,
    val page: PageMetaDto,
)

data class PageMetaDto(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

fun Page<*>.toPageMetaDto(): PageMetaDto =
    PageMetaDto(
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        hasNext = hasNext(),
        hasPrevious = hasPrevious(),
    )
