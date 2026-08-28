package render

import genuary.DetectedFace
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_face
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_objdetect
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point2fVectorVector
import org.bytedeco.opencv.opencv_core.RectVector
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier
import org.openrndr.draw.ColorBuffer
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import java.io.File
import java.net.URL

/**
 * Shared detection code for the 13 Self Portrait website renders.
 * Same pipeline as genuary/13SelfPortraitJavaCV.kt: Haar cascade for the face
 * box, then Facemark LBF for the 68 landmarks, mapped into screen space.
 */

fun fitRect(imageWidth: Int, imageHeight: Int, windowWidth: Int, windowHeight: Int): Rectangle {
    val imageAspect = imageWidth.toDouble() / imageHeight.toDouble()
    val windowAspect = windowWidth.toDouble() / windowHeight.toDouble()
    return if (imageAspect > windowAspect) {
        val displayWidth = windowWidth.toDouble()
        val displayHeight = windowWidth.toDouble() / imageAspect
        Rectangle(0.0, (windowHeight - displayHeight) / 2.0, displayWidth, displayHeight)
    } else {
        val displayHeight = windowHeight.toDouble()
        val displayWidth = windowHeight.toDouble() * imageAspect
        Rectangle((windowWidth - displayWidth) / 2.0, 0.0, displayWidth, displayHeight)
    }
}

fun detectFaces68(sourceImage: ColorBuffer, displayRect: Rectangle): List<DetectedFace> {
    val dataDir = File("data/opencv")
    dataDir.mkdirs()

    val faceCascadeFile = cachedDownload(
        dataDir,
        "haarcascade_frontalface_alt2.xml",
        "https://raw.githubusercontent.com/opencv/opencv/refs/heads/4.x/data/haarcascades/haarcascade_frontalface_alt2.xml"
    )
    val faceCascade = CascadeClassifier(faceCascadeFile.absolutePath)
    check(!faceCascade.empty()) { "Could not load face cascade" }

    val facemarkModelFile = cachedDownload(
        dataDir,
        "lbfmodel.yaml",
        "https://raw.githubusercontent.com/kurnianggoro/GSOC2017/master/data/lbfmodel.yaml"
    )
    val facemark = opencv_face.createFacemarkLBF()
    facemark.loadModel(facemarkModelFile.absolutePath)

    val mat = colorBufferToJavaCVMat(sourceImage)
    val grayMat = Mat()
    opencv_imgproc.cvtColor(mat, grayMat, opencv_imgproc.COLOR_RGB2GRAY)
    opencv_imgproc.equalizeHist(grayMat, grayMat)

    val faces = RectVector()
    faceCascade.detectMultiScale(
        grayMat, faces, 1.05, 2,
        opencv_objdetect.CASCADE_SCALE_IMAGE,
        Size(50, 50), Size(1200, 1200)
    )
    println("Detected ${faces.size()} face(s)")

    val landmarks = Point2fVectorVector()
    if (!facemark.fit(grayMat, faces, landmarks)) {
        println("Warning: Facemark fit failed")
    }

    val detectedFaces = mutableListOf<DetectedFace>()
    for (i in 0 until faces.size()) {
        val faceRect = faces.get(i)
        val screenFaceRect = Rectangle(
            displayRect.x + (faceRect.x() / sourceImage.width.toDouble()) * displayRect.width,
            displayRect.y + (faceRect.y() / sourceImage.height.toDouble()) * displayRect.height,
            (faceRect.width() / sourceImage.width.toDouble()) * displayRect.width,
            (faceRect.height() / sourceImage.height.toDouble()) * displayRect.height
        )

        val landmarkPoints = mutableListOf<Circle>()
        if (i < landmarks.size()) {
            val faceLandmarks = landmarks.get(i)
            for (j in 0L until faceLandmarks.size()) {
                val point = faceLandmarks.get(j)
                val screenX = displayRect.x + (point.x().toDouble() / sourceImage.width.toDouble()) * displayRect.width
                val screenY = displayRect.y + (point.y().toDouble() / sourceImage.height.toDouble()) * displayRect.height
                landmarkPoints.add(Circle(screenX, screenY, 2.0))
            }
        }
        detectedFaces.add(DetectedFace(screenFaceRect, landmarkPoints))
    }

    mat.release()
    grayMat.release()
    faces.deallocate()
    landmarks.deallocate()
    faceCascade.close()
    facemark.close()

    return detectedFaces
}

private fun cachedDownload(dataDir: File, filename: String, url: String): File {
    val file = File(dataDir, filename)
    if (!file.exists()) {
        println("Downloading $filename...")
        URL(url).openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return file
}

private fun colorBufferToJavaCVMat(colorBuffer: ColorBuffer): Mat {
    val width = colorBuffer.width
    val height = colorBuffer.height
    val buffer = java.nio.ByteBuffer.allocateDirect(width * height * 4)
    colorBuffer.read(buffer)
    buffer.rewind()

    val bytePointer = org.bytedeco.javacpp.BytePointer(buffer)
    val mat = Mat(height, width, opencv_core.CV_8UC4, bytePointer, (width * 4).toLong())

    val rgbMat = Mat()
    opencv_imgproc.cvtColor(mat, rgbMat, opencv_imgproc.COLOR_RGBA2RGB)

    // OPENRNDR reads bottom-left origin; OpenCV expects top-left
    val flippedMat = Mat()
    opencv_core.flip(rgbMat, flippedMat, 0)
    rgbMat.release()

    return flippedMat
}
