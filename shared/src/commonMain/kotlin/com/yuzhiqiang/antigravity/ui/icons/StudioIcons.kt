package com.yuzhiqiang.antigravity.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object StudioIcons {
    private var _switchAccount: ImageVector? = null

    /**
     * 优雅的圆形双向切换账号矢量图标
     */
    val SwitchAccount: ImageVector
        get() {
            if (_switchAccount != null) {
                return _switchAccount!!
            }

            val path1 = "M514.56 962.56C267.776 962.56 66.56 761.344 66.56 514.56S267.776 66.56 514.56 66.56 962.56 267.776 962.56 514.56 761.344 962.56 514.56 962.56z m0-834.56C301.568 128 128 301.568 128 514.56S301.568 901.12 514.56 901.12s386.56-173.568 386.56-386.56S727.552 128 514.56 128z"
            val path2 = "M737.28 482.816H291.84c-15.36 0-28.16-12.8-28.16-28.16s12.8-28.16 28.16-28.16h445.44c15.36 0 28.16 12.8 28.16 28.16s-12.8 28.16-28.16 28.16z"
            val path3 = "M615.936 293.376l141.312 141.312c10.752 10.752 10.752 28.672 0 39.936-10.752 10.752-28.672 10.752-39.936 0l-141.312-141.312c-10.752-10.752-10.752-28.672 0-39.936 11.264-10.752 29.184-10.752 39.936 0zM311.808 555.008L453.12 696.32c10.752 10.752 10.752 28.672 0 39.936-10.752 10.752-28.672 10.752-39.936 0l-141.312-141.312c-10.752-10.752-10.752-28.672 0-39.936s28.672-11.264 39.936 0z"
            val path4 = "M737.28 603.136H291.84c-15.36 0-28.16-12.8-28.16-28.16s12.8-28.16 28.16-28.16h445.44c15.36 0 28.16 12.8 28.16 28.16s-12.8 28.16-28.16 28.16z"

            _switchAccount = ImageVector.Builder(
                name = "StudioIcons.SwitchAccount",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 1024f,
                viewportHeight = 1024f
            ).apply {
                addPath(
                    pathData = PathParser().parsePathString(path1).toNodes(),
                    fill = SolidColor(Color.Black)
                )
                addPath(
                    pathData = PathParser().parsePathString(path2).toNodes(),
                    fill = SolidColor(Color.Black)
                )
                addPath(
                    pathData = PathParser().parsePathString(path3).toNodes(),
                    fill = SolidColor(Color.Black)
                )
                addPath(
                    pathData = PathParser().parsePathString(path4).toNodes(),
                    fill = SolidColor(Color.Black)
                )
            }.build()

            return _switchAccount!!
        }
}
