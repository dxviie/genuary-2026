package genuary

import org.openrndr.draw.Drawer
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import org.openrndr.color.ColorRGBa
import kotlin.random.Random

/**
 * Data structure for a detected face with landmarks
 */
data class DetectedFace(
    val faceRect: Rectangle,
    val landmarks: List<Circle>
)

/**
 * Particle that spawns from a landmark and moves around
 */
data class Particle(
    var position: Vector2,
    var velocity: Vector2,
    var age: Double = 0.0,
    val lifetime: Double,
    val color: ColorRGBa,
    val radius: Double = 3.0
) {
    val isAlive: Boolean
        get() = age < lifetime

    val opacity: Double
        get() = 1.0 - (age / lifetime)

    fun update(deltaTime: Double) {
        age += deltaTime
        position += velocity * deltaTime

        // Small chance to adjust direction (10% per second)
        if (Random.nextDouble() < 0.1 * deltaTime) {
            val angle = Random.nextDouble(-0.5, 0.5) // Small angle change in radians
            val speed = velocity.length
            val currentAngle = kotlin.math.atan2(velocity.y, velocity.x)
            val newAngle = currentAngle + angle
            velocity = Vector2(
                kotlin.math.cos(newAngle) * speed,
                kotlin.math.sin(newAngle) * speed
            )
        }
    }
}

/**
 * Particle system that manages all particles
 */
object ParticleSystem {
    private val particles = mutableListOf<Particle>()

    fun spawn(position: Vector2, color: ColorRGBa) {
        val velocity = Vector2(
            Random.nextDouble(-20.0, 20.0),
            Random.nextDouble(-20.0, 20.0)
        )
        val lifetime = Random.nextDouble(2.0, 5.0)

        particles.add(
            Particle(
                position = position,
                velocity = velocity,
                lifetime = lifetime,
                color = color
            )
        )
    }

    fun update(deltaTime: Double) {
        // Update all particles
        particles.forEach { it.update(deltaTime) }

        // Remove dead particles
        particles.removeAll { !it.isAlive }
    }

    fun render(drawer: Drawer) {
        drawer.circles {
            for (particle in particles) {
                fill = particle.color.opacify(particle.opacity)
                stroke = null
                circle(particle.position, particle.radius)
            }
        }
    }

    fun clear() {
        particles.clear()
    }
}

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
    displayRect: Rectangle,
    deltaTime: Double = 1.0 / 60.0
) {
    // Update particle system
    ParticleSystem.update(deltaTime)

    // Spawn new particles at landmark positions
    for (face in faces) {
        for ((index, landmark) in face.landmarks.withIndex()) {
            // Determine color based on facial feature
            val color = when (index) {
                in 0..16 -> ColorRGBa.GREEN
                in 17..21 -> ColorRGBa.CYAN
                in 22..26 -> ColorRGBa.CYAN
                in 27..35 -> ColorRGBa.YELLOW
                in 36..47 -> ColorRGBa.BLUE
                in 48..67 -> ColorRGBa.RED
                else -> ColorRGBa.WHITE
            }

            // Spawn particle at landmark position (with some randomness to avoid all spawning at once)
            if (Random.nextDouble() < 0.1) {  // 10% chance per landmark per frame
                ParticleSystem.spawn(landmark.center, color)
            }
        }
    }

    // Render all particles
    ParticleSystem.render(drawer)
}
