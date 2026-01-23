package genuary

import org.openrndr.draw.Drawer
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.color.ColorRGBa
import kotlin.random.Random

/**
 * Bauhaus color palettes
 */
object BauhausPalettes {
    val classic = listOf(
        ColorRGBa.fromHex(0xE63946), // Red
        ColorRGBa.fromHex(0xF1FA8C), // Yellow
        ColorRGBa.fromHex(0x457B9D), // Blue
        ColorRGBa.fromHex(0x1D3557), // Dark blue
        ColorRGBa.fromHex(0xF4F1DE)  // Cream
    )

    val warm = listOf(
        ColorRGBa.fromHex(0xFF6B35), // Orange
        ColorRGBa.fromHex(0xF7931E), // Amber
        ColorRGBa.fromHex(0xFDC500), // Gold
        ColorRGBa.fromHex(0xC1121F), // Crimson
        ColorRGBa.fromHex(0x780000)  // Dark red
    )

    val cool = listOf(
        ColorRGBa.fromHex(0x264653), // Charcoal
        ColorRGBa.fromHex(0x2A9D8F), // Teal
        ColorRGBa.fromHex(0xE9C46A), // Sand
        ColorRGBa.fromHex(0xF4A261), // Orange
        ColorRGBa.fromHex(0xE76F51)  // Terracotta
    )

    val allPalettes = listOf(classic, warm, cool)
}

/**
 * Facial feature groups
 */
enum class FacialFeature {
    JAW,
    RIGHT_EYEBROW,
    LEFT_EYEBROW,
    NOSE,
    RIGHT_EYE,
    LEFT_EYE,
    MOUTH
}

/**
 * Bauhaus shape types (without position - position calculated per frame)
 */
sealed class BauhausShape {
    abstract val rotation: Double
    abstract val color: ColorRGBa
    abstract val sizeFactor: Double // multiplier for calculated bounds

    data class Circle(
        override val rotation: Double,
        override val color: ColorRGBa,
        override val sizeFactor: Double
    ) : BauhausShape()

    data class Square(
        override val rotation: Double,
        override val color: ColorRGBa,
        override val sizeFactor: Double
    ) : BauhausShape()

    data class Rectangle(
        override val rotation: Double,
        override val color: ColorRGBa,
        override val sizeFactor: Double,
        val aspectRatio: Double // width/height ratio
    ) : BauhausShape()

    data class Triangle(
        override val rotation: Double,
        override val color: ColorRGBa,
        override val sizeFactor: Double
    ) : BauhausShape()

    data class IrregularTriangle(
        override val rotation: Double,
        override val color: ColorRGBa,
        override val sizeFactor: Double,
        val point1: Vector2, // normalized coordinates (-0.5 to 0.5)
        val point2: Vector2,
        val point3: Vector2
    ) : BauhausShape()

    data class Line(
        override val rotation: Double,
        override val color: ColorRGBa,
        override val sizeFactor: Double,
        val thickness: Double = 2.0
    ) : BauhausShape()
}

/**
 * Bauhaus renderer state
 */
object BauhausRenderer {
    private var shapes = mutableMapOf<FacialFeature, BauhausShape>()
    private var currentPalette = BauhausPalettes.classic

    /**
     * Generate Bauhaus shapes for each facial feature
     */
    fun generateShapes(landmarks: List<Circle>) {
        shapes.clear()
        currentPalette = BauhausPalettes.allPalettes.random()

        println("\n=== Generating Bauhaus Shapes ===")
        println("Palette: ${when (currentPalette) {
            BauhausPalettes.classic -> "Classic"
            BauhausPalettes.warm -> "Warm"
            BauhausPalettes.cool -> "Cool"
            else -> "Unknown"
        }}")

