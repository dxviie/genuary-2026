package genuary

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.renderTarget
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.shape.Rectangle

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
                videoTarget = renderTarget(videoPlayer.height, videoPlayer.width) {
                    colorBuffer()
                }
                println("Video initialized: ${videoPlayer.width}x${videoPlayer.height}")
            }

            videoTarget?.let { target ->
                // Draw video to render target at native resolution (no rotation needed)
                drawer.withTarget(target) {
                    drawer.clear(ColorRGBa.BLACK)
                    videoPlayer.draw(drawer)
                }

                // Now scale the render target to fit the window with center crop
                val videoWidth = target.width.toDouble()
                val videoHeight = target.height.toDouble()
                val windowWidth = width.toDouble()
                val windowHeight = height.toDouble()

                if (frameCount == 1) {
                    println("Video dimensions: ${videoWidth}x${videoHeight}")
                    println("Window dimensions: ${windowWidth}x${windowHeight}")
                    println("Video aspect: ${videoWidth / videoHeight}, Window aspect: ${windowWidth / windowHeight}")
                }

                // Calculate aspect ratios
                val videoAspect = videoWidth / videoHeight
                val windowAspect = windowWidth / windowHeight

                // Calculate source crop region (what part of the video to use)
                val sourceX: Double
                val sourceY: Double
                val sourceW: Double
                val sourceH: Double

                if (videoAspect > windowAspect) {
                    // Video is wider - crop sides
                    sourceH = videoHeight
                    sourceW = videoHeight * windowAspect
                    sourceX = (videoWidth - sourceW) / 2.0
                    sourceY = 0.0
                } else {
                    // Video is taller - crop top/bottom
                    sourceW = videoWidth
                    sourceH = videoWidth / windowAspect
                    sourceX = 0.0
                    sourceY = (videoHeight - sourceH) / 2.0
                }

                // Draw with source and destination rectangles for proper scaling
                drawer.image(
                    target.colorBuffer(0),
                    Rectangle(sourceX, sourceY, sourceW, sourceH),  // crop from video
                    Rectangle(0.0, 0.0, windowWidth, windowHeight)  // fill entire window
                )
//                drawer.image(target.colorBuffer(0))
            }
        }
    }
}
