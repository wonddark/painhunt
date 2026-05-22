package com.painhunt.domain

import kotlinx.serialization.Serializable

@Serializable
data class ImplementationGoal(
    val goal: String,
    val tasks: List<ImplementationTask>,
)
