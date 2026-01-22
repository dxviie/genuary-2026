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
import org.openrndr.draw.loadImage
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import java.io.File
import java.net.URL

/**
 * JavaCV-based face detection with 68-point facial landmarks - Static Image Version
 *
 * This implementation uses JavaCV's Facemark LBF for precise 68-point facial landmarks.
 * Much more accurate than Haar Cascades for detailed features like:
 * - Face outline (17 points)
 * - Eyebrows (10 points: 5 each)
 * - Nose (9 points)
 * - Eyes (12 points: 6 each)
 * - Mouth (20 points: outer + inner contours)
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

        // Load static image
        println("Loading image from data/images/face-04.jpg")
        val sourceImage = loadImage("data/images/face-01.jpg")
        println("Image loaded: ${sourceImage.width}x${sourceImage.height}")

        // Calculate scaling to fit image in window
        val imageAspect = sourceImage.width.toDouble() / sourceImage.height.toDouble()
        val windowAspect = width.toDouble() / height.toDouble()

        val displayRect = if (imageAspect > windowAspect) {
            val displayWidth = width.toDouble()
            val displayHeight = width.toDouble() / imageAspect
            val displayY = (height - displayHeight) / 2.0
            Rectangle(0.0, displayY, displayWidth, displayHeight)
        } else {
            val displayHeight = height.toDouble()
            val displayWidth = height.toDouble() * imageAspect
            val displayX = (width - displayWidth) / 2.0
            Rectangle(displayX, 0.0, displayWidth, displayHeight)
        }

        println("Display rectangle: $displayRect")

        // Convert OPENRNDR ColorBuffer to JavaCV Mat
        println("Converting image to OpenCV format...")
        println("Source image: ${sourceImage.width}x${sourceImage.height}, format: ${sourceImage.format}, type: ${sourceImage.type}")
        val mat = colorBufferToJavaCVMat(sourceImage)
        println("Mat created: ${mat.cols()}x${mat.rows()}, channels: ${mat.channels()}")

        // Convert to grayscale
        val grayMat = Mat()
        opencv_imgproc.cvtColor(mat, grayMat, opencv_imgproc.COLOR_RGB2GRAY)

        // Store grayscale before histogram equalization for debugging
        val grayMatBeforeEq = Mat()
        grayMat.copyTo(grayMatBeforeEq)

        opencv_imgproc.equalizeHist(grayMat, grayMat)

        // Convert processed mats to ColorBuffers for visualization
        val grayImage = javaCVMatToColorBuffer(grayMatBeforeEq, sourceImage.width, sourceImage.height)
        val equalizedImage = javaCVMatToColorBuffer(grayMat, sourceImage.width, sourceImage.height)

        // Detect faces
        println("Detecting faces...")
        val faces = RectVector()
        faceCascade.detectMultiScale(
            grayMat,
            faces,
            1.05,  // Lower scale factor for more thorough detection
            2,     // Lower min neighbors for more permissive detection
            opencv_objdetect.CASCADE_SCALE_IMAGE,
            Size(50, 50),  // Larger min size for closeup faces
            Size(1200, 1200)
        )

        println("Detected ${faces.size()} face(s)")

        // Detect landmarks for each face
        val landmarks = Point2fVectorVector()
        val success = facemark.fit(grayMat, faces, landmarks)

        if (!success) {
            println("Warning: Facemark fit failed")
        }

        // Store landmarks for rendering
        data class FaceLandmarks(
            val faceRect: Rectangle,
            val landmarks: List<Circle>
        )

        val detectedFaces = mutableListOf<FaceLandmarks>()

        for (i in 0 until faces.size()) {
            val faceRect = faces.get(i)

            // Map face rectangle to screen space
            val faceX = displayRect.x + (faceRect.x() / sourceImage.width.toDouble()) * displayRect.width
            val faceY = displayRect.y + (faceRect.y() / sourceImage.height.toDouble()) * displayRect.height
            val faceW = (faceRect.width() / sourceImage.width.toDouble()) * displayRect.width
            val faceH = (faceRect.height() / sourceImage.height.toDouble()) * displayRect.height

            val screenFaceRect = Rectangle(faceX, faceY, faceW, faceH)

            // Get landmarks for this face (68 points)
            val landmarkPoints = mutableListOf<Circle>()

            if (i < landmarks.size()) {
                val faceLandmarks = landmarks.get(i)

                // Facemark returns a Point2fVector with 68 points
                for (j in 0L until faceLandmarks.size()) {
                    val point = faceLandmarks.get(j)
                    val x = point.x().toDouble()
                    val y = point.y().toDouble()

                    // Map landmark to screen space
                    val screenX = displayRect.x + (x / sourceImage.width.toDouble()) * displayRect.width
                    val screenY = displayRect.y + (y / sourceImage.height.toDouble()) * displayRect.height

                    landmarkPoints.add(Circle(screenX, screenY, 2.0))
                }

                println("Face $i: ${landmarkPoints.size} landmarks detected")
            }

            detectedFaces.add(FaceLandmarks(screenFaceRect, landmarkPoints))
        }

        // Clean up intermediate mats
        mat.release()
        grayMat.release()
        grayMatBeforeEq.release()
        faces.deallocate()
        landmarks.deallocate()
        faceCascade.close()
        facemark.close()

        println("Face analysis complete. 68 points per face:")
        println("  - Jaw: 0-16 (17 points)")
        println("  - Right eyebrow: 17-21 (5 points)")
        println("  - Left eyebrow: 22-26 (5 points)")
        println("  - Nose bridge: 27-30 (4 points)")
        println("  - Nose bottom: 31-35 (5 points)")
        println("  - Right eye: 36-41 (6 points)")
        println("  - Left eye: 42-47 (6 points)")
        println("  - Outer mouth: 48-59 (12 points)")
        println("  - Inner mouth: 60-67 (8 points)")
        println("\nDebug controls:")
        println("  - Press 1: Original image")
        println("  - Press 2: Grayscale (before equalization)")
        println("  - Press 3: Histogram equalized (what detector sees)")

        // Debug mode: 1 = original, 2 = grayscale, 3 = equalized
        var debugMode = 1

        keyboard.keyDown.listen {
            when (it.name) {
                "1" -> debugMode = 1
                "2" -> debugMode = 2
                "3" -> debugMode = 3
            }
            println("Debug mode: $debugMode")
        }

        extend {
            drawer.clear(ColorRGBa.BLACK)

            // Draw the image based on debug mode
            val currentImage = when (debugMode) {
                2 -> grayImage
                3 -> equalizedImage
                else -> sourceImage
            }
            drawer.image(currentImage, currentImage.bounds, displayRect)

            // Draw all detected face features
            for (face in detectedFaces) {
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
                drawer.stroke = ColorRGBa.GREEN.opacify(0.5)
                drawer.fill = null
                drawer.lineSegments(face.landmarks.subList(0, 17).zipWithNext().map {
                    org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                })

                // Right eyebrow (17-21)
                drawer.stroke = ColorRGBa.CYAN.opacify(0.5)
                drawer.lineSegments(face.landmarks.subList(17, 22).zipWithNext().map {
                    org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                })

                // Left eyebrow (22-26)
                drawer.lineSegments(face.landmarks.subList(22, 27).zipWithNext().map {
                    org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                })

                // Nose bridge (27-30)
                drawer.stroke = ColorRGBa.YELLOW.opacify(0.5)
                drawer.lineSegments(face.landmarks.subList(27, 31).zipWithNext().map {
                    org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                })

                // Nose bottom (31-35)
                drawer.lineSegments(face.landmarks.subList(31, 36).zipWithNext().map {
                    org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                })
                // Close nose bottom
                drawer.lineSegment(face.landmarks[35].center, face.landmarks[31].center)

                // Right eye (36-41)
                drawer.stroke = ColorRGBa.BLUE.opacify(0.5)
                drawer.lineSegments(face.landmarks.subList(36, 42).zipWithNext().map {
                    org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                })
                // Close right eye
                drawer.lineSegment(face.landmarks[41].center, face.landmarks[36].center)

                // Left eye (42-47)
                drawer.lineSegments(face.landmarks.subList(42, 48).zipWithNext().map {
                    org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                })
                // Close left eye
                drawer.lineSegment(face.landmarks[47].center, face.landmarks[42].center)

                // Outer mouth (48-59)
                drawer.stroke = ColorRGBa.RED.opacify(0.5)
                drawer.lineSegments(face.landmarks.subList(48, 60).zipWithNext().map {
                    org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                })
                // Close outer mouth
                drawer.lineSegment(face.landmarks[59].center, face.landmarks[48].center)

                // Inner mouth (60-67)
                if (face.landmarks.size > 67) {
                    drawer.lineSegments(face.landmarks.subList(60, 68).zipWithNext().map {
                        org.openrndr.shape.LineSegment(it.first.center, it.second.center)
                    })
                    // Close inner mouth
                    drawer.lineSegment(face.landmarks[67].center, face.landmarks[60].center)
                }
            }

            // Display info
            drawer.fill = ColorRGBa.WHITE
            drawer.stroke = null

            val modeText = when (debugMode) {
                2 -> "Grayscale (pre-equalization)"
                3 -> "Histogram Equalized (detection input)"
                else -> "Original"
            }

            drawer.text("Debug Mode: $modeText (press 1/2/3)", 20.0, 30.0)
            drawer.text("Faces detected: ${detectedFaces.size}", 20.0, 50.0)
            drawer.text("Detection params:", 20.0, 70.0)
            drawer.text("  Scale: 1.05, MinNeighbors: 2", 20.0, 90.0)
            drawer.text("  MinSize: 50x50, MaxSize: 1200x1200", 20.0, 110.0)
            drawer.text("Image: ${sourceImage.width}x${sourceImage.height}", 20.0, 130.0)
        }
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
