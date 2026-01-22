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
 * Render the jaw outline (landmarks 0-16)
 */
fun renderJaw(drawer: Drawer, landmarks: List<Circle>) {
    if (landmarks.size < 17) return

    val jawLandmarks = landmarks.subList(0, 17)

    drawer.fill = ColorRGBa.GREEN.opacify(0.9)
    drawer.stroke = null
    for (landmark in jawLandmarks) {
        drawer.circle(landmark.center, 3.0)
    }
}

/**
 * Render the right eyebrow (landmarks 17-21)
 */
fun renderRightEyebrow(drawer: Drawer, landmarks: List<Circle>) {
    if (landmarks.size < 22) return

    val eyebrowLandmarks = landmarks.subList(17, 22)

    drawer.fill = ColorRGBa.CYAN.opacify(0.9)
    drawer.stroke = null
    for (landmark in eyebrowLandmarks) {
        drawer.circle(landmark.center, 3.0)
    }
}

/**
 * Render the left eyebrow (landmarks 22-26)
 */
fun renderLeftEyebrow(drawer: Drawer, landmarks: List<Circle>) {
    if (landmarks.size < 27) return

    val eyebrowLandmarks = landmarks.subList(22, 27)

    drawer.fill = ColorRGBa.CYAN.opacify(0.9)
    drawer.stroke = null
    for (landmark in eyebrowLandmarks) {
        drawer.circle(landmark.center, 3.0)
    }
}

/**
 * Render the nose (landmarks 27-35)
 */
fun renderNose(drawer: Drawer, landmarks: List<Circle>) {
    if (landmarks.size < 36) return

    val noseLandmarks = landmarks.subList(27, 36)

    drawer.fill = ColorRGBa.YELLOW.opacify(0.9)
    drawer.stroke = null
    for (landmark in noseLandmarks) {
        drawer.circle(landmark.center, 3.0)
    }
}

/**
 * Render the right eye (landmarks 36-41)
 */
fun renderRightEye(drawer: Drawer, landmarks: List<Circle>) {
    if (landmarks.size < 42) return

    val eyeLandmarks = landmarks.subList(36, 42)

    drawer.fill = ColorRGBa.BLUE.opacify(0.9)
    drawer.stroke = null
    for (landmark in eyeLandmarks) {
        drawer.circle(landmark.center, 3.0)
    }
}

/**
 * Render the left eye (landmarks 42-47)
 */
fun renderLeftEye(drawer: Drawer, landmarks: List<Circle>) {
    if (landmarks.size < 48) return

    val eyeLandmarks = landmarks.subList(42, 48)

    drawer.fill = ColorRGBa.BLUE.opacify(0.9)
    drawer.stroke = null
    for (landmark in eyeLandmarks) {
        drawer.circle(landmark.center, 3.0)
    }
}

/**
 * Render the mouth (landmarks 48-67)
 */
fun renderMouth(drawer: Drawer, landmarks: List<Circle>) {
    if (landmarks.size < 68) return

    val mouthLandmarks = landmarks.subList(48, 68)

    drawer.fill = ColorRGBa.RED.opacify(0.9)
    drawer.stroke = null
    for (landmark in mouthLandmarks) {
        drawer.circle(landmark.center, 3.0)
    }
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
    for (face in faces) {
        // Draw face outline with a different style
        drawer.stroke = ColorRGBa.MAGENTA
        drawer.strokeWeight = 3.0
        drawer.fill = ColorRGBa.MAGENTA.opacify(0.1)
        drawer.rectangle(face.faceRect)

        // Render each facial feature separately
        renderJaw(drawer, face.landmarks)
        renderRightEyebrow(drawer, face.landmarks)
        renderLeftEyebrow(drawer, face.landmarks)
        renderNose(drawer, face.landmarks)
        renderRightEye(drawer, face.landmarks)
        renderLeftEye(drawer, face.landmarks)
        renderMouth(drawer, face.landmarks)
    }
}
