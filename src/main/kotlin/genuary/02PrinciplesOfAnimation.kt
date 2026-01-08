package genuary

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.olive.oliveProgram

fun main() = application {
    configure {
        width = 1080
        height = 1080
        display = displays[1]
    }

    oliveProgram {
        extend {
            drawer.clear(ColorRGBa.PINK)
            drawer.circle(mouse.position.x, mouse.position.y, 55.0)
        }
    }
}