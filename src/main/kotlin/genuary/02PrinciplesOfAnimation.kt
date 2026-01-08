package genuary

import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
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

        // keep a reference to the recorder so we can start it and stop it.
        val recorder = ScreenRecorder().apply {
            outputToVideo = false
            frameRate = 60  // Match the physics step rate
        }
        extend(recorder)

        mouse.buttonDown.listen {
            val clickPos = mouse.position

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
        }

        // Reset on right click
        mouse.buttonDown.listen { event ->
            if (event.button.ordinal == 1) { // Right mouse button
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

            // Draw current mouse position hint
            drawer.fill = ColorRGBa.BLACK.opacify(0.3)
            drawer.stroke = null
            drawer.circle(mouse.position, 3.0)

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

            // Show controls hint when no shapes exist
            if (allShapes.isEmpty() && currentPoints.isEmpty()) {
                drawer.text("Click to create shapes | 'd' = debug | 'p' = pause | 'c' = clear | 'v' = record", 20.0, height - 20.0)
            }
        }
    }
}