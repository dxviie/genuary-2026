package genuary

import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_face
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_objdetect
import org.bytedeco.opencv.opencv_core.*
import org.bytedeco.opencv.opencv_face.Facemark
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier
import org.bytedeco.javacpp.indexer.UByteIndexer
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.renderTarget
import org.openrndr.extra.fx.color.ChromaticAberration
import org.openrndr.extra.fx.distort.Fisheye
import org.openrndr.extra.fx.distort.VideoGlitch
import org.openrndr.extra.fx.dither.Crosshatch
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import java.io.File
import java.net.URL

/**
 * JavaCV-based face detection with 68-point facial landmarks - Live Video Version
 *
 * This implementation uses JavaCV's Facemark LBF for precise 68-point facial landmarks
 * on live video input from a camera.
 */
fun main() = application {
    configure {
        width = 841
        height = 1189
        if (displays.size > 1) display = displays[1]
        vsync = true
    }

    program {
        println("Initializing JavaCV...")

        // Download and load Haar Cascade for initial face detection
        val dataDir = File("data/opencv")
        dataDir.mkdirs()

        println("Loading Haar Cascade classifier...")
        val faceCascadeFile = downloadCascade(
            dataDir,
            "haarcascade_frontalface_alt2.xml",
            "https://raw.githubusercontent.com/opencv/opencv/refs/heads/4.x/data/haarcascades/haarcascade_frontalface_alt2.xml"
        )

        val faceCascade = CascadeClassifier(faceCascadeFile.absolutePath)
        if (faceCascade.empty()) {
            println("Error loading face cascade")
            return@program
        }
        println("Face cascade loaded successfully")

        // Download and load Facemark LBF model for 68-point landmarks
        println("Loading Facemark LBF model...")
        val facemarkModelFile = downloadCascade(
            dataDir,
            "lbfmodel.yaml",
            "https://raw.githubusercontent.com/kurnianggoro/GSOC2017/master/data/lbfmodel.yaml"
        )

        val facemark: Facemark = opencv_face.createFacemarkLBF()
        facemark.loadModel(facemarkModelFile.absolutePath)
        println("Facemark model loaded successfully")

        // Initialize video player
        println("devices: ${VideoPlayerFFMPEG.listDeviceNames()}")
        val deviceName = "iPhoneForMojo Camera"
        val videoPlayer = VideoPlayerFFMPEG.fromDevice(
            deviceName = deviceName,
            frameRate = 30.0
        )
        videoPlayer.play()

        // Create render target matching video dimensions
        var videoTarget: org.openrndr.draw.RenderTarget? = null
        var sourceCrop: Rectangle? = null
        var destRect: Rectangle? = null

        // Debug mode: 1 = original, 2 = grayscale, 3 = equalized, 4 = custom render
        var debugMode = 1

        // Store previous face data for smoothing and persistence
        var previousFaces = listOf<DetectedFace>()
        val smoothingFactor = 0.3 // 0 = use only previous, 1 = use only current

        // Post-processing effects
        val chromaticAberration = ChromaticAberration()
        val crosshatchDither = Crosshatch()
        val videoGlitch = VideoGlitch()
        val fisheye = Fisheye()
        var useEffects = false

        // Create render target for post-processing
        val postTarget = renderTarget(width, height) {
            colorBuffer()
        }

        keyboard.keyDown.listen {
            when (it.name) {
                "1" -> {
                    debugMode = 1
                    println("Debug mode: $debugMode")
                }
                "2" -> {
                    debugMode = 2
                    println("Debug mode: $debugMode")
                }
                "3" -> {
                    debugMode = 3
                    println("Debug mode: $debugMode")
                }
                "4" -> {
                    debugMode = 4
                    println("Debug mode: $debugMode")
                }
                "e" -> {
                    useEffects = !useEffects
                    println("Effects: ${if (useEffects) "ON" else "OFF"}")
                }
            }
        }

        println("\nControls:")
        println("  - Press 1: Original video")
        println("  - Press 2: Grayscale (before equalization)")
        println("  - Press 3: Histogram equalized (what detector sees)")
        println("  - Press 4: Custom render mode")
        println("  - Press E: Toggle post-processing effects (fisheye + chromatic aberration + video glitch + crosshatch)")

        extend {
            // Create render target on first frame (dimensions swapped because video is rotated)
            if (videoTarget == null && videoPlayer.width > 0 && videoPlayer.height > 0) {
                videoTarget = renderTarget(videoPlayer.height, videoPlayer.width) {
                    colorBuffer()
                }

                // Video dimensions are swapped (rotated)
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

                // Calculate source crop region
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

            videoTarget?.let { target ->
                // Draw video to render target to consume frames (flipped horizontally)
                drawer.withTarget(target) {
                    drawer.clear(ColorRGBa.BLACK)
                    // Flip horizontally for mirror effect
                    drawer.pushTransforms()
                    drawer.translate(target.width.toDouble(), 0.0)
                    drawer.scale(-1.0, 1.0)
                    videoPlayer.draw(drawer)
                    drawer.popTransforms()
                }

                val videoFrame = target.colorBuffer(0)

                // Convert to OpenCV Mat for face detection
                val mat = colorBufferToJavaCVMat(videoFrame)

                // Convert to grayscale
                val grayMat = Mat()
                opencv_imgproc.cvtColor(mat, grayMat, opencv_imgproc.COLOR_RGB2GRAY)

                // Store grayscale before histogram equalization for debugging
                val grayMatBeforeEq = Mat()
                grayMat.copyTo(grayMatBeforeEq)

                opencv_imgproc.equalizeHist(grayMat, grayMat)

                // Detect faces
                val faces = RectVector()
                faceCascade.detectMultiScale(
                    grayMat,
                    faces,
                    1.1,   // Increase scale factor for faster, less sensitive detection
                    10,     // Increase min neighbors to reduce false positives
                    opencv_objdetect.CASCADE_SCALE_IMAGE,
                    Size(80, 120),  // Increase min size to filter out tiny false detections
                    Size(1000, 1500)
                )

                // Detect landmarks for each face
                val landmarks = Point2fVectorVector()
                val success = facemark.fit(grayMat, faces, landmarks)

                // Store detected faces and landmarks
                val detectedFaces = mutableListOf<DetectedFace>()

                for (i in 0 until faces.size()) {
                    val faceRect = faces.get(i)

                    // Map face rectangle to cropped video space, then to screen space
                    val faceX = sourceCrop!!.x + (faceRect.x() / videoFrame.width.toDouble()) * sourceCrop!!.width
                    val faceY = sourceCrop!!.y + (faceRect.y() / videoFrame.height.toDouble()) * sourceCrop!!.height
                    val faceW = (faceRect.width() / videoFrame.width.toDouble()) * sourceCrop!!.width
                    val faceH = (faceRect.height() / videoFrame.height.toDouble()) * sourceCrop!!.height

                    // Map from source crop to destination rectangle
                    val screenFaceX = destRect!!.x + ((faceX - sourceCrop!!.x) / sourceCrop!!.width) * destRect!!.width
                    val screenFaceY = destRect!!.y + ((faceY - sourceCrop!!.y) / sourceCrop!!.height) * destRect!!.height
                    val screenFaceW = (faceW / sourceCrop!!.width) * destRect!!.width
                    val screenFaceH = (faceH / sourceCrop!!.height) * destRect!!.height

                    val screenFaceRect = Rectangle(screenFaceX, screenFaceY, screenFaceW, screenFaceH)

                    // Get landmarks for this face (68 points)
                    val landmarkPoints = mutableListOf<Circle>()

                    if (i < landmarks.size()) {
                        val faceLandmarks = landmarks.get(i)

                        for (j in 0L until faceLandmarks.size()) {
                            val point = faceLandmarks.get(j)
                            val x = point.x().toDouble()
                            val y = point.y().toDouble()

                            // Map landmark to screen space (same as face rectangle)
                            val cropX = sourceCrop!!.x + (x / videoFrame.width.toDouble()) * sourceCrop!!.width
                            val cropY = sourceCrop!!.y + (y / videoFrame.height.toDouble()) * sourceCrop!!.height
                            val screenX = destRect!!.x + ((cropX - sourceCrop!!.x) / sourceCrop!!.width) * destRect!!.width
                            val screenY = destRect!!.y + ((cropY - sourceCrop!!.y) / sourceCrop!!.height) * destRect!!.height

                            landmarkPoints.add(Circle(screenX, screenY, 2.0))
                        }
                    }

                    detectedFaces.add(DetectedFace(screenFaceRect, landmarkPoints))
                }

                // Apply smoothing and persistence
                val smoothedFaces = when {
                    detectedFaces.isEmpty() && previousFaces.isNotEmpty() -> {
                        // No faces detected, use previous data
                        previousFaces
                    }
                    detectedFaces.isNotEmpty() && previousFaces.isNotEmpty() -> {
                        // Interpolate between previous and current for smoothing
                        interpolateFaces(previousFaces, detectedFaces, smoothingFactor)
                    }
                    else -> {
                        // First detection or no previous data
                        detectedFaces
                    }
                }

                // Store for next frame
                previousFaces = smoothedFaces

                // Prepare debug images if needed
                val currentImage = when (debugMode) {
                    2 -> javaCVMatToColorBuffer(grayMatBeforeEq, videoFrame.width, videoFrame.height)
                    3 -> javaCVMatToColorBuffer(grayMat, videoFrame.width, videoFrame.height)
                    else -> videoFrame
                }

                // Render scene to target for post-processing
                drawer.withTarget(postTarget) {
                    drawer.clear(ColorRGBa.BLACK)

                    // Draw based on mode
                if (debugMode == 4) {
                    // Mode 4: Custom render from separate file
                    renderFaceDetection(drawer, smoothedFaces, destRect!!)
                } else {
                    // Modes 1-3: Draw video with standard face detection overlay
                    drawer.image(
                        currentImage,
                        sourceCrop!!,
                        destRect!!
                    )

                    // Draw all detected face features
                    for (face in smoothedFaces) {
                    // Draw face rectangle
                    drawer.stroke = ColorRGBa.GREEN
                    drawer.strokeWeight = 2.0
                    drawer.fill = null
                    drawer.rectangle(face.faceRect)

                    // Draw all 68 landmarks
                    drawer.fill = ColorRGBa.WHITE.opacify(0.8)
                    drawer.stroke = null

                    for ((index, landmark) in face.landmarks.withIndex()) {
                        // Color-code different facial features
                        drawer.fill = when (index) {
                            in 0..16 -> ColorRGBa.GREEN.opacify(0.7) // Jaw
                            in 17..21 -> ColorRGBa.CYAN.opacify(0.8) // Right eyebrow
                            in 22..26 -> ColorRGBa.CYAN.opacify(0.8) // Left eyebrow
                            in 27..35 -> ColorRGBa.YELLOW.opacify(0.8) // Nose
                            in 36..47 -> ColorRGBa.BLUE.opacify(0.9) // Eyes
                            in 48..67 -> ColorRGBa.RED.opacify(0.8) // Mouth
                            else -> ColorRGBa.WHITE.opacify(0.6)
                        }
                        drawer.circle(landmark)
                    }

                    // Draw contour lines connecting landmarks
                    drawer.strokeWeight = 1.5

                    // Jaw outline (0-16)
                    if (face.landmarks.size > 16) {
                        drawer.stroke = ColorRGBa.GREEN.opacify(0.5)
                        drawer.fill = null
                        drawer.lineSegments(face.landmarks.subList(0, 17).zipWithNext().map {
                            org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                        })
                    }

                    // Eyebrows
                    if (face.landmarks.size > 26) {
                        drawer.stroke = ColorRGBa.CYAN.opacify(0.5)
                        // Right eyebrow (17-21)
                        drawer.lineSegments(face.landmarks.subList(17, 22).zipWithNext().map {
                            org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                        })
                        // Left eyebrow (22-26)
                        drawer.lineSegments(face.landmarks.subList(22, 27).zipWithNext().map {
                            org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                        })
                    }

                    // Eyes
                    if (face.landmarks.size > 47) {
                        drawer.stroke = ColorRGBa.BLUE.opacify(0.5)
                        // Right eye (36-41)
                        drawer.lineSegments(face.landmarks.subList(36, 42).zipWithNext().map {
                            org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                        })
                        drawer.lineSegment(face.landmarks[41].center, face.landmarks[36].center)
                        // Left eye (42-47)
                        drawer.lineSegments(face.landmarks.subList(42, 48).zipWithNext().map {
                            org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                        })
                        drawer.lineSegment(face.landmarks[47].center, face.landmarks[42].center)
                    }

                    // Mouth
                    if (face.landmarks.size > 59) {
                        drawer.stroke = ColorRGBa.RED.opacify(0.5)
                        // Outer mouth (48-59)
                        drawer.lineSegments(face.landmarks.subList(48, 60).zipWithNext().map {
                            org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                        })
                        drawer.lineSegment(face.landmarks[59].center, face.landmarks[48].center)

                        // Inner mouth (60-67)
                        if (face.landmarks.size > 67) {
                            drawer.lineSegments(face.landmarks.subList(60, 68).zipWithNext().map {
                                org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                            })
                            drawer.lineSegment(face.landmarks[67].center, face.landmarks[60].center)
                        }
                    }
                    }
                }
                }

                // Apply post-processing effects and draw to screen
                drawer.clear(ColorRGBa.BLACK)
                if (useEffects) {
                    // Apply fisheye distortion
                    fisheye.apply(postTarget.colorBuffer(0), postTarget.colorBuffer(0))

                    // Apply video glitch
                    videoGlitch.time = seconds
                    videoGlitch.amplitude = 0.05
                    videoGlitch.vfreq = 0.0
                    videoGlitch.pfreq = 1.0
                    videoGlitch.hfreq = 1.0
                    videoGlitch.poffset = 0.0
                    videoGlitch.scrollOffset0 = 0.0
                    videoGlitch.scrollOffset1 = 0.0
                    videoGlitch.borderHeight = 0.05
                    videoGlitch.apply(postTarget.colorBuffer(0), postTarget.colorBuffer(0))

                    // Apply chromatic aberration
                    chromaticAberration.aberrationFactor = 3.0
                    chromaticAberration.apply(postTarget.colorBuffer(0), postTarget.colorBuffer(0))

                    // Apply crosshatch dither
//                    crosshatchDither.apply(postTarget.colorBuffer(0), postTarget.colorBuffer(0))
                }
                drawer.image(postTarget.colorBuffer(0))

                // Display info
                drawer.fill = ColorRGBa.WHITE
                drawer.stroke = null

                val modeText = when (debugMode) {
                    2 -> "Grayscale (pre-equalization)"
                    3 -> "Histogram Equalized (detection input)"
                    4 -> "Custom Render"
                    else -> "Original"
                }

                drawer.text("Debug Mode: $modeText (press 1/2/3/4)", 20.0, 30.0)
                drawer.text("Effects: ${if (useEffects) "ON" else "OFF"} (press E)", 20.0, 50.0)
                drawer.text("Faces: ${smoothedFaces.size} (raw: ${detectedFaces.size})", 20.0, 70.0)
                drawer.text("Landmarks per face: 68", 20.0, 90.0)
                drawer.text("FPS: ${frameCount / seconds}", 20.0, 110.0)

                // Clean up
                mat.release()
                grayMat.release()
                grayMatBeforeEq.release()
                faces.deallocate()
                landmarks.deallocate()
            }
        }

        // Note: Resources (videoPlayer, faceCascade, facemark) are kept alive
        // for the entire program duration and cleaned up on JVM exit
    }
}

/**
 * Downloads a cascade/model file if it doesn't exist locally
 */
private fun downloadCascade(dataDir: File, filename: String, url: String): File {
    val file = File(dataDir, filename)
    if (!file.exists()) {
        println("Downloading $filename... (this may take a while for model files)")
        URL(url).openStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        println("Downloaded $filename")
    } else {
        println("Using cached $filename")
    }
    return file
}

/**
 * Converts an OPENRNDR ColorBuffer to a JavaCV Mat
 * Flips vertically because OPENRNDR data is bottom-left origin, OpenCV expects top-left
 */
private fun colorBufferToJavaCVMat(colorBuffer: ColorBuffer): Mat {
    val width = colorBuffer.width
    val height = colorBuffer.height
    val buffer = java.nio.ByteBuffer.allocateDirect(width * height * 4)

    // Read pixels from ColorBuffer
    colorBuffer.read(buffer)
    buffer.rewind()

    // Create Mat (RGBA format) with data from ByteBuffer
    val bytePointer = org.bytedeco.javacpp.BytePointer(buffer)
    val mat = Mat(height, width, opencv_core.CV_8UC4, bytePointer, (width * 4).toLong())

    // Convert RGBA to RGB
    val rgbMat = Mat()
    opencv_imgproc.cvtColor(mat, rgbMat, opencv_imgproc.COLOR_RGBA2RGB)

    // Flip vertically so OpenCV sees the image right-side up
    val flippedMat = Mat()
    opencv_core.flip(rgbMat, flippedMat, 0)  // 0 = flip around x-axis (vertical flip)
    rgbMat.release()

    return flippedMat
}

/**
 * Converts a JavaCV Mat (grayscale or RGB) to an OPENRNDR ColorBuffer
 * Note: The Mat is already in the correct orientation (flipped during input conversion)
 */
private fun javaCVMatToColorBuffer(mat: Mat, width: Int, height: Int): ColorBuffer {
    val colorBuffer = org.openrndr.draw.colorBuffer(width, height)
    val shadow = colorBuffer.shadow

    val indexer = mat.createIndexer<UByteIndexer>()
    val channels = mat.channels()

    for (y in 0 until height) {
        for (x in 0 until width) {
            val value = if (channels == 1) {
                // Grayscale
                val gray = (indexer.get(y.toLong(), x.toLong(), 0) and 0xFF) / 255.0
                ColorRGBa(gray, gray, gray, 1.0)
            } else {
                // RGB
                val r = (indexer.get(y.toLong(), x.toLong(), 0) and 0xFF) / 255.0
                val g = (indexer.get(y.toLong(), x.toLong(), 1) and 0xFF) / 255.0
                val b = (indexer.get(y.toLong(), x.toLong(), 2) and 0xFF) / 255.0
                ColorRGBa(r, g, b, 1.0)
            }
            shadow[x, y] = value
        }
    }

    indexer.release()
    shadow.upload()

    return colorBuffer
}
