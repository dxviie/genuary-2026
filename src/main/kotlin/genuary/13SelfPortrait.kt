package genuary

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.draw.loadImage
import org.openrndr.extra.color.colormatrix.tint
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import kotlin.math.cos
import kotlin.math.sin

fun main() = application {
    configure {
        width = 841
        height = 1189
        if (displays.size > 1) display = displays[1]
        // Enable vsync for smoother frame delivery
        vsync = true
    }

    program {
        println("devices: ${VideoPlayerFFMPEG.listDeviceNames()}")

        // Try to find a working frame rate for the camera
        val deviceName = "iPhoneForMojo Camera" //"USB Camera VID"
        val videoPlayer: VideoPlayerFFMPEG = VideoPlayerFFMPEG.fromDevice(
            deviceName = deviceName,
            frameRate = 30.0
        )
        videoPlayer.play()

        extend {
            drawer.clear(ColorRGBa.BLACK)
            videoPlayer.draw(drawer)
        }
    }
}
