package com.hongxiang.kpilot.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Android vectors using the same document, language book, remote, books and gear motifs as iOS. */
object NavigationIcons {
    val Notes: ImageVector by lazy {
        navigationIcon("ClippingsDocument") {
            outline {
                moveTo(13.5f, 2.75f)
                horizontalLineTo(6.75f)
                curveTo(5.78f, 2.75f, 5f, 3.53f, 5f, 4.5f)
                verticalLineTo(19.5f)
                curveTo(5f, 20.47f, 5.78f, 21.25f, 6.75f, 21.25f)
                horizontalLineTo(17.25f)
                curveTo(18.22f, 21.25f, 19f, 20.47f, 19f, 19.5f)
                verticalLineTo(8.25f)
                close()
                moveTo(13.5f, 2.75f)
                verticalLineTo(6.75f)
                curveTo(13.5f, 7.58f, 14.17f, 8.25f, 15f, 8.25f)
                horizontalLineTo(19f)
                moveTo(8.5f, 12f)
                horizontalLineTo(15.5f)
                moveTo(8.5f, 15.25f)
                horizontalLineTo(15.5f)
                moveTo(8.5f, 18.5f)
                horizontalLineTo(12.75f)
            }
        }
    }

    val Vocabulary: ImageVector by lazy {
        navigationIcon("VocabularyBook") {
            outline {
                moveTo(6.5f, 3.25f)
                horizontalLineTo(19f)
                verticalLineTo(20.75f)
                horizontalLineTo(6.5f)
                curveTo(4.98f, 20.75f, 3.75f, 19.52f, 3.75f, 18f)
                verticalLineTo(6f)
                curveTo(3.75f, 4.48f, 4.98f, 3.25f, 6.5f, 3.25f)
                close()
                moveTo(19f, 17f)
                horizontalLineTo(6.5f)
                curveTo(4.98f, 17f, 3.75f, 17.84f, 3.75f, 18.88f)
                moveTo(7f, 3.25f)
                verticalLineTo(17f)
                moveTo(9.5f, 13.25f)
                lineTo(12.75f, 6.75f)
                lineTo(16f, 13.25f)
                moveTo(10.5f, 11.25f)
                horizontalLineTo(15f)
            }
        }
    }

    val Remote: ImageVector by lazy {
        navigationIcon("KindleRemote") {
            outline {
                moveTo(10f, 2.25f)
                horizontalLineTo(14f)
                curveTo(15.38f, 2.25f, 16.5f, 3.37f, 16.5f, 4.75f)
                verticalLineTo(19.25f)
                curveTo(16.5f, 20.63f, 15.38f, 21.75f, 14f, 21.75f)
                horizontalLineTo(10f)
                curveTo(8.62f, 21.75f, 7.5f, 20.63f, 7.5f, 19.25f)
                verticalLineTo(4.75f)
                curveTo(7.5f, 3.37f, 8.62f, 2.25f, 10f, 2.25f)
                close()
                moveTo(14.5f, 7.5f)
                curveTo(14.5f, 8.88f, 13.38f, 10f, 12f, 10f)
                curveTo(10.62f, 10f, 9.5f, 8.88f, 9.5f, 7.5f)
                curveTo(9.5f, 6.12f, 10.62f, 5f, 12f, 5f)
                curveTo(13.38f, 5f, 14.5f, 6.12f, 14.5f, 7.5f)
                close()
                moveTo(10.25f, 13.25f)
                horizontalLineTo(10.3f)
                moveTo(13.7f, 13.25f)
                horizontalLineTo(13.75f)
                moveTo(10.25f, 16.5f)
                horizontalLineTo(10.3f)
                moveTo(13.7f, 16.5f)
                horizontalLineTo(13.75f)
                moveTo(11f, 19.25f)
                horizontalLineTo(13f)
            }
        }
    }

    val Library: ImageVector by lazy {
        navigationIcon("Bookshelf") {
            outline {
                moveTo(3f, 4.25f)
                horizontalLineTo(6.5f)
                verticalLineTo(20.5f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 7.5f)
                horizontalLineTo(6.5f)
                moveTo(3f, 17.5f)
                horizontalLineTo(6.5f)
                moveTo(8.75f, 2.75f)
                horizontalLineTo(12.25f)
                verticalLineTo(20.5f)
                horizontalLineTo(8.75f)
                close()
                moveTo(8.75f, 5.75f)
                horizontalLineTo(12.25f)
                moveTo(8.75f, 17.5f)
                horizontalLineTo(12.25f)
                moveTo(14.25f, 5f)
                lineTo(17.63f, 4.09f)
                lineTo(21.78f, 19.55f)
                lineTo(18.4f, 20.46f)
                close()
                moveTo(15.03f, 7.9f)
                lineTo(18.41f, 6.99f)
                moveTo(17.62f, 17.56f)
                lineTo(21f, 16.65f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        navigationIcon("SettingsGear") {
            outline {
                moveTo(10.3f, 2.25f)
                horizontalLineTo(13.7f)
                lineTo(14.1f, 4.8f)
                lineTo(15.35f, 5.3f)
                lineTo(17.45f, 3.8f)
                lineTo(20.2f, 6.55f)
                lineTo(18.7f, 8.65f)
                lineTo(19.2f, 9.9f)
                lineTo(21.75f, 10.3f)
                verticalLineTo(13.7f)
                lineTo(19.2f, 14.1f)
                lineTo(18.7f, 15.35f)
                lineTo(20.2f, 17.45f)
                lineTo(17.45f, 20.2f)
                lineTo(15.35f, 18.7f)
                lineTo(14.1f, 19.2f)
                lineTo(13.7f, 21.75f)
                horizontalLineTo(10.3f)
                lineTo(9.9f, 19.2f)
                lineTo(8.65f, 18.7f)
                lineTo(6.55f, 20.2f)
                lineTo(3.8f, 17.45f)
                lineTo(5.3f, 15.35f)
                lineTo(4.8f, 14.1f)
                lineTo(2.25f, 13.7f)
                verticalLineTo(10.3f)
                lineTo(4.8f, 9.9f)
                lineTo(5.3f, 8.65f)
                lineTo(3.8f, 6.55f)
                lineTo(6.55f, 3.8f)
                lineTo(8.65f, 5.3f)
                lineTo(9.9f, 4.8f)
                close()
                moveTo(15.5f, 12f)
                curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
                curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
                curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
                curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
                close()
            }
        }
    }
}

private fun navigationIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

private fun ImageVector.Builder.outline(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block
    )
}
