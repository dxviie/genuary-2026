package genuary

import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.jbox2d.dynamics.joints.DistanceJoint
import org.openrndr.KEY_ESCAPE
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.svg.loadSVG
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour
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
 * SVG-based softbody physics with inter-shape joints
 */

fun main() = application {
    configure {
        // Load SVG to get dimensions for window config
        val svgFile = File("data/svg/ptpx-a4.svg")
        val composition = loadSVG(svgFile)

        // Parse viewBox from SVG file if available
        val viewBoxPattern = Regex("""viewBox=["']([^"']+)["']""")
        val svgContent = svgFile.readText()
        val viewBoxMatch = viewBoxPattern.find(svgContent)

        val (svgWidth, svgHeight) = if (viewBoxMatch != null) {
            val viewBoxValues = viewBoxMatch.groupValues[1].trim().split(Regex("\\s+|,"))
            Pair(viewBoxValues[2].toDouble(), viewBoxValues[3].toDouble())
        } else {
            Pair(composition.root.bounds.width, composition.root.bounds.height)
        }

        // Define maximum display size
        val maxDisplayWidth = 1920.0
        val maxDisplayHeight = 1080.0

        // Calculate scale to fit display
        val scaleX = maxDisplayWidth / svgWidth
        val scaleY = maxDisplayHeight / svgHeight
        val displayScale = min(scaleX, scaleY)

        // Set window size
        width = (svgWidth * displayScale).toInt()
        height = (svgHeight * displayScale).toInt()
        if (displays.size > 1) display = displays[1]
    }

    oliveProgram {
        val fontFile = File("data/fonts/default.otf")
        val font = loadFont(fontFile.toURI().toString(), 13.0)

        // Reload SVG (needed for olive hot-reload)
        val svgFile = File("data/svg/ptpx-a4.svg")
        val composition = loadSVG(svgFile)

        // Parse viewBox from SVG file if available
        val viewBoxPattern = Regex("""viewBox=["']([^"']+)["']""")
        val svgContent = svgFile.readText()
        val viewBoxMatch = viewBoxPattern.find(svgContent)

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

        val svgRight = svgLeft + svgWidth
        val svgBottom = svgTop + svgHeight

        // Calculate scale and offset
        val maxDisplayWidth = 1920.0
        val maxDisplayHeight = 1080.0
        val scaleX = maxDisplayWidth / svgWidth
        val scaleY = maxDisplayHeight / svgHeight
        val scale = min(scaleX, scaleY)

        val offsetX = -svgLeft * scale
        val offsetY = -svgTop * scale

        // Box2D setup
        val world = World(Vec2(0f, 0f))  // Gravity pointing down

        // Create walls
        val wallThickness = 50f / PHYSICS_SCALE.toFloat()
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), (height / PHYSICS_SCALE + wallThickness).toFloat(),
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Floor
        createWall(world, (width / 2 / PHYSICS_SCALE).toFloat(), -wallThickness,
                   (width / 2 / PHYSICS_SCALE).toFloat(), wallThickness) // Ceiling
        createWall(world, -wallThickness, (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Left wall
        createWall(world, (width / PHYSICS_SCALE + wallThickness).toFloat(), (height / 2 / PHYSICS_SCALE).toFloat(),
                   wallThickness, (height / 2 / PHYSICS_SCALE).toFloat()) // Right wall

        // State
        val shapes = mutableListOf<SoftBody>()
        val shapeColors = mutableMapOf<SoftBody, ColorRGBa>()
        val interShapeJoints = mutableListOf<DistanceJoint>()
        var debugMode = false
        var paused = true

        // Joint parameters for softbodies
        val edgeFrequency = 4f
        val edgeDamping = 0.7f
        val diagonalFrequency = 2f
        val diagonalDamping = 0.8f
        val enableDiagonalJoints = true  // Disable to reduce Box2D load

        // Inter-shape joint parameters
        val interShapeFrequency = 1.5f
        val interShapeDamping = 0.9f

        // Function to generate random color
        fun randomColor(): ColorRGBa {
            val r = Random.nextDouble(0.3, 1.0)
            val g = Random.nextDouble(0.3, 1.0)
            val b = Random.nextDouble(0.3, 1.0)
            return ColorRGBa(r, g, b, 1.0)
        }

        // Function to transform SVG point to screen space
        fun transformPoint(p: Vector2): Vector2 {
            return Vector2(p.x * scale + offsetX, p.y * scale + offsetY)
        }

        // Function to sample points from a contour with shape transform
        fun sampleContour(contour: ShapeContour, numPoints: Int, shapeTransform: org.openrndr.math.Matrix44): List<Vector2> {
            val points = mutableListOf<Vector2>()
            for (i in 0 until numPoints) {
                val t = i.toDouble() / numPoints
                val point = contour.position(t)
                // Apply shape transform first, then screen transform
                val transformedPoint = (shapeTransform * point.xy01).xy
                points.add(transformPoint(transformedPoint))
            }
            return points
        }

        // Function to check line-segment intersection
        fun lineSegmentIntersection(p1: Vector2, p2: Vector2, p3: Vector2, p4: Vector2): Vector2? {
            val x1 = p1.x
            val y1 = p1.y
            val x2 = p2.x
            val y2 = p2.y
            val x3 = p3.x
            val y3 = p3.y
            val x4 = p4.x
            val y4 = p4.y

            val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
            if (kotlin.math.abs(denom) < 1e-10) return null

            val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
            val u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom

            if (t >= 0.0 && t <= 1.0 && u >= 0.0 && u <= 1.0) {
                return Vector2(x1 + t * (x2 - x1), y1 + t * (y2 - y1))
            }
            return null
        }

        // Function to find closest body to a point in a shape
        fun findClosestBody(shape: SoftBody, point: Vector2): Body? {
            return shape.bodies.minByOrNull { body ->
                val bodyPos = body.position.toOpenRNDR()
                (bodyPos - point).length
            }
        }

        // Function to initialize/reset the scene
        fun initializeScene() {
            // Clear existing shapes and joints
            shapes.forEach { softBody ->
                softBody.bodies.forEach { body -> world.destroyBody(body) }
            }
            shapes.clear()
            shapeColors.clear()

            interShapeJoints.forEach { joint ->
                world.destroyJoint(joint)
            }
            interShapeJoints.clear()

            // Extract shapes from SVG and create softbodies
            composition.findShapes().forEach { shapeNode ->
                // Get the shape's effective transform (includes all parent transforms)
                val shapeTransform = shapeNode.effectiveTransform

                shapeNode.shape.contours.forEach { contour ->
                    if (contour.closed) {
                        // Sample points along the contour (increased distance to reduce body count)
                        val numPoints = max(6, (contour.length * scale / 50.0).toInt()).coerceAtMost(50)
                        val points = sampleContour(contour, numPoints, shapeTransform)

                        if (points.size >= 3) {
                            val softBody = createSoftBody(world, points)

                            // Update edge joint parameters
                            softBody.edgeJoints.forEach { joint ->
                                if (joint is DistanceJoint) {
                                    joint.frequency = edgeFrequency
                                    joint.dampingRatio = edgeDamping
                                }
                            }

                            // Optionally remove diagonal joints to reduce Box2D load
                            if (!enableDiagonalJoints) {
                                // Destroy diagonal joints to reduce interconnections
                                softBody.diagonalJoints.forEach { joint ->
                                    world.destroyJoint(joint)
                                }
                            } else {
                                // Update diagonal joint parameters
                                softBody.diagonalJoints.forEach { joint ->
                                    if (joint is DistanceJoint) {
                                        joint.frequency = diagonalFrequency
                                        joint.dampingRatio = diagonalDamping
                                    }
                                }
                            }

                            // Apply tiny random initial forces to all bodies
                            val maxForce = .003
                            softBody.bodies.forEach { body ->
                                val forceX = Random.nextDouble(-maxForce, maxForce).toFloat()
                                val forceY = Random.nextDouble(-maxForce, maxForce).toFloat()
                                body.applyLinearImpulse(Vec2(forceX, forceY), body.worldCenter)
                            }

                            shapes.add(softBody)
                            shapeColors[softBody] = randomColor()
                        }
                    }
                }
            }

            // Create inter-shape joints by shooting random lines
            val numConnectionLines = 100  // Reduced to prevent Box2D overflow
            val maxInterShapeJoints = 50  // Global limit on inter-shape joints
            var jointsCreated = 0

            for (i in 0 until numConnectionLines) {
                if (jointsCreated >= maxInterShapeJoints) break

                // Generate random line across the scene
                val angle = Random.nextDouble(0.0, kotlin.math.PI * 2.0)
                val centerX = width / 2.0
                val centerY = height / 2.0
                val length = max(width, height) * 1.5

                val lineStart = Vector2(
                    centerX + kotlin.math.cos(angle) * length,
                    centerY + kotlin.math.sin(angle) * length
                )
                val lineEnd = Vector2(
                    centerX - kotlin.math.cos(angle) * length,
                    centerY - kotlin.math.sin(angle) * length
                )

                // Find intersections with shape edges
                data class Intersection(val point: Vector2, val shape: SoftBody, val body: Body)
                val intersections = mutableListOf<Intersection>()

                for (shape in shapes) {
                    for (j in shape.bodies.indices) {
                        val nextIndex = (j + 1) % shape.bodies.size
                        val p1 = shape.bodies[j].position.toOpenRNDR()
                        val p2 = shape.bodies[nextIndex].position.toOpenRNDR()

                        val intersection = lineSegmentIntersection(lineStart, lineEnd, p1, p2)
                        if (intersection != null) {
                            val closestBody = findClosestBody(shape, intersection)
                            if (closestBody != null) {
                                intersections.add(Intersection(intersection, shape, closestBody))
                            }
                        }
                    }
                }

                // Create joints between consecutive intersections
                for (j in 0 until intersections.size - 1) {
                    if (jointsCreated >= maxInterShapeJoints) break

                    val int1 = intersections[j]
                    val int2 = intersections[j + 1]

                    // Only create joint if they're from different shapes
                    if (int1.shape != int2.shape) {
                        val joint = createSpringJoint(
                            world,
                            int1.body,
                            int2.body,
                            frequency = interShapeFrequency,
                            damping = interShapeDamping
                        )
                        if (joint is DistanceJoint) {
                            interShapeJoints.add(joint)
                            jointsCreated++
                        }
                    }
                }
            }

            // Print diagnostics
            val totalBodies = shapes.sumOf { it.bodies.size }
            val totalEdgeJoints = shapes.sumOf { it.edgeJoints.size }
            val diagonalStatus = if (enableDiagonalJoints) {
                val totalDiagonalJoints = shapes.sumOf { it.diagonalJoints.size }
                "$totalDiagonalJoints diagonal joints"
            } else {
                "diagonal joints disabled"
            }
            println("Scene initialized: ${shapes.size} shapes, $totalBodies bodies, " +
                    "$totalEdgeJoints edge joints, $diagonalStatus, " +
                    "${interShapeJoints.size} inter-shape joints")
            val viewBoxStr = if (viewBoxMatch != null) {
                "viewBox($svgLeft, $svgTop, $svgWidth, $svgHeight)"
            } else {
                "no viewBox (using bounds)"
            }
            println("SVG: $viewBoxStr -> screen ($svgLeft, $svgTop) to ($svgRight, $svgBottom)")
            println("Screen mapping: scale=$scale, offset=($offsetX, $offsetY), " +
                    "window=${width}x${height}")
        }

        // Initialize the scene on startup
        initializeScene()

        // Screen recorder
        val recorder = ScreenRecorder().apply {
            outputToVideo = false
            frameRate = 60
        }
        extend(recorder)

        // Keyboard controls
        keyboard.keyDown.listen { event ->
            when (event.name) {
                "d" -> debugMode = !debugMode
                "p" -> paused = !paused
                "c" -> initializeScene()
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

            drawer.clear(ColorRGBa(0.95, 0.96, 0.98, 1.0))
            drawer.fontMap = font

            // Draw SVG bounds rectangle (in debug mode)
            if (debugMode) {
                drawer.fill = null
                drawer.stroke = ColorRGBa.BLUE.opacify(0.5)
                drawer.strokeWeight = 2.0
                drawer.rectangle(0.0, 0.0, width.toDouble(), height.toDouble())
            }

            // Draw all softbody shapes
            shapes.forEach { softBody ->
                val shapeColor = shapeColors[softBody] ?: ColorRGBa.WHITE

                // Draw filled polygon
                if (softBody.bodies.isNotEmpty()) {
                    val polygonPoints = softBody.bodies.map { it.position.toOpenRNDR() }
                    drawer.fill = shapeColor.opacify(0.6)
                    drawer.stroke = null
                    drawer.contour(org.openrndr.shape.contour {
                        moveTo(polygonPoints.first())
                        polygonPoints.drop(1).forEach { lineTo(it) }
                        close()
                    })
                }

                // Draw edges
                drawer.fill = null
                drawer.stroke = ColorRGBa.BLACK
                drawer.strokeWeight = 2.0

                for (i in softBody.bodies.indices) {
                    val nextIndex = (i + 1) % softBody.bodies.size
                    val p1 = softBody.bodies[i].position.toOpenRNDR()
                    val p2 = softBody.bodies[nextIndex].position.toOpenRNDR()
                    drawer.lineSegment(p1, p2)
                }

                // Draw debug info
                if (debugMode) {
                    // Draw bodies
                    drawer.fill = shapeColor
                    drawer.stroke = shapeColor.shade(0.7)
                    drawer.strokeWeight = 1.0
                    softBody.bodies.forEach { body ->
                        val pos = body.position.toOpenRNDR()
                        val radius = body.fixtureList?.shape?.radius ?: 0.05f
                        val visualRadius = radius * PHYSICS_SCALE
                        drawer.circle(pos, visualRadius)
                    }

                    // Draw edge joints in cyan
                    drawer.stroke = ColorRGBa.CYAN
                    drawer.strokeWeight = 2.0
                    softBody.edgeJoints.forEach { joint ->
                        val p1 = joint.bodyA.position.toOpenRNDR()
                        val p2 = joint.bodyB.position.toOpenRNDR()
                        drawer.lineSegment(p1, p2)
                    }

                    // Draw diagonal joints in magenta (if enabled)
                    if (enableDiagonalJoints) {
                        drawer.stroke = ColorRGBa.MAGENTA
                        drawer.strokeWeight = 1.5
                        softBody.diagonalJoints.forEach { joint ->
                            val p1 = joint.bodyA.position.toOpenRNDR()
                            val p2 = joint.bodyB.position.toOpenRNDR()
                            drawer.lineSegment(p1, p2)
                        }
                    }
                }
            }

            // Draw inter-shape joints
            if (debugMode) {
                drawer.stroke = ColorRGBa.RED.opacify(0.5)
                drawer.strokeWeight = 1.0
                interShapeJoints.forEach { joint ->
                    val p1 = joint.bodyA.position.toOpenRNDR()
                    val p2 = joint.bodyB.position.toOpenRNDR()
                    drawer.lineSegment(p1, p2)
                }
            }

            // Draw status text
            drawer.fill = ColorRGBa.BLACK
            drawer.stroke = null

            if (recorder.outputToVideo) {
                drawer.fill = ColorRGBa.RED
                drawer.circle(30.0, 30.0, 10.0)

                if (debugMode) {
                    drawer.fill = ColorRGBa.RED
                    drawer.text("● RECORDING (press 'v' to stop)", 50.0, 35.0)
                }
            }

            if (!recorder.outputToVideo || debugMode) {
                var yPos = if (recorder.outputToVideo) 60.0 else 30.0

                if (paused) {
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("PAUSED (press 'p' to resume)", 20.0, yPos)
                    yPos += 25.0
                }

                if (debugMode) {
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("DEBUG MODE (press 'd' to disable)", 20.0, yPos)
                    yPos += 25.0
                    drawer.text("Shapes: ${shapes.size}", 20.0, yPos)
                    yPos += 25.0
                    drawer.text("Inter-shape joints: ${interShapeJoints.size}", 20.0, yPos)
                    yPos += 25.0
                    drawer.text("SVG: ${svgWidth.toInt()}x${svgHeight.toInt()} " +
                                "@ scale ${String.format("%.2f", scale)}", 20.0, yPos)
                    yPos += 25.0
                    val legend = if (enableDiagonalJoints) {
                        "Red = Inter-shape | Cyan = Edges | Magenta = Diagonals"
                    } else {
                        "Red = Inter-shape | Cyan = Edges"
                    }
                    drawer.text(legend, 20.0, yPos)
                }

                // Show controls hint
                if (shapes.isEmpty()) {
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("No shapes found in SVG", 20.0, height - 40.0)
                } else {
                    drawer.fill = ColorRGBa.BLACK.opacify(0.7)
                    drawer.text("'d' = debug | 'p' = pause | 'c' = reset | 'v' = record | ESC = exit", 20.0, height - 20.0)
                }
            }
        }
    }
}