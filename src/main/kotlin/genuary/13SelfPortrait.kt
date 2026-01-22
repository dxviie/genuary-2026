package genuary

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.draw.loadImage
import org.openrndr.draw.renderTarget
import org.openrndr.extra.color.colormatrix.tint
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun main() = application {
    configure {
        width = 841
        height = 1189
        if (displays.size > 1) display = displays[1]
        // Disable vsync to avoid timing conflicts with video capture
        vsync = true

    }

    program {
        println("devices: ${VideoPlayerFFMPEG.listDeviceNames()}")

        val deviceName = "iPhoneForMojo Camera"
        val videoPlayer = VideoPlayerFFMPEG.fromDevice(
            deviceName = deviceName,
            frameRate = 30.0
        )
        videoPlayer.play()

        // Create render target matching video dimensions (will be created after first frame)
        var videoTarget: org.openrndr.draw.RenderTarget? = null

        extend {
            drawer.clear(ColorRGBa.BLACK)

            // Create render target on first frame
            if (videoTarget == null && videoPlayer.width > 0 && videoPlayer.height > 0) {
                videoTarget = renderTarget(videoPlayer.width, videoPlayer.height) {
                    colorBuffer()
                }
                println("Video initialized: ${videoPlayer.width}x${videoPlayer.height}")
            }

            videoTarget?.let { target ->
                // Draw video to render target at native resolution
                drawer.withTarget(target) {
                    drawer.clear(ColorRGBa.BLACK)
                    videoPlayer.draw(drawer)
                }

                // Now scale the render target to fit the window with center crop
                val videoWidth = target.height.toDouble()
                val videoHeight = target.width.toDouble()
                val windowWidth = width.toDouble()
                val windowHeight = height.toDouble()

                // Calculate aspect ratios
                val videoAspect = videoWidth / videoHeight
                val windowAspect = windowWidth / windowHeight

                // Calculate scale to cover the window (may crop)
                val scale = if (videoAspect > windowAspect) {
                    // Video is wider - scale to height and crop sides
                    windowHeight / videoHeight
                } else {
                    // Video is taller - scale to width and crop top/bottom
                    windowWidth / videoWidth
                }

                // Calculate scaled dimensions
                val scaledWidth = videoWidth * scale
                val scaledHeight = videoHeight * scale

                // Center the video
                val offsetX = (windowWidth - scaledWidth) / 2.0
                val offsetY = (windowHeight - scaledHeight) / 2.0

                // Draw scaled video
                drawer.image(target.colorBuffer(0), offsetX, offsetY, scaledWidth, scaledHeight)
            }
        }
    }
}
