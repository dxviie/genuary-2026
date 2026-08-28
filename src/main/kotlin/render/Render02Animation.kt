package render

import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.World
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.noise.simplex
import org.openrndr.math.Vector2
import org.openrndr.shape.contour
import utils.PHYSICS_SCALE
import utils.SoftBody
import utils.createSoftBody
import utils.createWall
import utils.toOpenRNDR
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Website render for 02 Twelve Principles of Animation (see genuary/02PrinciplesOfAnimation.kt).
 * The "Create Blob" button is pressed by a timer instead of the mouse: a new soft
 * body blob is launched from the center every ~1.7 seconds with seeded parameters.
 */
fun main() = application {
    configure {
        width = 1080
        height = 1080
    }

    program {
        val rng = Random(20260102)
        val totalFrames = 1020L // 17s @ 60fps

        val world = World(Vec2(0f, 9.8f))

        val wallThickness = 50f / PHYSICS_SCALE.toFloat()
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), (height / PHYSICS_SCALE + wallThickness).toFloat(),
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Floor
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), -wallThickness,
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Ceiling
        createWall(world, -wallThickness, (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Left wall
        createWall(world, (width / PHYSICS_SCALE + wallThickness).toFloat(), (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Right wall

        val allShapes = mutableListOf<SoftBody>()
        val shapeColors = mutableMapOf<SoftBody, ColorRGBa>()

        fun randomPastelColor(): ColorRGBa {
            val r = rng.nextDouble(0.0, 1.0)
            val g = rng.nextDouble(0.0, 1.0)
            val b = rng.nextDouble(0.0, 1.0)
            return ColorRGBa(r, g, b, 1.0)
        }

        fun generateBlob(blobSegments: Int, blobNoiseFactor: Double, blobRadius: Double): List<Vector2> {
            val points = mutableListOf<Vector2>()
            val numPoints = blobSegments - 1
            val centerX = width / 2.0
            val centerY = height / 2.0
            val noiseSeed = rng.nextInt(1000000)

            for (i in 0 until numPoints) {
                val angle = (i.toDouble() / numPoints) * 2.0 * PI
                val noiseX = cos(angle) * 5.0
                val noiseY = sin(angle) * 5.0
                val noiseValue = simplex(noiseSeed, Vector2(noiseX, noiseY))
                val radiusVariation = noiseValue * blobNoiseFactor
                val radius = blobRadius + radiusVariation
                points.add(Vector2(centerX + cos(angle) * radius, centerY + sin(angle) * radius))
            }
            return points
        }

        fun launchBlob() {
            val blobPoints = generateBlob(
                blobSegments = rng.nextInt(8, 22),
                blobNoiseFactor = rng.nextDouble(15.0, 55.0),
                blobRadius = rng.nextDouble(70.0, 150.0)
            )
            val newShape = createSoftBody(world, blobPoints)
            allShapes.add(newShape)
            shapeColors[newShape] = randomPastelColor()

            val upwardForce = rng.nextDouble(5.0, 15.0)
            val sidewaysForce = rng.nextDouble(-3.0, 3.0)
            newShape.bodies.forEach { body ->
                body.applyLinearImpulse(
                    Vec2((sidewaysForce / PHYSICS_SCALE).toFloat(),
                         (-upwardForce / PHYSICS_SCALE).toFloat()),
                    body.worldCenter
                )
            }
        }

        extend(siteRecorder("02-animation", totalFrames))

        var frame = 0L
        extend {
            if (frame % 100 == 20L && allShapes.size < 9) {
                launchBlob()
            }
            frame++

            world.step(1f / 60f, 8, 3)

            drawer.clear(ColorRGBa(0.95, 0.96, 0.98, 1.0))

            allShapes.forEach { softBody ->
                val shapeColor = shapeColors[softBody] ?: ColorRGBa.WHITE

                if (softBody.bodies.isNotEmpty()) {
                    val polygonPoints = softBody.bodies.map { it.position.toOpenRNDR() }
                    drawer.fill = shapeColor.opacify(0.5)
                    drawer.stroke = null
                    drawer.contour(contour {
                        moveTo(polygonPoints.first())
                        polygonPoints.drop(1).forEach { lineTo(it) }
                        close()
                    })
                }

                drawer.fill = null
                drawer.stroke = ColorRGBa.BLACK
                drawer.strokeWeight = 2.0
                for (i in softBody.bodies.indices) {
                    val nextIndex = (i + 1) % softBody.bodies.size
                    val p1 = softBody.bodies[i].position.toOpenRNDR()
                    val p2 = softBody.bodies[nextIndex].position.toOpenRNDR()
                    drawer.lineSegment(p1, p2)
                }

                drawer.fill = shapeColor
                drawer.stroke = shapeColor.shade(0.7)
                drawer.strokeWeight = 1.0
                softBody.bodies.forEach { body ->
                    val pos = body.position.toOpenRNDR()
                    val radius = body.fixtureList?.shape?.radius ?: 0.05f
                    val visualRadius = radius * PHYSICS_SCALE
                    drawer.circle(pos, visualRadius)
                }
            }
        }
    }
}
