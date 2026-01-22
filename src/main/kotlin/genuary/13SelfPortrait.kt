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
        var sourceCrop: Rectangle? = null
        var destRect: Rectangle? = null

        extend {
            drawer.clear(ColorRGBa.BLACK)

            // Create render target on first frame
            if (videoTarget == null && videoPlayer.width > 0 && videoPlayer.height > 0) {
                videoTarget = renderTarget(videoPlayer.height, videoPlayer.width) {
                    colorBuffer()
                }

                // Calculate crop rectangles once
                val videoWidth = videoPlayer.height.toDouble()
                val videoHeight = videoPlayer.width.toDouble()
                val windowWidth = width.toDouble()
                val windowHeight = height.toDouble()

                println("Video initialized: ${videoPlayer.width}x${videoPlayer.height}")
                println("Video dimensions (target): ${videoWidth}x${videoHeight}")
                println("Window dimensions: ${windowWidth}x${windowHeight}")

                // Calculate aspect ratios
                val videoAspect = videoWidth / videoHeight
                val windowAspect = windowWidth / windowHeight

                println("Video aspect: $videoAspect, Window aspect: $windowAspect")

                // Calculate source crop region (what part of the video to use)
                if (videoAspect > windowAspect) {
                    // Video is wider - crop sides
                    val sourceH = videoHeight
                    val sourceW = videoHeight * windowAspect
                    val sourceX = (videoWidth - sourceW) / 2.0
                    val sourceY = 0.0
                    sourceCrop = Rectangle(sourceX, sourceY, sourceW, sourceH)
                } else {
                    // Video is taller - crop top/bottom
                    val sourceW = videoWidth
                    val sourceH = videoWidth / windowAspect
                    val sourceX = 0.0
                    val sourceY = (videoHeight - sourceH) / 2.0
                    sourceCrop = Rectangle(sourceX, sourceY, sourceW, sourceH)
                }

                destRect = Rectangle(0.0, 0.0, windowWidth, windowHeight)

                println("Source crop: $sourceCrop")
                println("Destination: $destRect")
            }

            // Draw video each frame using pre-calculated rectangles
            videoTarget?.let { target ->
                // Draw video to render target at native resolution (no rotation needed)
                drawer.withTarget(target) {
                    drawer.clear(ColorRGBa.BLACK)
                    videoPlayer.draw(drawer)
                }

                // Draw with source and destination rectangles for proper scaling
                drawer.image(
                    target.colorBuffer(0),
                    sourceCrop!!,  // crop from video
                    destRect!!     // fill entire window
                )
            }
        }
    }
}
