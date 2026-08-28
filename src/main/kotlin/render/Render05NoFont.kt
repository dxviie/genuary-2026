package render

import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.jbox2d.dynamics.joints.DistanceJoint
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.shape.contour
import utils.PHYSICS_SCALE
import utils.SoftBody
import utils.createSoftBody
import utils.createSpringJoint
import utils.createWall
import utils.toBox2D
import utils.toOpenRNDR
import kotlin.random.Random

/**
 * Website render for 05 "Write 'Genuary'. Avoid using a font." (see genuary/05GenuaryWithoutFont.kt).
 *
 * In the interactive sketch the letters are drawn by hand, click by click, and
 * become wobbly soft bodies. Here the "hand drawn" outlines come from tracing
 * tiny 5x7 pixel-grid letters (no font involved), which drop into the scene one
 * by one and land on the floor. Confetti at the end, as per the original's 'f' key.
 */

// 5x7 pixel letters, deliberately blocky. '1' = filled cell.
private val GLYPHS = mapOf(
    'G' to listOf("11111", "10000", "10000", "10111", "10001", "10001", "11111"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'N' to listOf("10001", "11001", "11101", "10111", "10011", "10001", "10001"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "11111"),
    'A' to listOf("11111", "10001", "10001", "11111", "10001", "10001", "10001"),
    'R' to listOf("11111", "10001", "10001", "11111", "10010", "10010", "10010"),
    'Y' to listOf("10001", "10001", "11111", "00100", "00100", "00100", "00100"),
)

/**
 * Traces the outer boundary of the filled cells as a single closed loop.
 * Directed edges keep the interior on the right, so the loop comes out
 * clockwise in screen coordinates. The glyphs above avoid diagonal "pinch"
 * configurations, so every vertex has at most one outgoing edge.
 */
private fun traceOutline(rows: List<String>): List<Vector2> {
    val h = rows.size
    val w = rows[0].length
    fun filled(x: Int, y: Int) = y in 0 until h && x in 0 until w && rows[y][x] == '1'

    val next = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
    for (y in 0 until h) for (x in 0 until w) {
        if (!filled(x, y)) continue
        if (!filled(x, y - 1)) next[x to y] = (x + 1) to y             // top edge, walking right
        if (!filled(x + 1, y)) next[(x + 1) to y] = (x + 1) to (y + 1) // right edge, walking down
        if (!filled(x, y + 1)) next[(x + 1) to (y + 1)] = x to (y + 1) // bottom edge, walking left
        if (!filled(x - 1, y)) next[x to (y + 1)] = x to y             // left edge, walking up
    }

    val start = next.keys.minWith(compareBy({ it.second }, { it.first }))
    val loop = mutableListOf<Vector2>()
    var current = start
    do {
        loop.add(Vector2(current.first.toDouble(), current.second.toDouble()))
        current = next[current] ?: error("Broken outline at $current")
    } while (current != start && loop.size < next.size + 1)
    return loop
}

/** Evenly respaces a closed polygon at roughly [spacing] between points. */
private fun resampleClosed(points: List<Vector2>, spacing: Double): List<Vector2> {
    val lengths = points.indices.map { i -> (points[(i + 1) % points.size] - points[i]).length }
    val perimeter = lengths.sum()
    val count = (perimeter / spacing).toInt().coerceAtLeast(8)
    val result = mutableListOf<Vector2>()
    var walked = 0.0
    var seg = 0
    for (k in 0 until count) {
        val target = k * perimeter / count
        while (seg < points.size - 1 && walked + lengths[seg] < target) {
            walked += lengths[seg]
            seg++
        }
        val a = points[seg]
        val b = points[(seg + 1) % points.size]
        val t = if (lengths[seg] > 1e-9) (target - walked) / lengths[seg] else 0.0
        result.add(a + (b - a) * t)
    }
    return result
}

fun main() = application {
    configure {
        width = 1920
        height = 1080
    }

    program {
        val rng = Random(20260105)
        val totalFrames = 1080L // 18s @ 60fps

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

        data class Confetti(val body: Body, val color: ColorRGBa, var lifetime: Double = 10.0)
        val confettiParticles = mutableListOf<Confetti>()

        fun randomVibrantColor(): ColorRGBa {
            val r = rng.nextDouble(0.5, 1.0)
            val g = rng.nextDouble(0.5, 1.0)
            val b = rng.nextDouble(0.5, 1.0)
            return when (rng.nextInt(3)) {
                0 -> ColorRGBa(1.0, g, b, 1.0)
                1 -> ColorRGBa(r, 1.0, b, 1.0)
                else -> ColorRGBa(r, g, 1.0, 1.0)
            }
        }

        fun spawnConfetti() {
            val centerX = (width / 2.0 / PHYSICS_SCALE).toFloat()
            val centerY = (height / 2.0 / PHYSICS_SCALE).toFloat()
            repeat(200) {
                val bodyDef = BodyDef().apply {
                    type = BodyType.DYNAMIC
                    position.set(centerX, centerY)
                }
                val body = world.createBody(bodyDef)
                val circle = CircleShape().apply {
                    radius = rng.nextDouble(0.02, 0.05).toFloat()
                }
                val fixtureDef = FixtureDef().apply {
                    shape = circle
                    density = 0.5f
                    friction = 0.3f
                    restitution = 0.6f
                }
                body.createFixture(fixtureDef)

                val upwardForce = rng.nextDouble(8.0, 15.0)
                val sidewaysForce = rng.nextDouble(-5.0, 5.0)
                body.applyLinearImpulse(
                    Vec2((sidewaysForce / PHYSICS_SCALE).toFloat(),
                         (-upwardForce / PHYSICS_SCALE).toFloat()),
                    body.worldCenter
                )
                body.angularVelocity = rng.nextDouble(-10.0, 10.0).toFloat()
                confettiParticles.add(Confetti(body, randomVibrantColor()))
            }
        }

        // Prepare the seven letter outlines, laid out across the top of the screen
        val cell = 30.0
        val gap = 36.0
        val glyphWidth = 5 * cell
        val advance = glyphWidth + gap
        val word = "GENUARY"
        val wordWidth = word.length * glyphWidth + (word.length - 1) * gap
        val startX = (width - wordWidth) / 2.0
        // letters materialize standing on the floor (concave softbody glyphs
        // don't survive a long drop legibly), then settle and jiggle in place
        val spawnY = height - 7 * cell - 40.0

        val letterOutlines = word.mapIndexed { index, ch ->
            val outline = traceOutline(GLYPHS.getValue(ch))
            val origin = Vector2(startX + index * advance, spawnY)
            resampleClosed(outline.map { origin + it * cell }, 22.0).map {
                // slight jitter so it looks click-drawn rather than machine made
                it + Vector2(rng.nextDouble(-3.0, 3.0), rng.nextDouble(-3.0, 3.0))
            }
        }

        fun dropLetter(index: Int) {
            val outlinePoints = letterOutlines[index]
            val newShape = createSoftBody(world, outlinePoints)
            // stiffen the springs (the interactive sketch exposes these as sliders)
            newShape.edgeJoints.forEach { joint ->
                if (joint is DistanceJoint) {
                    joint.frequency = 6f
                    joint.dampingRatio = 0.6f
                }
            }
            newShape.diagonalJoints.forEach { joint ->
                if (joint is DistanceJoint) {
                    joint.frequency = 3.5f
                    joint.dampingRatio = 0.75f
                }
            }
            // concave glyphs can't hold their shape with ring springs alone, so
            // pin each letter to two invisible anchors with spring spokes (same
            // idea as the screen-edge anchors in 00): jelly wobble, no collapse
            val centroid = outlinePoints.reduce { a, b -> a + b } * (1.0 / outlinePoints.size)
            val anchors = listOf(centroid, centroid + Vector2(0.0, -70.0)).map { p ->
                world.createBody(BodyDef().apply {
                    type = BodyType.STATIC
                    position.set(p.toBox2D())
                })
            }
            newShape.bodies.forEachIndexed { i, body ->
                createSpringJoint(world, anchors[0], body, frequency = 2f, damping = 0.8f)
                if (i % 3 == 0) {
                    createSpringJoint(world, anchors[1], body, frequency = 2f, damping = 0.85f)
                }
            }
            allShapes.add(newShape)
            shapeColors[newShape] = randomVibrantColor()
        }

        extend(siteRecorder("05-no-font", totalFrames))

        var frame = 0L
        extend {
            // one letter every ~0.8s, a couple of playful nudges, then confetti
            if (frame >= 20 && (frame - 20) % 50 == 0L) {
                val index = ((frame - 20) / 50).toInt()
                if (index < word.length) dropLetter(index)
            }
            if (frame == 520L || frame == 640L) {
                val shape = allShapes[rng.nextInt(allShapes.size)]
                val upwardForce = rng.nextDouble(2.0, 3.5)
                val sidewaysForce = rng.nextDouble(-1.0, 1.0)
                shape.bodies.forEach { body ->
                    body.applyLinearImpulse(
                        Vec2((sidewaysForce / PHYSICS_SCALE).toFloat(),
                             (-upwardForce / PHYSICS_SCALE).toFloat()),
                        body.worldCenter
                    )
                }
            }
            if (frame == 760L || frame == 880L) spawnConfetti()
            frame++

            world.step(1f / 60f, 8, 3)
            confettiParticles.removeAll { confetti ->
                confetti.lifetime -= 1.0 / 60.0
                if (confetti.lifetime <= 0) {
                    world.destroyBody(confetti.body)
                    true
                } else {
                    false
                }
            }

            drawer.clear(ColorRGBa(0.05, 0.05, 0.05, 1.0))

            allShapes.forEach { softBody ->
                val shapeColor = shapeColors[softBody] ?: ColorRGBa.WHITE

                if (softBody.bodies.isNotEmpty()) {
                    val polygonPoints = softBody.bodies.map { it.position.toOpenRNDR() }
                    drawer.fill = shapeColor.opacify(0.7)
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
                    drawer.lineSegment(
                        softBody.bodies[i].position.toOpenRNDR(),
                        softBody.bodies[nextIndex].position.toOpenRNDR()
                    )
                }

                drawer.fill = shapeColor
                drawer.stroke = shapeColor.shade(0.7)
                drawer.strokeWeight = 1.0
                softBody.bodies.forEach { body ->
                    val pos = body.position.toOpenRNDR()
                    val radius = body.fixtureList?.shape?.radius ?: 0.05f
                    drawer.circle(pos, radius * PHYSICS_SCALE)
                }
            }

            confettiParticles.forEach { confetti ->
                val pos = confetti.body.position.toOpenRNDR()
                val radius = confetti.body.fixtureList?.shape?.radius ?: 0.03f
                val visualRadius = radius * PHYSICS_SCALE
                val alpha = (confetti.lifetime / 10.0).coerceIn(0.0, 1.0)

                drawer.pushTransforms()
                drawer.translate(pos)
                drawer.rotate(Math.toDegrees(confetti.body.angle.toDouble()))
                drawer.fill = confetti.color.opacify(alpha)
                drawer.stroke = null
                drawer.rectangle(-visualRadius, -visualRadius * 2, visualRadius * 2, visualRadius * 4)
                drawer.popTransforms()
            }
        }
    }
}
