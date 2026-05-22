package com.painhunt.domain

import kotlinx.serialization.Serializable

@Serializable
data class ImplementationTask(
    val id: String,
    val task: String,
    val done: Boolean,
)
