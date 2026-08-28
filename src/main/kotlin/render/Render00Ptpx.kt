package render

import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.joints.DistanceJoint
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.shadeStyle
import org.openrndr.extra.svg.loadSVG
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.shape.Shape
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.contour
import utils.PHYSICS_SCALE
import utils.SoftBody
import utils.createSoftBody
import utils.createSpringJoint
import utils.createWall
import utils.toOpenRNDR
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Website render for 00 PTPX 2026 (see genuary/00PTPX2026.kt).
 * Same SVG-to-softbody scene with inter-shape joints and the gradient
 * background, but seeded, unpaused from the start and without the GUI.
 */
fun main() = application {
    configure {
        val svgFile = File("data/svg/ptpx-2026-a4.svg")
        val composition = loadSVG(svgFile)

        val viewBoxPattern = Regex("""viewBox=["']([^"']+)["']""")
        val viewBoxMatch = viewBoxPattern.find(svgFile.readText())
        val (svgWidth, svgHeight) = if (viewBoxMatch != null) {
            val viewBoxValues = viewBoxMatch.groupValues[1].trim().split(Regex("\\s+|,"))
            Pair(viewBoxValues[2].toDouble(), viewBoxValues[3].toDouble())
        } else {
            Pair(composition.root.bounds.width, composition.root.bounds.height)
        }

        val displayScale = min(1920.0 / svgWidth, 1080.0 / svgHeight)
        // h264 needs even dimensions
        width = ((svgWidth * displayScale).toInt() / 2) * 2
        height = ((svgHeight * displayScale).toInt() / 2) * 2
    }

    program {
        val rng = Random(20260100)
        val totalFrames = 900L // 15s @ 60fps

        // fixed defaults, matching the interactive sketch's PhysicsSettings
        val gravity = 0.0
        val pointDensity = 10.0
        val maxInitialForce = 0.005
        val interShapeFrequency = 1.5
        val interShapeDamping = 0.9
        val numConnectionLines = 100
        val maxInterShapeJoints = 50

        val svgFile = File("data/svg/ptpx-2026-a4.svg")
        val composition = loadSVG(svgFile)

        val viewBoxPattern = Regex("""viewBox=["']([^"']+)["']""")
        val viewBoxMatch = viewBoxPattern.find(svgFile.readText())
        val (svgLeft, svgTop, svgWidth, svgHeight) = if (viewBoxMatch != null) {
            val viewBoxValues = viewBoxMatch.groupValues[1].trim().split(Regex("\\s+|,"))
            listOf(
                viewBoxValues[0].toDouble(),
                viewBoxValues[1].toDouble(),
                viewBoxValues[2].toDouble(),
                viewBoxValues[3].toDouble()
            )
        } else {
            listOf(
                composition.root.bounds.corner.x,
                composition.root.bounds.corner.y,
                composition.root.bounds.width,
                composition.root.bounds.height
            )
        }

        val scale = min(1920.0 / svgWidth, 1080.0 / svgHeight)
        val offsetX = -svgLeft * scale
        val offsetY = -svgTop * scale

        val world = World(Vec2(0f, gravity.toFloat()))

        val wallThickness = 50f / PHYSICS_SCALE.toFloat()
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), (height / PHYSICS_SCALE + wallThickness).toFloat(),
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Floor
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), -wallThickness,
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Ceiling
        createWall(world, -wallThickness, (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Left wall
        createWall(world, (width / PHYSICS_SCALE + wallThickness).toFloat(), (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Right wall

        data class GradientPalette(val name: String, val colors: List<ColorRGBa>)

        val gradientPalettes = listOf(
            GradientPalette("CMY Spectrum", listOf(
                ColorRGBa.fromHex(0x00A04F),
                ColorRGBa.fromHex(0xF15A22),
                ColorRGBa.fromHex(0x953177),
            )),
            GradientPalette("Spectrum 1", listOf(
                ColorRGBa.fromHex(0xFF4400),
                ColorRGBa.fromHex(0x4400FF),
                ColorRGBa.fromHex(0x00FF44),
                ColorRGBa.fromHex(0xFF0044),
                ColorRGBa.fromHex(0x0044FF)
            )),
            GradientPalette("Spectrum 2", listOf(
                ColorRGBa.fromHex(0xFF0033),
                ColorRGBa.fromHex(0x0088FF),
                ColorRGBa.fromHex(0x88FF00),
                ColorRGBa.fromHex(0xFF0088),
                ColorRGBa.fromHex(0x00FF88)
            )),
            GradientPalette("Spectrum 3", listOf(
                ColorRGBa.fromHex(0xFF3300),
                ColorRGBa.fromHex(0x8800FF),
                ColorRGBa.fromHex(0x00FF33),
                ColorRGBa.fromHex(0xFF0066),
                ColorRGBa.fromHex(0x0066FF)
            ))
        )

        val shapes = mutableListOf<SoftBody>()
        val shapeColors = mutableMapOf<SoftBody, ColorRGBa>()
        val shapeGroups = mutableMapOf<Int, MutableList<SoftBody>>()
        val interShapeJoints = mutableListOf<DistanceJoint>()

        data class GradientPoint(val position: Vector2, val color: ColorRGBa)
        val gradientPoints = mutableListOf<GradientPoint>()

        val currentPalette = gradientPalettes[rng.nextInt(gradientPalettes.size)]
        repeat(rng.nextInt(4, 7)) {
            gradientPoints.add(
                GradientPoint(
                    Vector2(rng.nextDouble(0.0, width.toDouble()), rng.nextDouble(0.0, height.toDouble())),
                    currentPalette.colors[rng.nextInt(currentPalette.colors.size)]
                )
            )
        }

        fun transformPoint(p: Vector2) = Vector2(p.x * scale + offsetX, p.y * scale + offsetY)

        fun sampleContour(shapeContour: ShapeContour, numPoints: Int, shapeTransform: Matrix44): List<Vector2> {
            val points = mutableListOf<Vector2>()
            for (i in 0 until numPoints) {
                val t = i.toDouble() / numPoints
                val point = shapeContour.position(t)
                val transformedPoint = (shapeTransform * point.xy01).xy
                points.add(transformPoint(transformedPoint))
            }
            return points
        }

        fun lineSegmentIntersection(p1: Vector2, p2: Vector2, p3: Vector2, p4: Vector2): Vector2? {
            val denom = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
            if (kotlin.math.abs(denom) < 1e-10) return null
            val t = ((p1.x - p3.x) * (p3.y - p4.y) - (p1.y - p3.y) * (p3.x - p4.x)) / denom
            val u = -((p1.x - p2.x) * (p1.y - p3.y) - (p1.y - p2.y) * (p1.x - p3.x)) / denom
            if (t in 0.0..1.0 && u in 0.0..1.0) {
                return Vector2(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y))
            }
            return null
        }

        fun findClosestBody(shape: SoftBody, point: Vector2): Body? {
            return shape.bodies.minByOrNull { body ->
                (body.position.toOpenRNDR() - point).length
            }
        }

        // Build soft bodies from the SVG shapes
        var shapeIndex = 0
        composition.findShapes().forEach { shapeNode ->
            shapeIndex++
            val shapeTransform = shapeNode.effectiveTransform
            shapeNode.shape.contours.forEach { c ->
                if (c.closed) {
                    val numPoints = max(6, (c.length * scale / pointDensity).toInt()).coerceAtMost(50)
                    val points = sampleContour(c, numPoints, shapeTransform)
                    if (points.size >= 3) {
                        val softBody = createSoftBody(world, points)
                        softBody.bodies.forEach { body ->
                            val forceX = rng.nextDouble(-maxInitialForce, maxInitialForce).toFloat()
                            val forceY = rng.nextDouble(-maxInitialForce, maxInitialForce).toFloat()
                            body.applyLinearImpulse(Vec2(forceX, forceY), body.worldCenter)
                        }
                        shapes.add(softBody)
                        shapeColors[softBody] = ColorRGBa.WHITE
                        shapeGroups.getOrPut(shapeIndex) { mutableListOf() }.add(softBody)
                    }
                }
            }
        }

        // Inter-shape joints from random lines shot across the scene
        data class Intersection(
            val point: Vector2,
            val isScreenBoundary: Boolean,
            val shape: SoftBody? = null,
            val body: Body? = null
        )

        var jointsCreated = 0
        for (lineAttempt in 0 until numConnectionLines * 2) {
            if (jointsCreated >= maxInterShapeJoints) break

            val angle = rng.nextDouble(0.0, kotlin.math.PI * 2.0)
            val centerX = width / rng.nextDouble(1.0, 5.0)
            val centerY = height / rng.nextDouble(1.0, 5.0)
            val length = max(width, height) * 2.0

            val lineStart = Vector2(centerX + kotlin.math.cos(angle) * length, centerY + kotlin.math.sin(angle) * length)
            val lineEnd = Vector2(centerX - kotlin.math.cos(angle) * length, centerY - kotlin.math.sin(angle) * length)

            val intersections = mutableListOf<Intersection>()

            val screenEdges = listOf(
                Pair(Vector2(0.0, 0.0), Vector2(width.toDouble(), 0.0)),
                Pair(Vector2(width.toDouble(), 0.0), Vector2(width.toDouble(), height.toDouble())),
                Pair(Vector2(width.toDouble(), height.toDouble()), Vector2(0.0, height.toDouble())),
                Pair(Vector2(0.0, height.toDouble()), Vector2(0.0, 0.0))
            )
            for ((edgeStart, edgeEnd) in screenEdges) {
                lineSegmentIntersection(lineStart, lineEnd, edgeStart, edgeEnd)?.let {
                    intersections.add(Intersection(it, isScreenBoundary = true))
                }
            }

            for (shape in shapes) {
                for (j in shape.bodies.indices) {
                    val nextIndex = (j + 1) % shape.bodies.size
                    val p1 = shape.bodies[j].position.toOpenRNDR()
                    val p2 = shape.bodies[nextIndex].position.toOpenRNDR()
                    lineSegmentIntersection(lineStart, lineEnd, p1, p2)?.let { intersection ->
                        findClosestBody(shape, intersection)?.let { closestBody ->
                            intersections.add(Intersection(intersection, isScreenBoundary = false, shape, closestBody))
                        }
                    }
                }
            }

            if (intersections.size % 2 != 0) continue
            intersections.sortBy { (it.point - lineStart).length }

            fun getBodyForIntersection(intersection: Intersection): Body? {
                if (intersection.body != null) return intersection.body
                if (intersection.isScreenBoundary) {
                    val bodyDef = BodyDef().apply {
                        type = BodyType.STATIC
                        position.set(
                            (intersection.point.x / PHYSICS_SCALE).toFloat(),
                            (intersection.point.y / PHYSICS_SCALE).toFloat()
                        )
                    }
                    return world.createBody(bodyDef)
                }
                return null
            }

            for (pairIdx in 0 until intersections.size / 2) {
                if (jointsCreated >= maxInterShapeJoints) break
                val int1 = intersections[pairIdx * 2]
                val int2 = intersections[pairIdx * 2 + 1]
                if (int1.isScreenBoundary && int2.isScreenBoundary) continue
                val body1 = getBodyForIntersection(int1) ?: continue
                val body2 = getBodyForIntersection(int2) ?: continue
                val joint = createSpringJoint(
                    world, body1, body2,
                    frequency = interShapeFrequency.toFloat(),
                    damping = interShapeDamping.toFloat()
                )
                if (joint is DistanceJoint) {
                    interShapeJoints.add(joint)
                    jointsCreated++
                }
            }
        }

        println("Scene initialized: ${shapes.size} shapes, ${shapes.sumOf { it.bodies.size }} bodies, ${interShapeJoints.size} inter-shape joints")

        extend(siteRecorder("00-ptpx", totalFrames))

        extend {
            world.step(1f / 60f, 8, 3)

            drawer.clear(ColorRGBa.BLACK)

            // Gradient background
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                    vec2 screenPos = c_boundsPosition.xy;
                    vec3 color = vec3(0.0);
                    float totalWeight = 0.0;

                    ${gradientPoints.mapIndexed { i, point ->
                        """
                        vec2 p$i = vec2(${point.position.x / width}, ${point.position.y / height});
                        vec3 c$i = vec3(${point.color.r}, ${point.color.g}, ${point.color.b});
                        float d$i = distance(screenPos, p$i);
                        float w$i = 1.0 / (pow(d$i, 3.0) + 0.01);
                        color += c$i * w$i;
                        totalWeight += w$i;
                        """
                    }.joinToString("\n")}

                    color = color / totalWeight;

                    float maxC = max(max(color.r, color.g), color.b);
                    float minC = min(min(color.r, color.g), color.b);
                    float chroma = maxC - minC;

                    if (chroma > 0.01) {
                        vec3 newColor = minC + (color - minC) * 1.5;
                        color = clamp(newColor, 0.0, 1.0);
                    }

                    color = pow(color, vec3(0.9));

                    x_fill.rgb = color;
                    x_fill.a = 1.0;
                """.trimIndent()
            }
            drawer.rectangle(0.0, 0.0, width.toDouble(), height.toDouble())
            drawer.shadeStyle = null

            fun calculateArea(softBody: SoftBody): Double {
                if (softBody.bodies.isEmpty()) return 0.0
                val points = softBody.bodies.map { it.position.toOpenRNDR() }
                var area = 0.0
                for (i in points.indices) {
                    val j = (i + 1) % points.size
                    area += points[i].x * points[j].y
                    area -= points[j].x * points[i].y
                }
                return kotlin.math.abs(area / 2.0)
            }

            shapeGroups.forEach { (_, groupSoftBodies) ->
                if (groupSoftBodies.isEmpty()) return@forEach

                val sortedByArea = groupSoftBodies.sortedByDescending { calculateArea(it) }
                val outerShape = sortedByArea.first()
                val holes = sortedByArea.drop(1)
                val shapeColor = shapeColors[outerShape] ?: ColorRGBa.WHITE

                if (outerShape.bodies.isNotEmpty()) {
                    drawer.fill = shapeColor.opacify(0.95)
                    drawer.stroke = null

                    val outerPoints = outerShape.bodies.map { it.position.toOpenRNDR() }
                    val outerContour = contour {
                        moveTo(outerPoints.first())
                        outerPoints.drop(1).forEach { lineTo(it) }
                        close()
                    }

                    if (holes.isNotEmpty()) {
                        val holeContours = holes.mapNotNull { hole ->
                            if (hole.bodies.isNotEmpty()) {
                                val holePoints = hole.bodies.map { it.position.toOpenRNDR() }
                                contour {
                                    moveTo(holePoints.first())
                                    holePoints.drop(1).forEach { lineTo(it) }
                                    close()
                                }.reversed
                            } else null
                        }
                        drawer.shape(Shape(listOf(outerContour) + holeContours))
                    } else {
                        drawer.contour(outerContour)
                    }
                }

                drawer.fill = null
                drawer.stroke = ColorRGBa.WHITE
                drawer.strokeWeight = 2.0
                groupSoftBodies.forEach { softBody ->
                    for (i in softBody.bodies.indices) {
                        val nextIndex = (i + 1) % softBody.bodies.size
                        drawer.lineSegment(
                            softBody.bodies[i].position.toOpenRNDR(),
                            softBody.bodies[nextIndex].position.toOpenRNDR()
                        )
                    }
                }
            }

            drawer.stroke = ColorRGBa.WHITE
            drawer.strokeWeight = 4.0
            interShapeJoints.forEach { joint ->
                drawer.lineSegment(joint.bodyA.position.toOpenRNDR(), joint.bodyB.position.toOpenRNDR())
            }

            // plot frames
            drawer.stroke = ColorRGBa.WHITE
            drawer.fill = null
            drawer.strokeWeight = 20.0
            drawer.rectangle(0.0, 0.0, width / 2.0, height / 2.0)
            drawer.rectangle(width / 2.0, 0.0, width / 2.0, height / 2.0)
            drawer.rectangle(0.0, height / 2.0, width / 2.0, height / 2.0)
            drawer.rectangle(width / 2.0, height / 2.0, width / 2.0, height / 2.0)
        }
    }
}
