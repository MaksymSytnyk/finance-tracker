package com.maksym.financetracker

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.window.Window
import javax.imageio.ImageIO
import androidx.compose.ui.window.application

fun main() = application {
    val appIcon = remember {
        BitmapPainter(ImageIO.read(checkNotNull(object {}.javaClass.getResource("/app-icon.png"))).asImageBitmap())
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Personal Finance Tracker",
        icon = appIcon,
    ) {
        DesktopFinanceApp()
    }
}
