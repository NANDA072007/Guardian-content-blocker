package com.guardian.app.walls.wall2.model

data class WindowHierarchy(
    val primary: WindowInfo?,
    val secondary: WindowInfo?,
    val overlays: List<WindowInfo> = emptyList(),
    val pictureInPicture: Boolean = false
)

data class WindowInfo(
    val packageName: String,
    val isFocused: Boolean,
    val layer: Int,
    val bounds: android.graphics.Rect?
)
