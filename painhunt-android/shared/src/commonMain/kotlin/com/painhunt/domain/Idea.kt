package com.painhunt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Idea(
    val id: String,
    @SerialName("reddit_post_id") val redditPostId: String,
    @SerialName("subreddit_id") val subredditId: String,
    val title: String,
    @SerialName("body_excerpt") val bodyExcerpt: String?,
    val url: String,
    val author: String,
    @SerialName("reddit_score") val redditScore: Int,
    @SerialName("ai_relevance_score") val aiRelevanceScore: Int,
    @SerialName("ai_summary") val aiSummary: String,
    @SerialName("ai_category") val aiCategory: String,
    @SerialName("scraped_at") val scrapedAt: String,
)
