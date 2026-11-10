package com.landxcape.synkromesh.domain.model

enum class IntercomMode(val title: String, val description: String) {
    PUSH_TO_TALK(
        title = "Push to Talk",
        description = "Hold to speak; incoming audio plays simultaneously in full duplex"
    ),
    TOGGLE(
        title = "Toggle / Latch",
        description = "Tap once to open mic, tap again to mute; incoming audio always active"
    ),
    ALWAYS_ON(
        title = "Always-On Intercom",
        description = "Continuous open-mic with VAD/noise gating; simultaneous full duplex"
    )
}