        // Generate one shape for each facial feature
        for (feature in FacialFeature.values()) {
            val shapeType = Random.nextInt(6) // 6 shape types now
            val color = currentPalette.random()
            val sizeFactor = Random.nextDouble(0.5, 1.0) // Limit to 50%-100% of facial feature size
            val rotation = Random.nextDouble(0.0, 360.0)

            val shape = when (shapeType) {
                0 -> BauhausShape.Circle(
                    rotation = rotation,
                    color = color,
                    sizeFactor = sizeFactor
                )
                1 -> BauhausShape.Square(
                    rotation = rotation,
                    color = color,
                    sizeFactor = sizeFactor
                )
                2 -> BauhausShape.Rectangle(
                    rotation = rotation,
                    color = color,
                    sizeFactor = sizeFactor,
                    aspectRatio = Random.nextDouble(0.5, 2.5) // width/height ratio
                )
                3 -> BauhausShape.Triangle(
                    rotation = rotation,
                    color = color,
                    sizeFactor = sizeFactor
                )
                4 -> BauhausShape.IrregularTriangle(
                    rotation = rotation,
                    color = color,
                    sizeFactor = sizeFactor,
                    point1 = Vector2(Random.nextDouble(-0.5, 0.5), Random.nextDouble(-0.5, 0.5)),
                    point2 = Vector2(Random.nextDouble(-0.5, 0.5), Random.nextDouble(-0.5, 0.5)),
                    point3 = Vector2(Random.nextDouble(-0.5, 0.5), Random.nextDouble(-0.5, 0.5))
                )
                else -> BauhausShape.Line(
                    rotation = rotation,
                    color = color,
                    sizeFactor = sizeFactor,
                    thickness = Random.nextDouble(2.0, 6.0)
                )
            }

            shapes[feature] = shape

            // Log the shape assignment
            val shapeName = when (shape) {
                is BauhausShape.Circle -> "Circle"
                is BauhausShape.Square -> "Square"
                is BauhausShape.Rectangle -> "Rectangle (${String.format("%.1f", shape.aspectRatio)}:1)"
                is BauhausShape.Triangle -> "Triangle"
                is BauhausShape.IrregularTriangle -> "Irregular Triangle"
                is BauhausShape.Line -> "Line"
            }
            println("  ${feature.name.replace('_', ' ')}: $shapeName (size: ${String.format("%.0f%%", sizeFactor * 100)})")
        }
        println("=================================\n")
    }

    /**
     * Render jaw shape (landmarks 0-16)
     */
    fun renderJaw(drawer: Drawer, landmarks: List<Circle>) {
        if (landmarks.size < 17) return
        val jawLandmarks = landmarks.subList(0, 17)
        shapes[FacialFeature.JAW]?.let { shape ->
            val (center, size) = calculateBounds(jawLandmarks)
            renderShape(drawer, shape, center, size)
        }
    }

    /**
     * Render right eyebrow shape (landmarks 17-21)
     */
    fun renderRightEyebrow(drawer: Drawer, landmarks: List<Circle>) {
        if (landmarks.size < 22) return
        val eyebrowLandmarks = landmarks.subList(17, 22)
        shapes[FacialFeature.RIGHT_EYEBROW]?.let { shape ->
            val (center, size) = calculateBounds(eyebrowLandmarks)
            renderShape(drawer, shape, center, size)
        }
    }

    /**
     * Render left eyebrow shape (landmarks 22-26)
     */
    fun renderLeftEyebrow(drawer: Drawer, landmarks: List<Circle>) {
        if (landmarks.size < 27) return
        val eyebrowLandmarks = landmarks.subList(22, 27)
        shapes[FacialFeature.LEFT_EYEBROW]?.let { shape ->
            val (center, size) = calculateBounds(eyebrowLandmarks)
            renderShape(drawer, shape, center, size)
        }
    }

    /**
     * Render nose shape (landmarks 27-35)
     */
    fun renderNose(drawer: Drawer, landmarks: List<Circle>) {
        if (landmarks.size < 36) return
        val noseLandmarks = landmarks.subList(27, 36)
        shapes[FacialFeature.NOSE]?.let { shape ->
            val (center, size) = calculateBounds(noseLandmarks)
            renderShape(drawer, shape, center, size)
        }
    }

    /**
     * Render right eye shape (landmarks 36-41)
     */
    fun renderRightEye(drawer: Drawer, landmarks: List<Circle>) {
        if (landmarks.size < 42) return
        val eyeLandmarks = landmarks.subList(36, 42)
        shapes[FacialFeature.RIGHT_EYE]?.let { shape ->
            val (center, size) = calculateBounds(eyeLandmarks)
            renderShape(drawer, shape, center, size)
        }
    }

    /**
     * Render left eye shape (landmarks 42-47)
     */
    fun renderLeftEye(drawer: Drawer, landmarks: List<Circle>) {
        if (landmarks.size < 48) return
        val eyeLandmarks = landmarks.subList(42, 48)
        shapes[FacialFeature.LEFT_EYE]?.let { shape ->
            val (center, size) = calculateBounds(eyeLandmarks)
            renderShape(drawer, shape, center, size)
        }
    }

    /**
     * Render mouth shape (landmarks 48-67)
     */
    fun renderMouth(drawer: Drawer, landmarks: List<Circle>) {
        if (landmarks.size < 68) return
        val mouthLandmarks = landmarks.subList(48, 68)
        shapes[FacialFeature.MOUTH]?.let { shape ->
            val (center, size) = calculateBounds(mouthLandmarks)
            renderShape(drawer, shape, center, size)
        }
    }

    /**
     * Calculate center and size of a group of landmarks
     */
    private fun calculateBounds(landmarks: List<Circle>): Pair<Vector2, Double> {
        val minX = landmarks.minOf { it.center.x }
        val maxX = landmarks.maxOf { it.center.x }
        val minY = landmarks.minOf { it.center.y }
        val maxY = landmarks.maxOf { it.center.y }

        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        val width = maxX - minX
        val height = maxY - minY
        val size = maxOf(width, height)

        return Pair(Vector2(centerX, centerY), size)
    }

    /**
     * Render a single Bauhaus shape at given position and size
     */
    private fun renderShape(drawer: Drawer, shape: BauhausShape, position: Vector2, baseSize: Double) {
        val actualSize = baseSize * shape.sizeFactor

        drawer.pushTransforms()
        drawer.translate(position)
        drawer.rotate(shape.rotation)

        when (shape) {
            is BauhausShape.Circle -> {
                drawer.fill = shape.color
                drawer.stroke = null
                drawer.circle(0.0, 0.0, actualSize / 2)
            }
            is BauhausShape.Square -> {
                drawer.fill = shape.color
                drawer.stroke = null
                drawer.rectangle(-actualSize / 2, -actualSize / 2, actualSize, actualSize)
            }
            is BauhausShape.Rectangle -> {
                drawer.fill = shape.color
                drawer.stroke = null
                val width = actualSize * shape.aspectRatio
                val height = actualSize
                drawer.rectangle(-width / 2, -height / 2, width, height)
            }
            is BauhausShape.Triangle -> {
                drawer.fill = shape.color
                drawer.stroke = null
                val height = actualSize * 0.866 // equilateral triangle
                drawer.contour(org.openrndr.shape.contour {
                    moveTo(0.0, -height / 2)
                    lineTo(-actualSize / 2, height / 2)
                    lineTo(actualSize / 2, height / 2)
                    close()
                })
            }
            is BauhausShape.IrregularTriangle -> {
                drawer.fill = shape.color
                drawer.stroke = null
                drawer.contour(org.openrndr.shape.contour {
                    moveTo(shape.point1.x * actualSize, shape.point1.y * actualSize)
                    lineTo(shape.point2.x * actualSize, shape.point2.y * actualSize)
                    lineTo(shape.point3.x * actualSize, shape.point3.y * actualSize)
                    close()
                })
            }
            is BauhausShape.Line -> {
                drawer.fill = null
                drawer.stroke = shape.color
                drawer.strokeWeight = shape.thickness
                drawer.lineSegment(-actualSize / 2, 0.0, actualSize / 2, 0.0)
            }
        }

        drawer.popTransforms()
    }

    /**
     * Check if shapes are empty
     */
    fun isEmpty(): Boolean = shapes.isEmpty()

    /**
     * Clear all shapes
     */
    fun clear() {
        shapes.clear()
    }
}

/**
 * Main Bauhaus render function
 */
fun renderBauhausFaceDetection(
    drawer: Drawer,
    faces: List<DetectedFace>,
    shouldRegenerate: Boolean = false
) {
    for (face in faces) {
        // Regenerate shapes if requested or if shapes are empty
        if (shouldRegenerate || BauhausRenderer.isEmpty()) {
            BauhausRenderer.generateShapes(face.landmarks)
        }

        // Render each facial feature
        BauhausRenderer.renderJaw(drawer, face.landmarks)
        BauhausRenderer.renderRightEyebrow(drawer, face.landmarks)
        BauhausRenderer.renderLeftEyebrow(drawer, face.landmarks)
        BauhausRenderer.renderNose(drawer, face.landmarks)
        BauhausRenderer.renderRightEye(drawer, face.landmarks)
        BauhausRenderer.renderLeftEye(drawer, face.landmarks)
        BauhausRenderer.renderMouth(drawer, face.landmarks)
    }
}
