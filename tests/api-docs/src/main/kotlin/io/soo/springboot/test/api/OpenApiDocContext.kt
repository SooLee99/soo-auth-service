package io.soo.springboot.test.api

import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.request.ParameterDescriptor

internal object OpenApiDocContext {
    private val local = ThreadLocal.withInitial { Bag() }

    internal data class Snapshot(
        val headers: List<HeaderDescriptor>,
        val pathParams: List<ParameterDescriptor>,
        val queryParams: List<ParameterDescriptor>,
        val requestFields: List<FieldDescriptor>,
        val responseFields: List<FieldDescriptor>,
    )

    private class Bag {
        val headers = mutableListOf<HeaderDescriptor>()
        val pathParams = mutableListOf<ParameterDescriptor>()
        val queryParams = mutableListOf<ParameterDescriptor>()
        val requestFields = mutableListOf<FieldDescriptor>()
        val responseFields = mutableListOf<FieldDescriptor>()
        fun clear() {
            headers.clear()
            pathParams.clear()
            queryParams.clear()
            requestFields.clear()
            responseFields.clear()
        }
    }

    fun addHeaders(list: List<HeaderDescriptor>) = local.get().headers.addAll(list)
    fun addPathParams(list: List<ParameterDescriptor>) = local.get().pathParams.addAll(list)
    fun addQueryParams(list: List<ParameterDescriptor>) = local.get().queryParams.addAll(list)
    fun addRequestFields(list: List<FieldDescriptor>) = local.get().requestFields.addAll(list)
    fun addResponseFields(list: List<FieldDescriptor>) = local.get().responseFields.addAll(list)

    fun consume(): Snapshot {
        val bag = local.get()
        val snapshot = Snapshot(
            headers = bag.headers.toList(),
            pathParams = bag.pathParams.toList(),
            queryParams = bag.queryParams.toList(),
            requestFields = bag.requestFields.toList(),
            responseFields = bag.responseFields.toList(),
        )
        bag.clear()
        return snapshot
    }
}