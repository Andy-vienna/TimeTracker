package org.fx.timetracker

data class OnlineEvent(
    val id: String,
    val event: String,
    val username: String,
    val source: String,
    val deviceId: String,
    val tz: String,
    val ts: String
)
