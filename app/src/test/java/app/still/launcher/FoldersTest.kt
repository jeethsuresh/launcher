package app.still.launcher

import org.junit.Assert.*
import org.junit.Test

class FoldersTest {
    @Test fun createsHomeAndDockFoldersWithoutLosingSources() {
        val a = HomeItem(kind = ItemKind.APP, appKey = "a")
        val b = HomeItem(kind = ItemKind.APP, appKey = "b", x = 1)
        val state = LauncherState(listOf(a, b), listOf("c", "d"), emptyList())
        val grouped = state.group(MoveSource("a", a.id), "b", b.id)!!
        assertEquals(1, grouped.items.size)
        assertEquals(ItemKind.FOLDER, grouped.items.single().kind)
        assertEquals(listOf("b", "a"), grouped.folders.single().apps)
        val dock = grouped.group(MoveSource("c", fromDock = true), "d")!!
        assertEquals(1, dock.dock.size)
        assertEquals(listOf("d", "c"), dock.folders.last().apps)
    }
    @Test fun movingBetweenFoldersAndExtractingLastMemberPreservesReferences() {
        val f = LauncherFolder(apps = listOf("a"))
        val g = LauncherFolder(apps = listOf("b"))
        val state = LauncherState(listOf(HomeItem(kind = ItemKind.FOLDER, appKey = f.key)), listOf(g.key), listOf(f, g))
        val moved = state.group(MoveSource("a", folderKey = f.key), g.key)!!
        assertTrue(moved.items.isEmpty()); assertEquals(listOf(g.key), moved.dock)
        assertEquals(listOf(g.copy(apps = listOf("b", "a"))), moved.folders)
    }
    @Test fun rejectsDuplicatesSelfDropsNestingAndWidgetTargets() {
        val folder = LauncherFolder(apps = listOf("a", "b"))
        val widget = HomeItem(kind = ItemKind.WIDGET)
        val state = LauncherState(listOf(widget), listOf(folder.key), listOf(folder))
        assertNull(state.group(MoveSource("a"), folder.key))
        assertNull(state.group(MoveSource(folder.key, fromDock = true), "c"))
        assertNull(state.group(MoveSource("c"), "", widget.id))
        assertEquals(listOf(folder), state.folders)
    }
}
