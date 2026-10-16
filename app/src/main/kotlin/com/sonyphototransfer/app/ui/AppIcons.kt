package com.sonyphototransfer.app.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** The few Material symbols missing from material-icons-core, to avoid the huge extended set. */
object AppIcons {
    val Download: ImageVector by lazy {
        materialIcon(name = "Filled.Download") {
            materialPath {
                moveTo(19f, 9f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                lineToRelative(7f, 7f)
                lineToRelative(7f, -7f)
                close()
                moveTo(5f, 18f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    val Photo: ImageVector by lazy {
        materialIcon(name = "Filled.Photo") {
            materialPath {
                moveTo(21f, 19f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                close()
                moveTo(8.5f, 13.5f)
                lineToRelative(2.5f, 3.01f)
                lineTo(14.5f, 12f)
                lineToRelative(4.5f, 6f)
                horizontalLineTo(5f)
                lineToRelative(3.5f, -4.5f)
                close()
            }
        }
    }

    val SelectAll: ImageVector by lazy {
        materialIcon(name = "Filled.DoneAll") {
            materialPath {
                moveTo(18f, 7f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-6.34f, 6.34f)
                lineToRelative(1.41f, 1.41f)
                lineTo(18f, 7f)
                close()
                moveTo(22.24f, 5.59f)
                lineTo(11.66f, 16.17f)
                lineTo(7.48f, 12f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(11.66f, 19f)
                lineToRelative(12f, -12f)
                lineToRelative(-1.42f, -1.41f)
                close()
                moveTo(0.41f, 13.41f)
                lineTo(6f, 19f)
                lineToRelative(1.41f, -1.41f)
                lineTo(1.83f, 12f)
                lineTo(0.41f, 13.41f)
                close()
            }
        }
    }
}
