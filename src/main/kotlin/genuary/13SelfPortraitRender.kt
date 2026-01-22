package genuary

import org.openrndr.draw.Drawer
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import org.openrndr.color.ColorRGBa

/**
 * Data structure for a detected face with landmarks
 */
data class DetectedFace(
    val faceRect: Rectangle,
    val landmarks: List<Circle>
)

/**
 * Interpolate between two detected faces for smoothing
 */
fun interpolateFace(from: DetectedFace, to: DetectedFace, factor: Double): DetectedFace {
    // Interpolate face rectangle
    val rect = Rectangle(
        from.faceRect.x + (to.faceRect.x - from.faceRect.x) * factor,
        from.faceRect.y + (to.faceRect.y - from.faceRect.y) * factor,
        from.faceRect.width + (to.faceRect.width - from.faceRect.width) * factor,
        from.faceRect.height + (to.faceRect.height - from.faceRect.height) * factor
    )

    // Interpolate landmarks (assuming same number of landmarks)
    val landmarks = from.landmarks.zip(to.landmarks).map { (fromLandmark, toLandmark) ->
        Circle(
            fromLandmark.center.x + (toLandmark.center.x - fromLandmark.center.x) * factor,
            fromLandmark.center.y + (toLandmark.center.y - fromLandmark.center.y) * factor,
            fromLandmark.radius
        )
    }

    return DetectedFace(rect, landmarks)
}

/**
 * Interpolate between two lists of faces
 */
fun interpolateFaces(from: List<DetectedFace>, to: List<DetectedFace>, factor: Double): List<DetectedFace> {
    // Simple approach: interpolate matching indices, use 'to' for extra faces
    val minSize = minOf(from.size, to.size)
    val interpolated = (0 until minSize).map { i ->
        interpolateFace(from[i], to[i], factor)
    }

    // Add any extra faces from 'to' (new faces that appeared)
    return interpolated + to.drop(minSize)
}

/**
 * Custom render mode for face detection visualization
 *
 * This function receives all face detection data and can render it however it wants.
 *
 * @param drawer The OPENRNDR drawer for rendering
 * @param faces List of detected faces with their landmarks
 * @param displayRect The rectangle where the video/image is displayed
 */
fun renderFaceDetection(
    drawer: Drawer,
    faces: List<DetectedFace>,
    displayRect: Rectangle
) {
    // Example: Draw a creative visualization of the detected faces
    // You can customize this however you want!

    for (face in faces) {
        // Draw face outline with a different style
        drawer.stroke = ColorRGBa.MAGENTA
        drawer.strokeWeight = 3.0
        drawer.fill = ColorRGBa.MAGENTA.opacify(0.1)
        drawer.rectangle(face.faceRect)

        // Draw landmarks as larger circles
        drawer.stroke = null
        for ((index, landmark) in face.landmarks.withIndex()) {
            drawer.fill = when (index) {
                in 0..16 -> ColorRGBa.GREEN.opacify(0.9)
                in 17..21 -> ColorRGBa.CYAN.opacify(0.9)
                in 22..26 -> ColorRGBa.CYAN.opacify(0.9)
                in 27..35 -> ColorRGBa.YELLOW.opacify(0.9)
                in 36..47 -> ColorRGBa.BLUE.opacify(0.9)
                in 48..67 -> ColorRGBa.RED.opacify(0.9)
                else -> ColorRGBa.WHITE.opacify(0.9)
            }
            drawer.circle(landmark.center, 3.0)
        }
    }
}
