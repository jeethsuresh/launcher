package app.still.launcher

import org.junit.Assert.*
import org.junit.Test

class LayoutEngineTest {
    @Test fun rejectsOverlapButAllowsAdjacentCells() {
        val engine = LayoutEngine(4, 6)
        val widget = HomeItem(kind = ItemKind.WIDGET, spanX = 2, spanY = 2)
        assertFalse(engine.fits(HomeItem(kind = ItemKind.APP, x = 1, y = 1), listOf(widget)))
        assertTrue(engine.fits(HomeItem(kind = ItemKind.APP, x = 2), listOf(widget)))
        assertTrue(engine.fits(widget, listOf(widget)))
        assertFalse(engine.fits(widget.copy(x = 3), listOf(widget)))
    }
    @Test fun fillsNewPageWithoutDroppingAnything() {
        val e = LayoutEngine(3, 4)
        val items = mutableListOf<HomeItem>()
        repeat(30) { items += e.vacant(HomeItem(kind = ItemKind.APP), items)!! }
        assertEquals(30, items.size)
        assertEquals(2, items.last().page)
        assertTrue(items.all { e.fits(it, items) })
    }
    @Test fun smallerGridReflowsWidgetsAndIconsWithoutCollisions() {
        val original = mutableListOf(HomeItem(kind = ItemKind.WIDGET, spanX = 4, spanY = 2))
        repeat(26) { original += LayoutEngine(5, 6).vacant(HomeItem(kind = ItemKind.APP), original)!! }
        val engine = LayoutEngine(3, 4)
        val packed = engine.reflow(original)!!
        assertEquals(original.map { it.id }.toSet(), packed.map { it.id }.toSet())
        assertTrue(packed.all { engine.fits(it, packed) })
    }
    @Test fun movingDoesNotCollideWithItsOldPosition() {
        val item = HomeItem(kind = ItemKind.WIDGET, x = 1, y = 1, spanX = 2, spanY = 2)
        assertTrue(LayoutEngine(4, 6).fits(item.copy(x = 2), listOf(item)))
    }
    @Test fun impossibleSpanDoesNotLoopOrLoseData() {
        assertNull(LayoutEngine(3, 4).vacant(HomeItem(kind = ItemKind.WIDGET, spanX = 4), emptyList()))
    }
}
