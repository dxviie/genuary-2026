package genuary

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.extra.olive.oliveProgram
import java.io.File

/**
 *  This is a template for a live program.
 *
 *  It uses oliveProgram {} instead of program {}. All code inside the
 *  oliveProgram {} can be changed while the program is running.
 */

fun main() = application {
    configure {
        width = 1080
        height = 1080
        display = displays[1]
    }

    oliveProgram {
        val fontFile = File("data/fonts/default.otf")
        val font = loadFont(fontFile.toURI().toString(), 13.0)

        extend {
            drawer.fontMap = font
            drawer.clear(ColorRGBa.PINK)
        }
    }
}