package render

import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import utils.PHYSICS_SCALE
import utils.createWall
import utils.toOpenRNDR
import kotlin.random.Random

/**
 * Website render for 12 Boxes Only (see genuary/12BoxesOnly.kt).
 * The mouse clicks are replaced by a timer that splits a random box
 * every ~1.6 seconds, from a fixed seed.
 */

private data class Palette(val background: ColorRGBa, val boxBase: ColorRGBa)
private data class Box(val body: Body, val size: Double, val color: ColorRGBa)

fun main() = application {
    configure {
        width = 1080
        height = 1080
    }

    program {
        val rng = Random(20260112)
        val totalFrames = 1080L // 18s @ 60fps

        val world = World(Vec2(0f, 0f))

        val wallThickness = 50f / PHYSICS_SCALE.toFloat()
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), (height / PHYSICS_SCALE + wallThickness).toFloat(),
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Floor
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), -wallThickness,
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Ceiling
        createWall(world, -wallThickness, (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Left wall
        createWall(world, (width / PHYSICS_SCALE + wallThickness).toFloat(), (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Right wall

        val palettes = listOf(
            Palette(ColorRGBa.fromHex(0x1a1a2e), ColorRGBa.fromHex(0xeaeaea)),
            Palette(ColorRGBa.fromHex(0x0f0e17), ColorRGBa.fromHex(0xff8906)),
            Palette(ColorRGBa.fromHex(0x2d132c), ColorRGBa.fromHex(0xee4266)),
            Palette(ColorRGBa.fromHex(0x16213e), ColorRGBa.fromHex(0xf4a261)),
            Palette(ColorRGBa.fromHex(0x1b1b1e), ColorRGBa.fromHex(0x6fffe9)),
            Palette(ColorRGBa.fromHex(0x132a13), ColorRGBa.fromHex(0xecf39e)),
            Palette(ColorRGBa.fromHex(0x2b2d42), ColorRGBa.fromHex(0xef476f)),
            Palette(ColorRGBa.fromHex(0x191716), ColorRGBa.fromHex(0xfca311)),
        )

        val currentPalette = palettes[rng.nextInt(palettes.size)]
        val initialBoxSize = (width / 1.5)
        val maxDiff = 0.03
        val minBoxSize = 10.0

        val boxes = mutableListOf<Box>()

        fun tintColor(baseColor: ColorRGBa): ColorRGBa {
            val rShift = rng.nextDouble(-maxDiff, maxDiff)
            val gShift = rng.nextDouble(-maxDiff, maxDiff)
            val bShift = rng.nextDouble(-maxDiff, maxDiff)
            return ColorRGBa(
                (baseColor.r + rShift).coerceIn(0.0, 1.0),
                (baseColor.g + gShift).coerceIn(0.0, 1.0),
                (baseColor.b + bShift).coerceIn(0.0, 1.0),
                baseColor.alpha
            )
        }

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

        fun splitBox(box: Box) {
            val centerPos = box.body.position.toOpenRNDR()
            val originalVelocity = box.body.linearVelocity
            val originalAngularVelocity = box.body.angularVelocity
            val originalAngle = box.body.angle.toDouble()
            val newSize = box.size / 2.0

            world.destroyBody(box.body)
            boxes.remove(box)

            val localOffsets = listOf(
                Vector2(-newSize / 2, -newSize / 2),
                Vector2(newSize / 2, -newSize / 2),
                Vector2(-newSize / 2, newSize / 2),
                Vector2(newSize / 2, newSize / 2)
            )

            for (localOffset in localOffsets) {
                val rotatedOffset = Vector2(
                    localOffset.x * kotlin.math.cos(originalAngle) - localOffset.y * kotlin.math.sin(originalAngle),
                    localOffset.x * kotlin.math.sin(originalAngle) + localOffset.y * kotlin.math.cos(originalAngle)
                )

                val newColor = tintColor(box.color)
                val newBox = createBox(centerPos.x + rotatedOffset.x, centerPos.y + rotatedOffset.y, newSize, newColor)
                newBox.body.setTransform(newBox.body.position, originalAngle.toFloat())

                val forceDirection = rotatedOffset.normalized
                val forceMagnitude = rng.nextDouble(10.0, 50.0)
                val force = forceDirection * forceMagnitude

                newBox.body.applyLinearImpulse(
                    Vec2((force.x / PHYSICS_SCALE).toFloat(), (force.y / PHYSICS_SCALE).toFloat()),
                    newBox.body.worldCenter
                )
                newBox.body.linearVelocity = Vec2(originalVelocity.x * 0.5f, originalVelocity.y * 0.5f)
                newBox.body.angularVelocity = originalAngularVelocity * 0.5f + rng.nextDouble(-2.0, 2.0).toFloat()

                boxes.add(newBox)
            }
        }

        extend(siteRecorder("12-boxes", totalFrames))

        boxes.add(createBox(width / 2.0, height / 2.0, initialBoxSize, currentPalette.boxBase))

        var frame = 0L
        extend {
            // scripted "clicks": split a random splittable box on a fixed rhythm
            if (frame >= 40 && (frame - 40) % 95 == 0L) {
                val splittableBoxes = boxes.filter { it.size / 2.0 >= minBoxSize }
                if (splittableBoxes.isNotEmpty()) {
                    splitBox(splittableBoxes[rng.nextInt(splittableBoxes.size)])
                }
            }
            frame++

            world.step(1f / 60f, 8, 3)

            drawer.clear(currentPalette.background)

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

                drawer.popTransforms()
            }
        }
    }
}
