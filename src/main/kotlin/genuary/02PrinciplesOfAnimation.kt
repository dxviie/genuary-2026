package genuary

import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.jbox2d.dynamics.joints.MouseJoint
import org.jbox2d.dynamics.joints.MouseJointDef
import org.openrndr.KEY_ESCAPE
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Vector2
import utils.PHYSICS_SCALE
import utils.SoftBody
import utils.createSoftBody
import utils.createWall
import utils.toOpenRNDR
import utils.toBox2D
import kotlin.math.cos
import kotlin.math.sin

fun main() = application {
    configure {
        width = 1080
        height = 1080
        display = displays[1]
    }

    oliveProgram {
        // Box2D setup
        val world = World(Vec2(0f, 9.8f)) // Gravity pointing down

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

        // State for shape creation
        val currentPoints = mutableListOf<Vector2>()
        val allShapes = mutableListOf<SoftBody>() // Store all created shapes
        val closureDistance = 15.0 // Distance threshold to close the shape
        var debugMode = false
        var paused = false

        // Mouse interaction state
        var mouseJoint: MouseJoint? = null
        var draggedBody: Body? = null
        var lastMousePos = mouse.position
        var mouseVelocity = Vector2.ZERO

        // Helper function to check if a point is inside a polygon using ray casting
        fun pointInPolygon(point: Vector2, polygon: List<Vector2>): Boolean {
            var inside = false
            var j = polygon.size - 1
            for (i in polygon.indices) {
                val xi = polygon[i].x
                val yi = polygon[i].y
                val xj = polygon[j].x
                val yj = polygon[j].y

                val intersect = ((yi > point.y) != (yj > point.y)) &&
                        (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
                if (intersect) inside = !inside
                j = i
            }
            return inside
        }

        // Helper function to find body at position
        fun findBodyAtPosition(pos: Vector2): Body? {
            for (shape in allShapes) {
                // Get polygon vertices from body positions
                val polygonPoints = shape.bodies.map { it.position.toOpenRNDR() }

                // Check if click is inside the polygon
                if (pointInPolygon(pos, polygonPoints)) {
                    // Return the body closest to the click position
                    return shape.bodies.minByOrNull { body ->
                        val bodyPos = body.position.toOpenRNDR()
                        (pos - bodyPos).length
                    }
                }
            }
            return null
        }

        // keep a reference to the recorder so we can start it and stop it.
        val recorder = ScreenRecorder().apply {
            outputToVideo = false
            frameRate = 60  // Match the physics step rate
        }
        extend(recorder)

        mouse.buttonDown.listen { event ->
            if (event.button.ordinal != 0) return@listen // Only handle left click

            val clickPos = mouse.position

            // If we're currently drawing, continue with shape creation
            if (currentPoints.isNotEmpty()) {
                // Check if clicking near the first point to close the shape
                if (currentPoints.size >= 3) {
                    val distToFirst = (clickPos - currentPoints.first()).length
                    if (distToFirst < closureDistance) {
                        // Close the shape and create soft body
                        val newShape = createSoftBody(world, currentPoints)
                        allShapes.add(newShape)
                        currentPoints.clear()
                        return@listen
                    }
                }

                // Add new point
                currentPoints.add(clickPos)
            } else {
                // Not drawing - check if clicking on a body to drag
                val body = findBodyAtPosition(clickPos)
                if (body != null) {
                    draggedBody = body

                    // Create mouse joint for dragging
                    val groundBody = world.bodyList // Get the first static body (ground)
                    val jointDef = MouseJointDef().apply {
                        bodyA = groundBody
                        bodyB = body
                        target.set(clickPos.toBox2D())
                        maxForce = 1000f * body.mass
                        frequencyHz = 5f
                        dampingRatio = 0.7f
                    }
                    mouseJoint = world.createJoint(jointDef) as MouseJoint
                } else {
                    // Start a new shape
                    currentPoints.add(clickPos)
                }
            }
        }

        mouse.buttonUp.listen { event ->
            if (event.button.ordinal != 0) return@listen // Only handle left click

            // Release the mouse joint and apply throw velocity
            mouseJoint?.let { joint ->
                world.destroyJoint(joint)

                // Apply impulse based on mouse velocity for throwing
                draggedBody?.let { body ->
                    val impulse = mouseVelocity * (body.mass * 0.5)
                    body.applyLinearImpulse(
                        Vec2((impulse.x / PHYSICS_SCALE).toFloat(),
                             (impulse.y / PHYSICS_SCALE).toFloat()),
                        body.worldCenter
                    )
                }
            }

            mouseJoint = null
            draggedBody = null
        }

        mouse.moved.listen {
            // Track mouse velocity for throwing
            val currentMousePos = mouse.position
            mouseVelocity = (currentMousePos - lastMousePos) * 60.0 // Scale by frame rate
            lastMousePos = currentMousePos

            // Update mouse joint target if dragging
            mouseJoint?.let { joint ->
                joint.target = currentMousePos.toBox2D()
            }
        }

        mouse.dragged.listen {
            // Track mouse velocity during drag
            val currentMousePos = mouse.position
            mouseVelocity = (currentMousePos - lastMousePos) * 60.0
            lastMousePos = currentMousePos

            // Update mouse joint target
            mouseJoint?.let { joint ->
                joint.target = currentMousePos.toBox2D()
            }
        }

        // Reset on right click
        mouse.buttonDown.listen { event ->
            if (event.button.ordinal == 1) { // Right mouse button
                // Clean up mouse joint if active
                mouseJoint?.let { world.destroyJoint(it) }
                mouseJoint = null
                draggedBody = null

                currentPoints.clear()
                allShapes.forEach { shape ->
                    shape.bodies.forEach { body -> world.destroyBody(body) }
                }
                allShapes.clear()
            }
        }

        // Keyboard controls
        keyboard.keyDown.listen { event ->
            when (event.name) {
                "d" -> debugMode = !debugMode
                "p" -> paused = !paused
                "c" -> {
                    // Clear the scene
                    // Clean up mouse joint if active
                    mouseJoint?.let { world.destroyJoint(it) }
                    mouseJoint = null
                    draggedBody = null

                    currentPoints.clear()
                    allShapes.forEach { shape ->
                        shape.bodies.forEach { body -> world.destroyBody(body) }
                    }
                    allShapes.clear()
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
//                world.gravity = Vec2(9.8f * sin(seconds.toFloat()), 9.8f * cos(seconds.toFloat()))
            }

            drawer.clear(ColorRGBa.PINK)

            // Draw the shape being created
            if (currentPoints.isNotEmpty()) {
                drawer.fill = null
                drawer.stroke = ColorRGBa.BLACK
                drawer.strokeWeight = 2.0

                // Draw lines between consecutive points
                for (i in 0 until currentPoints.size - 1) {
                    drawer.lineSegment(currentPoints[i], currentPoints[i + 1])
                }

                // Draw points
                drawer.fill = ColorRGBa.BLACK
                drawer.stroke = null
                currentPoints.forEach { point ->
                    drawer.circle(point, 5.0)
                }

                // Highlight first point if we have enough points
                if (currentPoints.size >= 3) {
                    drawer.fill = ColorRGBa.RED.opacify(0.5)
                    drawer.circle(currentPoints.first(), closureDistance)
                    drawer.fill = ColorRGBa.RED
                    drawer.circle(currentPoints.first(), 7.0)
                }
            }

            // Draw all soft body shapes
            allShapes.forEach { softBody ->
                // Draw filled polygon for the soft body
                if (softBody.bodies.isNotEmpty()) {
                    val polygonPoints = softBody.bodies.map { it.position.toOpenRNDR() }
                    drawer.fill = ColorRGBa.WHITE.opacify(0.3)
                    drawer.stroke = null
                    drawer.contour(org.openrndr.shape.contour {
                        moveTo(polygonPoints.first())
                        polygonPoints.drop(1).forEach { lineTo(it) }
                        close()
                    })
                }

                // Draw debug joints if debug mode is on
                if (debugMode) {
                    // Draw edge joints in cyan
                    drawer.stroke = ColorRGBa.CYAN
                    drawer.strokeWeight = 2.0
                    softBody.edgeJoints.forEach { joint ->
                        val p1 = joint.bodyA.position.toOpenRNDR()
                        val p2 = joint.bodyB.position.toOpenRNDR()
                        drawer.lineSegment(p1, p2)
                    }

                    // Draw diagonal joints in magenta
                    drawer.stroke = ColorRGBa.MAGENTA
                    drawer.strokeWeight = 1.5
                    softBody.diagonalJoints.forEach { joint ->
                        val p1 = joint.bodyA.position.toOpenRNDR()
                        val p2 = joint.bodyB.position.toOpenRNDR()
                        drawer.lineSegment(p1, p2)
                    }
                } else {
                    // Normal mode: draw edge connections only
                    drawer.fill = null
                    drawer.stroke = ColorRGBa.BLACK
                    drawer.strokeWeight = 2.0

                    for (i in softBody.bodies.indices) {
                        val nextIndex = (i + 1) % softBody.bodies.size
                        val p1 = softBody.bodies[i].position.toOpenRNDR()
                        val p2 = softBody.bodies[nextIndex].position.toOpenRNDR()
                        drawer.lineSegment(p1, p2)
                    }
                }

                // Draw bodies at their actual physics size
                drawer.fill = ColorRGBa.WHITE
                drawer.stroke = ColorRGBa.BLACK
                drawer.strokeWeight = 1.0
                softBody.bodies.forEach { body ->
                    val pos = body.position.toOpenRNDR()
                    // Get the radius from the first fixture (the circle)
                    val radius = body.fixtureList?.shape?.radius ?: 0.05f
                    val visualRadius = radius * PHYSICS_SCALE
                    drawer.circle(pos, visualRadius)
                }
            }

            // Draw drag indicator if dragging
            draggedBody?.let { body ->
                val bodyPos = body.position.toOpenRNDR()
                drawer.stroke = ColorRGBa.BLUE
                drawer.strokeWeight = 2.0
                drawer.lineSegment(bodyPos, mouse.position)

                drawer.fill = ColorRGBa.BLUE.opacify(0.5)
                drawer.stroke = null
                drawer.circle(mouse.position, 8.0)
            }

            // Draw current mouse position hint
            if (draggedBody == null) {
                drawer.fill = ColorRGBa.BLACK.opacify(0.3)
                drawer.stroke = null
                drawer.circle(mouse.position, 3.0)
            }

            // Draw status text
            drawer.fill = ColorRGBa.BLACK
            drawer.stroke = null
            var yPos = 30.0
            if (paused) {
                drawer.text("PAUSED (press 'p' to resume)", 20.0, yPos)
                yPos += 25.0
            }
            if (debugMode) {
                drawer.text("DEBUG MODE (press 'd' to disable)", 20.0, yPos)
                yPos += 25.0
                drawer.text("Cyan = Edge joints | Magenta = Diagonal joints", 20.0, yPos)
            }
            if (recorder.outputToVideo) {
                drawer.fill = ColorRGBa.RED
                drawer.text("● RECORDING (press 'v' to stop)", 20.0, yPos)
                drawer.fill = ColorRGBa.BLACK
            }

            // Show controls hint
            if (allShapes.isEmpty() && currentPoints.isEmpty()) {
                drawer.text("Click to create shapes | Drag shapes to throw them around", 20.0, height - 40.0)
                drawer.text("'d' = debug | 'p' = pause | 'c' = clear | 'v' = record", 20.0, height - 20.0)
            } else if (allShapes.isNotEmpty() && currentPoints.isEmpty()) {
                drawer.fill = ColorRGBa.BLACK.opacify(0.7)
                drawer.text("Click & drag to throw shapes | Click empty space to draw new shape", 20.0, height - 20.0)
            }
        }
    }
}