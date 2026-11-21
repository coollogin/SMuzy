package io.github.coollogin.smuzy.visualizer

enum class VisualizerMode(val preferenceValue: String) {
    WAVE("wave"),
    PEDESTAL("pedestal");

    companion object {
        fun fromPreference(value: String?): VisualizerMode =
            entries.firstOrNull { it.preferenceValue == value } ?: WAVE
    }
}
