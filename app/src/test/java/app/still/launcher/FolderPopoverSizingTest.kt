package app.still.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPopoverSizingTest {
    @Test fun smallAndEmptyFoldersReserveTwoRows() {
        assertEquals(256, FolderPopoverSizing.height(0, 4, 96, 64, 700, 600))
        assertEquals(256, FolderPopoverSizing.height(3, 4, 96, 64, 700, 600))
        assertEquals(256, FolderPopoverSizing.height(8, 4, 96, 64, 700, 600))
    }

    @Test fun homeColumnsControlGrowthAndFolderPositionCapsIt() {
        assertEquals(448, FolderPopoverSizing.height(13, 4, 96, 64, 700, 600))
        assertEquals(544, FolderPopoverSizing.height(13, 3, 96, 64, 700, 600))
        assertEquals(352, FolderPopoverSizing.height(30, 4, 96, 64, 700, 400))
        assertEquals(256, FolderPopoverSizing.height(30, 4, 96, 64, 700, 100))
    }

    @Test fun dockFoldersStayCompactAndSmallWindowsStayWithinBounds() {
        assertEquals(256, FolderPopoverSizing.height(40, 4, 96, 64, 700, null))
        assertEquals(180, FolderPopoverSizing.height(40, 4, 96, 64, 180, 600))
        assertEquals(0, FolderPopoverSizing.height(40, 4, 96, 64, 0, 600))
    }
}
