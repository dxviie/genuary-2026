package render

import genuary.bauhausBackground
import genuary.renderBauhausFaceDetection
import org.openrndr.application
import org.openrndr.draw.loadFont

/**
 * Website render for 13 Self Portrait, Bauhaus mode (see genuary/13SelfPortraitBauhaus.kt
 * and mode 5 of genuary/13SelfPortraitJavaCVLive.kt): every facial feature becomes
 * a Bauhaus shape; the composition reshuffles itself every three seconds.
 */
fun main() = application {
    configure {
        width = 840
        height = 1188
    }

    program {
        val totalFrames = 900L // 15s @ 60fps

        val font = loadFont("data/fonts/Jost-VariableFont_wght.ttf", 200.0)

        val sourceImage = org.openrndr.draw.loadImage("data/images/face-01.jpg")
        val displayRect = fitRect(sourceImage.width, sourceImage.height, width, height)
        val faces = detectFaces68(sourceImage, displayRect)
        println("Rendering ${faces.size} face(s), ${faces.sumOf { it.landmarks.size }} landmarks")

        extend(siteRecorder("13-bauhaus", totalFrames))

        var frame = 0L
        extend {
            drawer.clear(bauhausBackground)
            drawer.fontMap = font
            val shouldRegenerate = frame > 0 && frame % 180 == 0L
            renderBauhausFaceDetection(drawer, faces, shouldRegenerate)
            frame++
        }
    }
}
