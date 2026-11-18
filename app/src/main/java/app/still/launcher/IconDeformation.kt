package app.still.launcher

/** Reciprocal scales preserve material area through compression and release. */
data class IconDeformation(val x: Float, val y: Float, val rotation: Float) {
    companion object {
        fun at(energy: Float): IconDeformation {
            val bounded = if (energy.isFinite()) energy.coerceIn(-.85f, 1f) else 0f
            val y = 1f - .085f * bounded
            return IconDeformation(1f / y, y, if (bounded == 0f) 0f else -1.5f * bounded)
        }
    }
}
