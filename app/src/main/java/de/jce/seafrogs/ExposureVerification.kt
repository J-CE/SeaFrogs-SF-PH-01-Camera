package de.jce.seafrogs

/**
 * Verify reported sensor values without relaxing the user's ceilings. Missing metadata is
 * unconfirmed, not proof of a capture failure or a breached ceiling.
 */
data class ExposureVerification(
    val exposureConfirmed: Boolean,
    val gainConfirmed: Boolean,
    val issues: List<String>,
) {
    companion object {
        fun check(
            limits: ExposureLimits,
            iso: Int?,
            timeNs: Long?,
            aeMode: Int?,
            boost: Int?,
            requestedBoost: Int?,
        ): ExposureVerification {
            val exposureIssues = buildList {
                if (iso == null || iso <= 0) add("Sensor-ISO fehlt")
                if (timeNs == null || timeNs <= 0) add("Sensorzeit fehlt")
                if (limits.isoCap > 0 && iso != null && iso > limits.isoCap)
                    add("ISO über Grenze ${limits.isoCap}")
                if (limits.isoCap > 0 && timeNs != null && timeNs > limits.longestTimeNs)
                    add("Zeit über Grenze")
                // Camera2 CONTROL_AE_MODE_OFF is 0. Keep this policy Android-free for boundary
                // tests.
                if (aeMode == null) add("AE-Modus fehlt")
                else if (aeMode != 0) add("Manuelle Belichtung nicht bestätigt")
            }
            val gainIssues = buildList {
                if (requestedBoost != null && boost != requestedBoost)
                    add("JPEG-Verstärkung nicht bestätigt")
                if (limits.boostCap > 0 && (boost == null || boost > limits.boostCap))
                    add("Digitalgrenze ${limits.boostCap}% nicht bestätigt")
            }
            return ExposureVerification(
                exposureIssues.isEmpty(),
                gainIssues.isEmpty(),
                exposureIssues + gainIssues,
            )
        }
    }
}
