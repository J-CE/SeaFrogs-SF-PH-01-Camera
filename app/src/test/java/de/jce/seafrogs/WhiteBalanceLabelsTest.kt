package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class WhiteBalanceLabelsTest {
    private val presets=listOf("Auto" to 1,"Tageslicht" to 5,"Bewölkt" to 6,"Schatten" to 8)
    private val profiles=listOf("Flach","Mittel","Tief","Videolicht")
    @Test fun everyStandardPresetSurvivesAStalePreparedManualProfile() {
        for((name,id) in presets) assertEquals(name,WhiteBalanceLabels.label(id,id,true,50,"",presets,profiles))
    }
    @Test fun allUnderwaterProfilesHaveTheCorrectNameAndStrength() {
        for(i in 0..2) assertEquals("${profiles[i]} · 50% · OK",WhiteBalanceLabels.label(100+i,0,true,50,"OK",presets,profiles))
    }
    @Test fun videoLightDoesNotAcquireAnUnderwaterStrengthSuffix() {
        assertEquals("Videolicht · OK",WhiteBalanceLabels.label(103,0,true,50,"OK",presets,profiles))
    }
    @Test fun unavailableAndInvalidManualModesSafelyUseTheAppliedPreset() {
        for(mode in listOf(-1,99,104,Int.MAX_VALUE)) assertEquals("Auto",WhiteBalanceLabels.label(mode,1,true,50,"",presets,profiles))
        assertEquals("Tageslicht",WhiteBalanceLabels.label(100,5,false,50,"",presets,profiles))
    }
}
