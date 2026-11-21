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
    fun fallsBackToPedestal() {
        assertEquals(VisualizerMode.PEDESTAL, VisualizerMode.fromPreference(null))
        assertEquals(VisualizerMode.PEDESTAL, VisualizerMode.fromPreference("unknown"))
    }
}
