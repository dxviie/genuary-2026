package genuary

import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.openrndr.KEY_ESCAPE
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Vector2
import utils.PHYSICS_SCALE
import utils.createWall
import utils.toOpenRNDR
import java.io.File
import kotlin.random.Random

/**
 * 12 Boxes Only
 * Click to split a random box into 4 smaller boxes with outward forces
 */

data class Palette(val background: ColorRGBa, val boxBase: ColorRGBa)
data class Box(val body: Body, val size: Double, val color: ColorRGBa)

fun main() = application {
    configure {
        width = 1080
        height = 1080
        display = displays[1]
    }

    oliveProgram {
        val fontFile = File("data/fonts/default.otf")
        val font = loadFont(fontFile.toURI().toString(), 13.0)

        // Box2D setup
        val world = World(Vec2(0f, 0f)) // Gravity pointing down

        // Create walls (floor, ceiling, left, right)
        val wallThickness = 50f / PHYSICS_SCALE.toFloat()
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), (height / PHYSICS_SCALE + wallThickness).toFloat(),
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Floor
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), -wallThickness,
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Ceiling
        createWall(world, -wallThickness, (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Left wall
        createWall(world, (width / PHYSICS_SCALE + wallThickness).toFloat(), (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Right wall

        // Define color palettes
        val palettes = listOf(
            Palette(ColorRGBa.fromHex(0x1a1a2e), ColorRGBa.fromHex(0xeaeaea)), // Dark blue-grey & light grey
            Palette(ColorRGBa.fromHex(0x0f0e17), ColorRGBa.fromHex(0xff8906)), // Dark & orange
            Palette(ColorRGBa.fromHex(0x2d132c), ColorRGBa.fromHex(0xee4266)), // Dark purple & pink
            Palette(ColorRGBa.fromHex(0x16213e), ColorRGBa.fromHex(0xf4a261)), // Navy & peach
            Palette(ColorRGBa.fromHex(0x1b1b1e), ColorRGBa.fromHex(0x6fffe9)), // Charcoal & cyan
            Palette(ColorRGBa.fromHex(0x132a13), ColorRGBa.fromHex(0xecf39e)), // Forest green & pale yellow
            Palette(ColorRGBa.fromHex(0x2b2d42), ColorRGBa.fromHex(0xef476f)), // Midnight blue & hot pink
            Palette(ColorRGBa.fromHex(0x191716), ColorRGBa.fromHex(0xfca311)), // Almost black & gold
        )

        var currentPalette = palettes.random()
        val initialBoxSize = (width/1.5)
        val maxDiff = 0.03

        // Function to create a tinted version of a color
        fun tintColor(baseColor: ColorRGBa): ColorRGBa {
            // Apply small random variations to RGB channels
            val rShift = Random.nextDouble(-maxDiff, maxDiff)
            val gShift = Random.nextDouble(-maxDiff, maxDiff)
            val bShift = Random.nextDouble(-maxDiff, maxDiff)

            return ColorRGBa(
                (baseColor.r + rShift).coerceIn(0.0, 1.0),
                (baseColor.g + gShift).coerceIn(0.0, 1.0),
                (baseColor.b + bShift).coerceIn(0.0, 1.0),
                baseColor.alpha
            )
        }

        // State
        val boxes = mutableListOf<Box>()
        var debugMode = false
        var paused = false
        val minBoxSize = 10.0 // Minimum box size to prevent infinite splitting

        // Create a box at a specific position
        fun createBox(centerX: Double, centerY: Double, size: Double, color: ColorRGBa): Box {
            val bodyDef = BodyDef().apply {
                type = BodyType.DYNAMIC
                position.set((centerX / PHYSICS_SCALE).toFloat(), (centerY / PHYSICS_SCALE).toFloat())
            }
            val body = world.createBody(bodyDef)

            val boxShape = PolygonShape().apply {
                setAsBox((size / 2 / PHYSICS_SCALE).toFloat(), (size / 2 / PHYSICS_SCALE).toFloat())
            }

            val fixtureDef = FixtureDef().apply {
                shape = boxShape
                density = 1f
                restitution = 0.3f
                friction = 0.5f
            }

            body.createFixture(fixtureDef)
            return Box(body, size, color)
        }

        // Screen recorder
        val recorder = ScreenRecorder().apply {
            outputToVideo = false
            frameRate = 60
        }
        extend(recorder)

        // Create initial box at center
        boxes.add(createBox(width / 2.0, height / 2.0, initialBoxSize, currentPalette.boxBase))

        // Split a box into 4 smaller boxes
        fun splitBox(box: Box) {
            val centerPos = box.body.position.toOpenRNDR()
            val originalVelocity = box.body.linearVelocity
            val originalAngularVelocity = box.body.angularVelocity
            val originalAngle = box.body.angle.toDouble()
            val newSize = box.size / 2.0

            // Remove the original box
            world.destroyBody(box.body)
            boxes.remove(box)

            // Create 4 new boxes at quadrants in local space
            val localOffsets = listOf(
                Vector2(-newSize / 2, -newSize / 2),  // Top-left
                Vector2(newSize / 2, -newSize / 2),   // Top-right
                Vector2(-newSize / 2, newSize / 2),   // Bottom-left
                Vector2(newSize / 2, newSize / 2)     // Bottom-right
            )

            for (localOffset in localOffsets) {
                // Rotate the offset by the original box's angle to get world space position
                val rotatedOffset = Vector2(
                    localOffset.x * kotlin.math.cos(originalAngle) - localOffset.y * kotlin.math.sin(originalAngle),
                    localOffset.x * kotlin.math.sin(originalAngle) + localOffset.y * kotlin.math.cos(originalAngle)
                )

                // Create new box with a tinted color from the parent
                val newColor = tintColor(box.color)
                val newBox = createBox(centerPos.x + rotatedOffset.x, centerPos.y + rotatedOffset.y, newSize, newColor)

                // Set the new box's rotation to match the original
                newBox.body.setTransform(newBox.body.position, originalAngle.toFloat())

                // Apply random outward force from center along the rotated direction
                val forceDirection = rotatedOffset.normalized
                val forceMagnitude = Random.nextDouble(10.0, 50.0)
                val force = forceDirection * forceMagnitude

                newBox.body.applyLinearImpulse(
                    Vec2((force.x / PHYSICS_SCALE).toFloat(), (force.y / PHYSICS_SCALE).toFloat()),
                    newBox.body.worldCenter
                )

                // Preserve some of the original velocity
                newBox.body.linearVelocity = Vec2(
                    originalVelocity.x * 0.5f,
                    originalVelocity.y * 0.5f
                )

                // Add some random spin
                newBox.body.angularVelocity = originalAngularVelocity * 0.5f + Random.nextDouble(-2.0, 2.0).toFloat()

                boxes.add(newBox)
            }
        }

        // Mouse click handler
        mouse.buttonDown.listen { event ->
            if (event.button.ordinal != 0) return@listen // Only handle left click

            if (boxes.isNotEmpty()) {
                // Find boxes that can still be split (larger than minimum size)
                val splittableBoxes = boxes.filter { it.size / 2.0 >= minBoxSize }

                if (splittableBoxes.isNotEmpty()) {
                    val randomBox = splittableBoxes.random()
                    splitBox(randomBox)
                }
            }
        }

        // Keyboard controls
        keyboard.keyDown.listen { event ->
            when (event.name) {
                "d" -> debugMode = !debugMode
                "p" -> paused = !paused
                "c" -> {
                    // Clear all boxes and reset with new palette
                    boxes.forEach { world.destroyBody(it.body) }
                    boxes.clear()
                    currentPalette = palettes.random()
                    boxes.add(createBox(width / 2.0, height / 2.0, initialBoxSize, currentPalette.boxBase))
                }
                "v" -> {
                    recorder.outputToVideo = !recorder.outputToVideo
                    println(if (recorder.outputToVideo) "Recording" else "Paused")
                }
            }
            when (event.key) {
                KEY_ESCAPE -> program.application.exit()
            }
        }

        extend {
            // Update physics only if not paused
            if (!paused) {
                world.step(1f/60f, 8, 3)
            }

            drawer.clear(currentPalette.background)
            drawer.fontMap = font

            // Draw all boxes
            boxes.forEach { box ->
                val pos = box.body.position.toOpenRNDR()
                val halfSize = (box.size / 2 / PHYSICS_SCALE).toFloat()

                drawer.pushTransforms()
                drawer.translate(pos)
                drawer.rotate(Math.toDegrees(box.body.angle.toDouble()))

                drawer.fill = box.color
                drawer.stroke = null
                drawer.rectangle(
                    -halfSize * PHYSICS_SCALE,
                    -halfSize * PHYSICS_SCALE,
                    box.size,
                    box.size
                )

                // Draw outline in debug mode
                if (debugMode) {
                    drawer.fill = null
                    drawer.stroke = ColorRGBa.RED
                    drawer.strokeWeight = 2.0
                    drawer.rectangle(
                        -halfSize * PHYSICS_SCALE,
                        -halfSize * PHYSICS_SCALE,
                        box.size,
                        box.size
                    )

                    // Draw center point
                    drawer.fill = ColorRGBa.RED
                    drawer.stroke = null
                    drawer.circle(0.0, 0.0, 3.0)
                }

                drawer.popTransforms()
            }

            // Draw status text
            drawer.fill = ColorRGBa.WHITE
            drawer.stroke = null

            // When recording, only show red dot unless in debug mode
            if (recorder.outputToVideo) {
                drawer.fill = ColorRGBa.RED
                drawer.circle(30.0, 30.0, 10.0)

                if (debugMode) {
                    drawer.fill = ColorRGBa.RED
                    drawer.text("● RECORDING (press 'v' to stop)", 50.0, 35.0)
                }
            }

            // Show other info only if not recording OR in debug mode
            if (!recorder.outputToVideo || debugMode) {
                var yPos = if (recorder.outputToVideo) 60.0 else 30.0

                if (paused) {
                    drawer.fill = ColorRGBa.WHITE
                    drawer.text("PAUSED (press 'p' to resume)", 20.0, yPos)
                    yPos += 25.0
                }

                if (debugMode) {
                    drawer.fill = ColorRGBa.WHITE
                    drawer.text("DEBUG MODE (press 'd' to disable)", 20.0, yPos)
                    yPos += 25.0
                    drawer.text("Boxes: ${boxes.size}", 20.0, yPos)
                    yPos += 25.0
                    val splittable = boxes.count { it.size / 2.0 >= minBoxSize }
                    drawer.text("Splittable: $splittable", 20.0, yPos)
                    yPos += 25.0
                }

                // Show controls hint
                if (boxes.size <= 4) {
                    drawer.fill = ColorRGBa.WHITE
                    drawer.text("Click to split a random box into 4 smaller boxes", 20.0, height - 40.0)
                    drawer.text("'d' = debug | 'p' = pause | 'c' = reset | 'v' = record | ESC = exit", 20.0, height - 20.0)
                }
            }
        }
    }
}
