package io.github.coollogin.smuzy.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerModeTest {

    @Test
    fun parsesStoredValues() {
        assertEquals(VisualizerMode.WAVE, VisualizerMode.fromPreference("wave"))
        assertEquals(VisualizerMode.PEDESTAL, VisualizerMode.fromPreference("pedestal"))
    }

    @Test
    fun fallsBackToWave() {
        assertEquals(VisualizerMode.WAVE, VisualizerMode.fromPreference(null))
        assertEquals(VisualizerMode.WAVE, VisualizerMode.fromPreference("unknown"))
    }
}
