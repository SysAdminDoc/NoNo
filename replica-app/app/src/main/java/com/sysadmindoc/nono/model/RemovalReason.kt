package com.sysadmindoc.nono.model

/**
 * Why a notification left the shade, as far as the platform said.
 *
 * Android supplies a reason code to `onNotificationRemoved` from API 26. Below that, and through
 * the older overloads any OEM may still call, there is no code at all, so the answer is
 * [UNKNOWN] and stays that way. Nothing here is inferred from timing, from the app, or from what
 * a sibling notification did: "the user swiped it away" and "the app withdrew it" look identical
 * from the outside, and guessing between them would put a claim in the history that the device
 * never made.
 */
enum class RemovalReason(val label: String, val userDismissed: Boolean = false) {
    /** No reason was supplied, or the code was one this build does not recognise. */
    UNKNOWN("Not recorded"),

    /** The user tapped the notification, which dismisses it unless the app said otherwise. */
    CLICKED("Opened by you", userDismissed = true),

    /** The user swiped this one away. */
    DISMISSED("Dismissed by you", userDismissed = true),

    /** The user cleared the whole shade. */
    DISMISSED_ALL("Cleared with everything else", userDismissed = true),

    /** The posting app withdrew it. */
    WITHDRAWN_BY_APP("Withdrawn by the app"),

    /** Another listener, not this one, cancelled it. This app cancels nothing. */
    CANCELLED_BY_LISTENER("Cancelled by another app"),

    /** The notification's own timeout elapsed. */
    TIMED_OUT("Timed out"),

    /** The user snoozed it. It may come back. */
    SNOOZED("Snoozed"),

    /** The platform withdrew it while reorganising a group. */
    GROUP_MANAGEMENT("Regrouped by Android"),

    /** The app, its channel, or its data changed underneath the notification. */
    APP_STATE_CHANGED("The app or its settings changed"),

    /** The assistant cancelled it. */
    ASSISTANT("Cancelled by the assistant"),

    /** The platform reported an error posting or keeping it. */
    PLATFORM_ERROR("Android reported an error"),
    ;

    companion object {
        // The platform constants, written out rather than referenced, because most of them were
        // added after this app's minimum API and NotificationListenerService does not expose them
        // as a stable set to compile against at minSdk 24.
        private const val REASON_CLICK = 1
        private const val REASON_CANCEL = 2
        private const val REASON_CANCEL_ALL = 3
        private const val REASON_ERROR = 4
        private const val REASON_PACKAGE_CHANGED = 5
        private const val REASON_USER_STOPPED = 6
        private const val REASON_PACKAGE_BANNED = 7
        private const val REASON_APP_CANCEL = 8
        private const val REASON_APP_CANCEL_ALL = 9
        private const val REASON_LISTENER_CANCEL = 10
        private const val REASON_LISTENER_CANCEL_ALL = 11
        private const val REASON_GROUP_SUMMARY_CANCELED = 12
        private const val REASON_GROUP_OPTIMIZATION = 13
        private const val REASON_PACKAGE_SUSPENDED = 14
        private const val REASON_PROFILE_TURNED_OFF = 15
        private const val REASON_UNAUTOBUNDLED = 16
        private const val REASON_CHANNEL_BANNED = 17
        private const val REASON_SNOOZED = 18
        private const val REASON_TIMEOUT = 19
        private const val REASON_CHANNEL_REMOVED = 20
        private const val REASON_CLEAR_DATA = 21
        private const val REASON_ASSISTANT_CANCEL = 22

        /**
         * A notification hidden because the device went into lockdown.
         *
         * Deliberately mapped to [UNKNOWN]. Lockdown hides notifications; it does not mean this
         * one is gone, and recording it as a removal would be wrong in both directions. It would
         * also put "this device was locked down at this time" into a history the user may export.
         */
        private const val REASON_LOCKDOWN = 23

        /**
         * Maps a platform reason code, or [UNKNOWN] when there is nothing to map.
         *
         * @param reason the code the callback supplied, or null when it supplied none.
         * @param sdkInt the running platform level. Below API 26 no code exists, so a value
         * arriving from an OEM build that back-ported the callback is not trusted either.
         */
        fun fromPlatform(reason: Int?, sdkInt: Int): RemovalReason {
            if (reason == null || sdkInt < 26) return UNKNOWN
            return when (reason) {
                REASON_CLICK -> CLICKED
                REASON_CANCEL -> DISMISSED
                REASON_CANCEL_ALL -> DISMISSED_ALL
                REASON_ERROR -> PLATFORM_ERROR
                REASON_APP_CANCEL, REASON_APP_CANCEL_ALL -> WITHDRAWN_BY_APP
                REASON_LISTENER_CANCEL, REASON_LISTENER_CANCEL_ALL -> CANCELLED_BY_LISTENER
                REASON_TIMEOUT -> TIMED_OUT
                REASON_SNOOZED -> SNOOZED
                REASON_GROUP_SUMMARY_CANCELED, REASON_GROUP_OPTIMIZATION, REASON_UNAUTOBUNDLED -> GROUP_MANAGEMENT
                REASON_PACKAGE_CHANGED,
                REASON_USER_STOPPED,
                REASON_PACKAGE_BANNED,
                REASON_PACKAGE_SUSPENDED,
                REASON_PROFILE_TURNED_OFF,
                REASON_CHANNEL_BANNED,
                REASON_CHANNEL_REMOVED,
                REASON_CLEAR_DATA,
                -> APP_STATE_CHANGED
                REASON_ASSISTANT_CANCEL -> ASSISTANT
                // Lockdown, and anything a later Android adds. An unrecognised code is not a
                // reason to invent one.
                REASON_LOCKDOWN -> UNKNOWN
                else -> UNKNOWN
            }
        }

        /** Reads a stored value back, tolerating a name written by a build that knew more. */
        fun fromStored(name: String?): RemovalReason =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}
