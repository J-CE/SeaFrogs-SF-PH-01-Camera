package de.jce.seafrogs

/** A profile switch may briefly retain a prepared matrix for the old mode.
 * Never index the manual profile list with an Android AWB preset ID.
 */
object WhiteBalanceLabels {
    fun label(requested: Int, applied: Int, manualPrepared: Boolean, strength: Int,
              status: String, presets: List<Pair<String,Int>>, profiles: List<String>): String {
        val profile = profiles.getOrNull(requested-100)
        if(manualPrepared && profile!=null) return profile +
            (if(requested!=103) " · $strength%" else "") + " · $status"
        return (presets.firstOrNull { it.second==applied }?.first ?: "Auto") + status
    }
}
