package io.soo.springboot.test.api

import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.headers.HeaderDocumentation
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.restdocs.snippet.Snippet

// ✅ requestHeaders
fun requestHeaders(vararg descriptors: HeaderDescriptor): Snippet {
    OpenApiDocContext.addHeaders(descriptors.toList())
    return HeaderDocumentation.requestHeaders(*descriptors)
}
fun requestHeaders(descriptors: List<HeaderDescriptor>): Snippet {
    OpenApiDocContext.addHeaders(descriptors)
    return HeaderDocumentation.requestHeaders(descriptors)
}

// ✅ pathParameters / queryParameters
fun pathParameters(vararg descriptors: ParameterDescriptor): Snippet {
    OpenApiDocContext.addPathParams(descriptors.toList())
    return RequestDocumentation.pathParameters(*descriptors)
}
fun pathParameters(descriptors: List<ParameterDescriptor>): Snippet {
    OpenApiDocContext.addPathParams(descriptors)
    return RequestDocumentation.pathParameters(descriptors)
}

fun queryParameters(vararg descriptors: ParameterDescriptor): Snippet {
    OpenApiDocContext.addQueryParams(descriptors.toList())
    return RequestDocumentation.queryParameters(*descriptors)
}
fun queryParameters(descriptors: List<ParameterDescriptor>): Snippet {
    OpenApiDocContext.addQueryParams(descriptors)
    return RequestDocumentation.queryParameters(descriptors)
}

// ✅ requestFields
fun requestFields(vararg descriptors: FieldDescriptor): Snippet {
    OpenApiDocContext.addRequestFields(descriptors.toList())
    return PayloadDocumentation.requestFields(*descriptors)
}
fun requestFields(descriptors: List<FieldDescriptor>): Snippet {
    OpenApiDocContext.addRequestFields(descriptors)
    return PayloadDocumentation.requestFields(descriptors)
}

// ✅ responseFields / relaxedResponseFields
fun responseFields(vararg descriptors: FieldDescriptor): Snippet {
    OpenApiDocContext.addResponseFields(descriptors.toList())
    return PayloadDocumentation.responseFields(*descriptors)
}
fun responseFields(descriptors: List<FieldDescriptor>): Snippet {
    OpenApiDocContext.addResponseFields(descriptors)
    return PayloadDocumentation.responseFields(descriptors)
}

fun relaxedResponseFields(vararg descriptors: FieldDescriptor): Snippet {
    OpenApiDocContext.addResponseFields(descriptors.toList())
    return PayloadDocumentation.relaxedResponseFields(*descriptors)
}
fun relaxedResponseFields(descriptors: List<FieldDescriptor>): Snippet {
    OpenApiDocContext.addResponseFields(descriptors)
    return PayloadDocumentation.relaxedResponseFields(descriptors)
}
