package com.yuyan.imemodule.keyboard

/** Pixel allocation shared by the normal IME window and the RP Duo lower screen. */
internal object KeyboardGeometry {
    data class Size(val keys: Int, val composing: Int, val candidates: Int, val candidateArea: Int) {
        val total: Int get() = keys + candidateArea
    }

    fun calculate(
        displayHeight: Int,
        heightRatio: Float,
        candidateScale: Float,
        reservedBottom: Int,
        fullscreen: Boolean,
    ): Size {
        val height = displayHeight.coerceAtLeast(1)
        val maxHeight = if (fullscreen) height else (height * 0.75f).toInt().coerceAtLeast(1)
        val available = (maxHeight - reservedBottom.coerceAtLeast(0)).coerceAtLeast(1)
        val ratio = if (heightRatio.isFinite()) heightRatio.coerceIn(0.1f, 1f) else 0.5f
        val scale = if (candidateScale.isFinite()) candidateScale.coerceIn(0.1f, 3f) else 0.55f
        val maxKeys = (available / (1f + 0.29f * scale)).toInt().coerceAtLeast(1)
        val keys = (height * ratio).toInt().coerceAtLeast(1).coerceAtMost(maxKeys)
        val composing = (keys * 0.1f * scale).toInt()
        val candidates = (composing * 1.9f).toInt()
        return Size(keys, composing, candidates, composing + candidates)
    }
}
