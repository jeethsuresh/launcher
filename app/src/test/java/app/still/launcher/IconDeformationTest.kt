package app.still.launcher

import org.junit.Assert.*
import org.junit.Test

class IconDeformationTest {
    @Test fun deformationPreservesAreaAndIsBoundedAcrossRapidRetargets() {
        for (energy in listOf(Float.NaN, Float.POSITIVE_INFINITY, -100f, -.85f, -.62f, 0f, .7f, 1f, 100f)) {
            val pose = IconDeformation.at(energy)
            assertTrue(pose.x.isFinite() && pose.y.isFinite() && pose.rotation.isFinite())
            assertEquals(1f, pose.x * pose.y, .00001f)
            assertTrue(pose.x in .9f..1.1f && pose.y in .9f..1.1f)
            assertTrue(kotlin.math.abs(pose.rotation) <= 1.5f)
        }
    }
    @Test fun restingPoseIsIdentity() {
        assertEquals(IconDeformation(1f, 1f, 0f), IconDeformation.at(0f))
    }
}
