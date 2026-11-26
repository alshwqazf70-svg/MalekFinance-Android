package com.malekfinance.voice

/** Android-free decisions, exercised for every supported API level without a virtual device. */
enum class SpeechRoute { ACTIVITY, ON_DEVICE, UNAVAILABLE }

data class SpeechCapabilities(val sdk: Int, val activityAvailable: Boolean, val onDeviceAvailable: Boolean)

object SpeechRoutePolicy {
    fun select(capabilities: SpeechCapabilities, preferOnDevice: Boolean = false): SpeechRoute = with(capabilities) {
        val local = sdk >= 31 && onDeviceAvailable
        when {
            preferOnDevice && local -> SpeechRoute.ON_DEVICE
            activityAvailable -> SpeechRoute.ACTIVITY // Includes the proven Note 9 / API29 flow.
            local -> SpeechRoute.ON_DEVICE
            else -> SpeechRoute.UNAVAILABLE
        }
    }
}

enum class SpeechActivityStatus { OK, CANCELLED, NO_MATCH, AUDIO_ERROR, CLIENT_ERROR, NETWORK_ERROR, SERVER_ERROR, UNKNOWN_ERROR }

sealed interface SpeechOutcome {
    data class Transcript(val text: String) : SpeechOutcome
    data class Failure(val status: SpeechActivityStatus) : SpeechOutcome
    data object Cancelled : SpeechOutcome
    data object Empty : SpeechOutcome
    data object Ignored : SpeechOutcome
}

object SpeechResultPolicy {
    fun fromActivity(awaitingResult: Boolean, status: SpeechActivityStatus, candidates: List<String>?): SpeechOutcome = when {
        !awaitingResult -> SpeechOutcome.Ignored
        status == SpeechActivityStatus.CANCELLED -> SpeechOutcome.Cancelled
        status == SpeechActivityStatus.OK -> candidates?.firstOrNull { it.isNotBlank() }?.trim()
            ?.let(SpeechOutcome::Transcript) ?: SpeechOutcome.Empty
        else -> SpeechOutcome.Failure(status)
    }
}
