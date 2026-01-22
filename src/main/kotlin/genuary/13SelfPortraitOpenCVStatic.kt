package genuary

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.loadImage
import org.openrndr.shape.Rectangle
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.opencv.objdetect.Objdetect
import java.io.File
import java.net.URL
import java.nio.ByteBuffer

/**
 * OpenCV-based face detection with Haar Cascades - Static Image Version
 *
 * This implementation analyzes a static image for face features.
 * It detects faces, eyes, nose, and mouth using Haar Cascades.
 */
fun main() = application {
    configure {
        width = 841
        height = 1189
        if (displays.size > 1) display = displays[1]
        vsync = true
    }

    program {
        // Load OpenCV native library
        nu.pattern.OpenCV.loadLocally()

        // Download and load Haar Cascade classifiers
        val dataDir = File("data/opencv")
        dataDir.mkdirs()

        println("Loading Haar Cascade classifiers...")

        val faceCascadeFile = downloadHaarCascade(
            dataDir,
            "haarcascade_frontalface_default.xml",
            "https://raw.githubusercontent.com/opencv/opencv/refs/heads/4.x/data/haarcascades/haarcascade_frontalface_default.xml"
        )
        val eyeCascadeFile = downloadHaarCascade(
            dataDir,
            "haarcascade_eye.xml",
            "https://raw.githubusercontent.com/opencv/opencv/refs/heads/4.x/data/haarcascades/haarcascade_eye.xml"
        )
        val noseCascadeFile = downloadHaarCascade(
            dataDir,
            "haarcascade_mcs_nose.xml",
            "https://raw.githubusercontent.com/otsedom/ViolaJonesCascades/master/Cascades/haarcascade_mcs_nose.xml"
        )
        val mouthCascadeFile = downloadHaarCascade(
            dataDir,
            "haarcascade_mcs_mouth.xml",
            "https://raw.githubusercontent.com/otsedom/ViolaJonesCascades/master/Cascades/haarcascade_mcs_mouth.xml"
        )

        // Initialize classifiers
        val faceCascade = CascadeClassifier(faceCascadeFile.absolutePath)
        val eyeCascade = CascadeClassifier(eyeCascadeFile.absolutePath)
        val noseCascade = CascadeClassifier(noseCascadeFile.absolutePath)
        val mouthCascade = CascadeClassifier(mouthCascadeFile.absolutePath)

        if (faceCascade.empty()) {
            println("Error loading face cascade")
        } else {
            println("Face cascade loaded successfully")
        }

        // Load static image
        println("Loading image from data/images/face-01.jpg")
        val sourceImage = loadImage("data/images/face-01.jpg")
        println("Image loaded: ${sourceImage.width}x${sourceImage.height}")

        // Calculate scaling to fit image in window while maintaining aspect ratio
        val imageAspect = sourceImage.width.toDouble() / sourceImage.height.toDouble()
        val windowAspect = width.toDouble() / height.toDouble()

        val displayRect = if (imageAspect > windowAspect) {
            // Image is wider - fit to width
            val displayWidth = width.toDouble()
            val displayHeight = width.toDouble() / imageAspect
            val displayY = (height - displayHeight) / 2.0
            Rectangle(0.0, displayY, displayWidth, displayHeight)
        } else {
            // Image is taller - fit to height
            val displayHeight = height.toDouble()
            val displayWidth = height.toDouble() * imageAspect
            val displayX = (width - displayWidth) / 2.0
            Rectangle(displayX, 0.0, displayWidth, displayHeight)
        }

        println("Display rectangle: $displayRect")

        // Perform face detection once at startup
        println("Analyzing image for faces...")
        val mat = colorBufferToMat(sourceImage)

        // Convert to grayscale for better detection
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)
        Imgproc.equalizeHist(grayMat, grayMat)

        // Detect faces
        val faces = MatOfRect()
        faceCascade.detectMultiScale(
            grayMat,
            faces,
            1.1,
            3,
            Objdetect.CASCADE_SCALE_IMAGE,
            Size(30.0, 30.0),
            Size()
        )

        val faceArray = faces.toArray()
        println("Detected ${faceArray.size} face(s)")

        // Store face feature data for rendering
        data class FaceFeatures(
            val faceRect: Rectangle,
            val eyes: List<Rectangle>,
            val nose: Rectangle?,
            val mouth: Rectangle?
        )

        val detectedFaces = mutableListOf<FaceFeatures>()

        for (faceRect in faceArray) {
            // Map face coordinates to screen space
            val faceX = displayRect.x + (faceRect.x / sourceImage.width.toDouble()) * displayRect.width
            val faceY = displayRect.y + (faceRect.y / sourceImage.height.toDouble()) * displayRect.height
            val faceW = (faceRect.width / sourceImage.width.toDouble()) * displayRect.width
            val faceH = (faceRect.height / sourceImage.height.toDouble()) * displayRect.height

            val screenFaceRect = Rectangle(faceX, faceY, faceW, faceH)

            // Create region of interest for face features
            val faceROI = Mat(grayMat, faceRect)

            // Detect eyes in face region
            val eyes = MatOfRect()
            eyeCascade.detectMultiScale(faceROI, eyes, 1.1, 3, 0, Size(20.0, 20.0), Size())
            val eyeArray = eyes.toArray()

            val eyeRectangles = eyeArray.map { eyeRect ->
                val eyeX = faceX + (eyeRect.x / sourceImage.width.toDouble()) * displayRect.width
                val eyeY = faceY + (eyeRect.y / sourceImage.height.toDouble()) * displayRect.height
                val eyeW = (eyeRect.width / sourceImage.width.toDouble()) * displayRect.width
                val eyeH = (eyeRect.height / sourceImage.height.toDouble()) * displayRect.height
                Rectangle(eyeX, eyeY, eyeW, eyeH)
            }

            // Detect nose in lower face region
            val noseROI = Mat(faceROI, Rect(0, faceRect.height / 3, faceRect.width, faceRect.height * 2 / 3))
            val noses = MatOfRect()
            noseCascade.detectMultiScale(noseROI, noses, 1.1, 3, 0, Size(15.0, 15.0), Size())
            val noseArray = noses.toArray()

            val noseRectangle = if (noseArray.isNotEmpty()) {
                val noseRect = noseArray[0]
                val noseX = faceX + (noseRect.x / sourceImage.width.toDouble()) * displayRect.width
                val noseY = faceY + ((noseRect.y + faceRect.height / 3) / sourceImage.height.toDouble()) * displayRect.height
                val noseW = (noseRect.width / sourceImage.width.toDouble()) * displayRect.width
                val noseH = (noseRect.height / sourceImage.height.toDouble()) * displayRect.height
                Rectangle(noseX, noseY, noseW, noseH)
            } else null

            // Detect mouth in lower face region
            val mouthROI = Mat(faceROI, Rect(0, faceRect.height * 2 / 3, faceRect.width, faceRect.height / 3))
            val mouths = MatOfRect()
            mouthCascade.detectMultiScale(mouthROI, mouths, 1.1, 3, 0, Size(20.0, 20.0), Size())
            val mouthArray = mouths.toArray()

            val mouthRectangle = if (mouthArray.isNotEmpty()) {
                val mouthRect = mouthArray[0]
                val mouthX = faceX + (mouthRect.x / sourceImage.width.toDouble()) * displayRect.width
                val mouthY = faceY + ((mouthRect.y + faceRect.height * 2 / 3) / sourceImage.height.toDouble()) * displayRect.height
                val mouthW = (mouthRect.width / sourceImage.width.toDouble()) * displayRect.width
                val mouthH = (mouthRect.height / sourceImage.height.toDouble()) * displayRect.height
                Rectangle(mouthX, mouthY, mouthW, mouthH)
            } else null

            detectedFaces.add(FaceFeatures(screenFaceRect, eyeRectangles, noseRectangle, mouthRectangle))

            // Clean up ROIs
            faceROI.release()
            noseROI.release()
            mouthROI.release()
            eyes.release()
            noses.release()
            mouths.release()
        }

        // Clean up OpenCV matrices
        mat.release()
        grayMat.release()
        faces.release()

        println("Face analysis complete. Press ESC to exit.")

        extend {
            drawer.clear(ColorRGBa.BLACK)

            // Draw the image scaled to fit window
            drawer.image(sourceImage, sourceImage.bounds, displayRect)

            // Draw all detected face features
            for (face in detectedFaces) {
                // Draw face rectangle
                drawer.stroke = ColorRGBa.GREEN
                drawer.strokeWeight = 3.0
                drawer.fill = null
                drawer.rectangle(face.faceRect)

                // Draw eyes
                drawer.fill = ColorRGBa.CYAN.opacify(0.7)
                drawer.stroke = ColorRGBa.CYAN
                drawer.strokeWeight = 2.0

                for (eyeRect in face.eyes) {
                    drawer.rectangle(eyeRect)
                    // Draw center point of eye
                    val eyeCenterX = eyeRect.x + eyeRect.width / 2.0
                    val eyeCenterY = eyeRect.y + eyeRect.height / 2.0
                    drawer.circle(eyeCenterX, eyeCenterY, 4.0)
                }

                // Draw nose
                face.nose?.let { noseRect ->
                    drawer.fill = ColorRGBa.YELLOW.opacify(0.7)
                    drawer.stroke = ColorRGBa.YELLOW
                    drawer.strokeWeight = 2.0
                    drawer.rectangle(noseRect)

                    // Draw center point of nose
                    val noseCenterX = noseRect.x + noseRect.width / 2.0
                    val noseCenterY = noseRect.y + noseRect.height / 2.0
                    drawer.circle(noseCenterX, noseCenterY, 5.0)
                }

                // Draw mouth
                face.mouth?.let { mouthRect ->
                    drawer.fill = ColorRGBa.RED.opacify(0.7)
                    drawer.stroke = ColorRGBa.RED
                    drawer.strokeWeight = 2.0
                    drawer.rectangle(mouthRect)
                }
            }

            // Display info
            drawer.fill = ColorRGBa.WHITE
            drawer.stroke = null
            drawer.text("Faces detected: ${detectedFaces.size}", 20.0, 30.0)
            drawer.text("Image: data/images/face-01.jpg", 20.0, 50.0)
            drawer.text("Method: Haar Cascades (OpenCV)", 20.0, 70.0)
        }
    }
}

/**
 * Downloads a Haar Cascade XML file if it doesn't exist locally
 */
private fun downloadHaarCascade(dataDir: File, filename: String, url: String): File {
    val file = File(dataDir, filename)
    if (!file.exists()) {
        println("Downloading $filename...")
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
 * Converts an OPENRNDR ColorBuffer to an OpenCV Mat
 * Assumes ColorBuffer format is RGB or RGBA
 */
private fun colorBufferToMat(colorBuffer: ColorBuffer): Mat {
    val width = colorBuffer.width
    val height = colorBuffer.height
    val buffer = ByteBuffer.allocateDirect(width * height * 4)

    // Read pixels from ColorBuffer
    colorBuffer.read(buffer)
    buffer.rewind()

    // Create Mat and copy data from direct buffer
    val mat = Mat(height, width, CvType.CV_8UC4)

    // Copy data from direct ByteBuffer to byte array
    val byteArray = ByteArray(width * height * 4)
    buffer.get(byteArray)
    mat.put(0, 0, byteArray)

    // Convert RGBA to RGB
    val rgbMat = Mat()
    Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_RGBA2RGB)
    mat.release()

    return rgbMat
}
