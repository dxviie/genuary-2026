package render

import genuary.renderFaceDetection
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadImage

/**
 * Website render for 13 Self Portrait, particle mode (see genuary/13SelfPortraitRender.kt
 * and mode 4 of genuary/13SelfPortraitJavaCVLive.kt): the 68 landmarks of the
 * portrait photo drawn as neon wireframe with particles drifting off them.
 * Only the stylized output is rendered; the source photo itself stays offline.
 */
fun main() = application {
    configure {
        width = 840
        height = 1188
    }

    program {
        val totalFrames = 900L // 15s @ 60fps

        val sourceImage = loadImage("data/images/face-01.jpg")
        val displayRect = fitRect(sourceImage.width, sourceImage.height, width, height)
        val faces = detectFaces68(sourceImage, displayRect)
        println("Rendering ${faces.size} face(s), ${faces.sumOf { it.landmarks.size }} landmarks")

        extend(siteRecorder("13-hero", totalFrames))

        extend {
            drawer.clear(ColorRGBa.BLACK)
            renderFaceDetection(drawer, faces, displayRect, 1.0 / 60.0)
        }
    }
}
