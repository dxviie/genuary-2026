package genuary

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.renderTarget
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.shape.Rectangle
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.opencv.objdetect.Objdetect
import java.io.File
import java.net.URL
import java.nio.ByteBuffer

/**
 * OpenCV-based face detection with Haar Cascades
 *
 * This implementation uses classical computer vision (no ML models).
 * It detects faces, eyes, nose, and mouth using Haar Cascades.
 *
 * Note: For more detailed facial landmarks (68 points), you would need:
 * 1. opencv_contrib face module (not in standard OpenCV Java)
 * 2. Facemark LBF or AAM model files
 *
 * This implementation demonstrates basic face feature detection.
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

        println("devices: ${VideoPlayerFFMPEG.listDeviceNames()}")

        val deviceName = "USB Camera VID" //"iPhoneForMojo Camera"
        val videoPlayer = VideoPlayerFFMPEG.fromDevice(
            deviceName = deviceName,
            frameRate = 30.0
        )
        videoPlayer.play()

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

        // Create render target matching video dimensions (will be created after first frame)
        var videoTarget: org.openrndr.draw.RenderTarget? = null
        var sourceCrop: Rectangle? = null
        var destRect: Rectangle? = null

        extend {
            drawer.clear(ColorRGBa.BLACK)

            // Create render target on first frame
            if (videoTarget == null && videoPlayer.width > 0 && videoPlayer.height > 0) {
                videoTarget = renderTarget(videoPlayer.width, videoPlayer.height) {
                    colorBuffer()
                }

                // Calculate crop rectangles once
                val videoWidth = videoPlayer.width.toDouble()
                val videoHeight = videoPlayer.height.toDouble()
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
                // Draw video to render target at native resolution
                drawer.withTarget(target) {
                    drawer.clear(ColorRGBa.BLACK)
                    videoPlayer.draw(drawer)
                }

                // Create a cropped ColorBuffer for the visible region only
                val croppedBuffer = renderTarget(sourceCrop!!.width.toInt(), sourceCrop!!.height.toInt()) {
                    colorBuffer()
                }

                // Draw only the cropped region to the temporary buffer
                drawer.withTarget(croppedBuffer) {
                    drawer.image(
                        target.colorBuffer(0),
                        sourceCrop!!,
                        Rectangle(0.0, 0.0, sourceCrop!!.width, sourceCrop!!.height)
                    )
                }

                // Convert only the cropped ColorBuffer to OpenCV Mat
                val mat = colorBufferToMat(croppedBuffer.colorBuffer(0))

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

                // Draw video with source and destination rectangles for proper scaling
                drawer.image(
                    target.colorBuffer(0),
                    sourceCrop!!,
                    destRect!!
                )

                // Draw face detections and features
                val faceArray = faces.toArray()

                for (faceRect in faceArray) {
                    // Map face coordinates from cropped buffer space to screen space
                    // Now coordinates are relative to the cropped buffer, which maps 1:1 to destRect
                    val faceX = destRect!!.x + (faceRect.x / croppedBuffer.width.toDouble()) * destRect!!.width
                    val faceY = destRect!!.y + (faceRect.y / croppedBuffer.height.toDouble()) * destRect!!.height
                    val faceW = (faceRect.width / croppedBuffer.width.toDouble()) * destRect!!.width
                    val faceH = (faceRect.height / croppedBuffer.height.toDouble()) * destRect!!.height

                    // Draw face rectangle
                    drawer.stroke = ColorRGBa.GREEN
                    drawer.strokeWeight = 3.0
                    drawer.fill = null
                    drawer.rectangle(faceX, faceY, faceW, faceH)

                    // Create region of interest for face features
                    val faceROI = Mat(grayMat, faceRect)

                    // Detect eyes in face region
                    val eyes = MatOfRect()
                    eyeCascade.detectMultiScale(faceROI, eyes, 1.1, 3, 0, Size(20.0, 20.0), Size())
                    val eyeArray = eyes.toArray()

                    drawer.fill = ColorRGBa.CYAN.opacify(0.7)
                    drawer.stroke = ColorRGBa.CYAN
                    drawer.strokeWeight = 2.0

                    for (eyeRect in eyeArray) {
                        val eyeX = faceX + (eyeRect.x / croppedBuffer.width.toDouble()) * destRect!!.width
                        val eyeY = faceY + (eyeRect.y / croppedBuffer.height.toDouble()) * destRect!!.height
                        val eyeW = (eyeRect.width / croppedBuffer.width.toDouble()) * destRect!!.width
                        val eyeH = (eyeRect.height / croppedBuffer.height.toDouble()) * destRect!!.height
                        drawer.rectangle(eyeX, eyeY, eyeW, eyeH)

                        // Draw center point of eye
                        val eyeCenterX = eyeX + eyeW / 2.0
                        val eyeCenterY = eyeY + eyeH / 2.0
                        drawer.circle(eyeCenterX, eyeCenterY, 4.0)
                    }

                    // Detect nose in lower face region
                    val noseROI = Mat(faceROI, Rect(0, faceRect.height / 3, faceRect.width, faceRect.height * 2 / 3))
                    val noses = MatOfRect()
                    noseCascade.detectMultiScale(noseROI, noses, 1.1, 3, 0, Size(15.0, 15.0), Size())
                    val noseArray = noses.toArray()

                    drawer.fill = ColorRGBa.YELLOW.opacify(0.7)
                    drawer.stroke = ColorRGBa.YELLOW
                    drawer.strokeWeight = 2.0

                    if (noseArray.isNotEmpty()) {
                        // Only draw the first nose detected
                        val noseRect = noseArray[0]
                        val noseX = faceX + (noseRect.x / croppedBuffer.width.toDouble()) * destRect!!.width
                        val noseY = faceY + ((noseRect.y + faceRect.height / 3) / croppedBuffer.height.toDouble()) * destRect!!.height
                        val noseW = (noseRect.width / croppedBuffer.width.toDouble()) * destRect!!.width
                        val noseH = (noseRect.height / croppedBuffer.height.toDouble()) * destRect!!.height
                        drawer.rectangle(noseX, noseY, noseW, noseH)

                        // Draw center point of nose
                        val noseCenterX = noseX + noseW / 2.0
                        val noseCenterY = noseY + noseH / 2.0
                        drawer.circle(noseCenterX, noseCenterY, 5.0)
                    }

                    // Detect mouth in lower face region
                    val mouthROI = Mat(faceROI, Rect(0, faceRect.height * 2 / 3, faceRect.width, faceRect.height / 3))
                    val mouths = MatOfRect()
                    mouthCascade.detectMultiScale(mouthROI, mouths, 1.1, 3, 0, Size(20.0, 20.0), Size())
                    val mouthArray = mouths.toArray()

                    drawer.fill = ColorRGBa.RED.opacify(0.7)
                    drawer.stroke = ColorRGBa.RED
                    drawer.strokeWeight = 2.0

                    if (mouthArray.isNotEmpty()) {
                        // Only draw the first mouth detected
                        val mouthRect = mouthArray[0]
                        val mouthX = faceX + (mouthRect.x / croppedBuffer.width.toDouble()) * destRect!!.width
                        val mouthY = faceY + ((mouthRect.y + faceRect.height * 2 / 3) / croppedBuffer.height.toDouble()) * destRect!!.height
                        val mouthW = (mouthRect.width / croppedBuffer.width.toDouble()) * destRect!!.width
                        val mouthH = (mouthRect.height / croppedBuffer.height.toDouble()) * destRect!!.height
                        drawer.rectangle(mouthX, mouthY, mouthW, mouthH)
                    }

                    faceROI.release()
                    noseROI.release()
                    mouthROI.release()
                    eyes.release()
                    noses.release()
                    mouths.release()
                }

                // Display info
                drawer.fill = ColorRGBa.WHITE
                drawer.text("Faces detected (OpenCV): ${faceArray.size}", 20.0, 30.0)
                drawer.text("Method: Haar Cascades (Classical CV)", 20.0, 50.0)

                // Clean up
                croppedBuffer.colorBuffer(0).destroy()
                croppedBuffer.destroy()
                mat.release()
                grayMat.release()
                faces.release()
            }
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
