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
