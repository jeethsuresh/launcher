package app.still.launcher

import java.util.UUID

data class LauncherFolder(val key: String = "folder:${UUID.randomUUID()}", val title: String = "Folder", val apps: List<String>)
data class MoveSource(val key: String, val itemId: String? = null, val fromDock: Boolean = false, val folderKey: String? = null)
data class LauncherState(val items: List<HomeItem>, val dock: List<String>, val folders: List<LauncherFolder>) {
    fun without(source: MoveSource): LauncherState {
        val updated = folders.map { if (it.key == source.folderKey) it.copy(apps = it.apps.filterNot { key -> key == source.key }) else it }
        val empty = updated.filter { it.apps.isEmpty() }.map { it.key }.toSet()
        return copy(items = items.filterNot { it.id == source.itemId || it.appKey in empty },
            dock = dock.filterNot { (source.fromDock && it == source.key) || it in empty }, folders = updated.filterNot { it.key in empty })
    }

    /** Group only apps, preserving every source until a valid destination is known. */
    fun group(source: MoveSource, targetKey: String, targetItemId: String? = null): LauncherState? {
        if (source.key.isEmpty() || source.key.startsWith("folder:") || source.key == targetKey || source.itemId == targetItemId && targetItemId != null) return null
        val target = folders.find { it.key == targetKey }
        if (targetKey.startsWith("folder:") && target == null || target?.apps?.contains(source.key) == true) return null
        if (targetItemId != null && items.none { it.id == targetItemId && it.kind in listOf(ItemKind.APP, ItemKind.FOLDER) }) return null
        if (targetItemId == null && targetKey !in dock) return null
        val state = without(source)
        if (target != null) return state.copy(folders = state.folders.map {
            if (it.key == targetKey) it.copy(apps = it.apps + source.key) else it
        })
        val folder = LauncherFolder(apps = listOf(targetKey, source.key))
        return state.copy(items = state.items.map { if (it.id == targetItemId) it.copy(kind = ItemKind.FOLDER, appKey = folder.key) else it },
            dock = state.dock.map { if (targetItemId == null && it == targetKey) folder.key else it }, folders = state.folders + folder)
    }
}
