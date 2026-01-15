package genuary

import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.jbox2d.dynamics.joints.MouseJoint
import org.jbox2d.dynamics.joints.MouseJointDef
import org.openrndr.KEY_ESCAPE
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.extra.noise.simplex
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Vector2
import utils.PHYSICS_SCALE
import utils.SoftBody
import utils.createSoftBody
import utils.createWall
import utils.toOpenRNDR
import utils.toBox2D
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun main() = application {
    configure {
        width = 1080
        height = 1080
        if (displays.size > 1) display = displays[1]
    }

    oliveProgram {
        // Box2D setup
        val world = World(Vec2(0f, 9.8f)) // Gravity pointing down
        val fontFile = File("data/fonts/default.otf")
        val font = loadFont(fontFile.toURI().toString(), 13.0)

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
        val shapeColors = mutableMapOf<SoftBody, ColorRGBa>() // Store color for each shape
        val closureDistance = 15.0 // Distance threshold to close the shape
        var debugMode = false
        var paused = false

        // Function to generate random pastel color
        fun randomPastelColor(): ColorRGBa {
            val r = Random.nextDouble(0.0, 1.0)
            val g = Random.nextDouble(0.0, 1.0)
            val b = Random.nextDouble(0.0, 1.0)
            return ColorRGBa(r, g, b, 1.0)
        }

        // Joint parameters (adjustable in debug mode)
        var edgeFrequency = 4f
        var edgeDamping = 0.7f
        var diagonalFrequency = 2f
        var diagonalDamping = 0.8f

        // Blob generation parameters
        var blobSegments = 8
        var blobNoiseFactor = 30.0 // Max deviation in pixels
        var blobRadius = 100.0 // Base radius of blob

        // Mouse interaction state
        var mouseJoint: MouseJoint? = null
        var draggedBody: Body? = null
        var lastMousePos = mouse.position
        var mouseVelocity = Vector2.ZERO
        var draggingSlider: String? = null // Track which slider is being dragged

        // Slider positions (will be updated during rendering)
        var sliderRegions = mutableMapOf<String, Pair<Double, Double>>() // name -> (y position, slider x start)

        // Button position (will be updated during rendering)
        var createBlobButtonBounds = Pair(Vector2.ZERO, Vector2.ZERO) // top-left, bottom-right

        // Function to generate a noisy circular blob
        fun generateBlob(): List<Vector2> {
            val points = mutableListOf<Vector2>()
            val numPoints = blobSegments - 1
            val centerX = width / 2.0
            val centerY = height / 2.0

            // Use random seed for noise variation
            val noiseSeed = Random.nextInt(1000000)

            for (i in 0 until numPoints) {
                val angle = (i.toDouble() / numPoints) * 2.0 * PI

                // Use simplex noise to vary the radius
                // Create a point on a circle in noise space for smooth variation
                val noiseX = cos(angle) * 5.0
                val noiseY = sin(angle) * 5.0
                val noiseValue = simplex(noiseSeed, Vector2(noiseX, noiseY))
                val radiusVariation = noiseValue * blobNoiseFactor
                val radius = blobRadius + radiusVariation

                val x = centerX + cos(angle) * radius
                val y = centerY + sin(angle) * radius

                points.add(Vector2(x, y))
            }

            return points
        }

        // Helper function to check if mouse is over a slider
        fun getSliderAt(pos: Vector2): String? {
            if (!debugMode) return null
            val sliderWidth = 200.0
            for ((name, region) in sliderRegions) {
                val (y, x) = region
                if (pos.x >= x && pos.x <= x + sliderWidth && pos.y >= y - 10 && pos.y <= y + 10) {
                    return name
                }
            }
            return null
        }

        // Helper function to update slider value based on mouse position
        fun updateSliderValue(sliderName: String, mouseX: Double) {
            val sliderX = sliderRegions[sliderName]?.second ?: return
            val sliderWidth = 200.0
            val normalized = ((mouseX - sliderX) / sliderWidth).coerceIn(0.0, 1.0)

            when (sliderName) {
                "edgeFreq" -> edgeFrequency = (1f + normalized * 9f).toFloat() // 1-10 Hz
                "edgeDamp" -> edgeDamping = normalized.toFloat() // 0-1
                "diagFreq" -> diagonalFrequency = (1f + normalized * 9f).toFloat() // 1-10 Hz
                "diagDamp" -> diagonalDamping = normalized.toFloat() // 0-1
                "blobSegs" -> blobSegments = (4 + (normalized * 96).toInt()).coerceIn(4, 100) // 4-100 segments
                "blobNoise" -> blobNoiseFactor = normalized * 100.0 // 0-100 pixels
                "blobRadius" -> blobRadius = 50.0 + normalized * 150.0 // 50-200 pixels
            }

            // Update existing joints
            allShapes.forEach { shape ->
                shape.edgeJoints.forEach { joint ->
                    if (joint is org.jbox2d.dynamics.joints.DistanceJoint) {
                        joint.frequency = edgeFrequency
                        joint.dampingRatio = edgeDamping
                    }
                }
                shape.diagonalJoints.forEach { joint ->
                    if (joint is org.jbox2d.dynamics.joints.DistanceJoint) {
                        joint.frequency = diagonalFrequency
                        joint.dampingRatio = diagonalDamping
                    }
                }
            }
        }

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

            // Check if clicking on create blob button
            val (btnTopLeft, btnBottomRight) = createBlobButtonBounds
            if (clickPos.x >= btnTopLeft.x && clickPos.x <= btnBottomRight.x &&
                clickPos.y >= btnTopLeft.y && clickPos.y <= btnBottomRight.y) {
                // Create a new blob
                val blobPoints = generateBlob()
                val newShape = createSoftBody(world, blobPoints)
                // Update joint parameters for the new shape
                newShape.edgeJoints.forEach { joint ->
                    if (joint is org.jbox2d.dynamics.joints.DistanceJoint) {
                        joint.frequency = edgeFrequency
                        joint.dampingRatio = edgeDamping
                    }
                }
                newShape.diagonalJoints.forEach { joint ->
                    if (joint is org.jbox2d.dynamics.joints.DistanceJoint) {
                        joint.frequency = diagonalFrequency
                        joint.dampingRatio = diagonalDamping
                    }
                }
                allShapes.add(newShape)
                shapeColors[newShape] = randomPastelColor()

                // Apply random upward force to shoot it into the sky
                val upwardForce = Random.nextDouble(5.0, 15.0)
                val sidewaysForce = Random.nextDouble(-3.0, 3.0)
                newShape.bodies.forEach { body ->
                    body.applyLinearImpulse(
                        Vec2((sidewaysForce / PHYSICS_SCALE).toFloat(),
                             (-upwardForce / PHYSICS_SCALE).toFloat()), // Negative = up
                        body.worldCenter
                    )
                }

                return@listen
            }

            // Check if clicking on a slider in debug mode
            val slider = getSliderAt(clickPos)
            if (slider != null) {
                draggingSlider = slider
                updateSliderValue(slider, clickPos.x)
                return@listen
            }

            // If we're currently drawing, continue with shape creation
            if (currentPoints.isNotEmpty()) {
                // Check if clicking near the first point to close the shape
                if (currentPoints.size >= 3) {
                    val distToFirst = (clickPos - currentPoints.first()).length
                    if (distToFirst < closureDistance) {
                        // Close the shape and create soft body with current parameters
                        val newShape = createSoftBody(world, currentPoints)
                        // Update joint parameters for the new shape
                        newShape.edgeJoints.forEach { joint ->
                            if (joint is org.jbox2d.dynamics.joints.DistanceJoint) {
                                joint.frequency = edgeFrequency
                                joint.dampingRatio = edgeDamping
                            }
                        }
                        newShape.diagonalJoints.forEach { joint ->
                            if (joint is org.jbox2d.dynamics.joints.DistanceJoint) {
                                joint.frequency = diagonalFrequency
                                joint.dampingRatio = diagonalDamping
                            }
                        }
                        allShapes.add(newShape)
                        shapeColors[newShape] = randomPastelColor()
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

            // Release slider
            draggingSlider = null

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
            val currentMousePos = mouse.position

            // Handle slider dragging
            draggingSlider?.let { slider ->
                updateSliderValue(slider, currentMousePos.x)
                return@listen
            }

            // Track mouse velocity during drag
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
                shapeColors.clear()
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
                    shapeColors.clear()
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
//                world.gravity = Vec2(9.8f * sin(seconds.toFloat()/ 3), 9.8f * cos(seconds.toFloat()/3))
            }

            drawer.clear(ColorRGBa(0.95, 0.96, 0.98, 1.0)) // Light blue-gray background
            drawer.fontMap = font

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
                // Get the color for this shape (or use white as fallback)
                val shapeColor = shapeColors[softBody] ?: ColorRGBa.WHITE

                // Draw filled polygon for the soft body
                if (softBody.bodies.isNotEmpty()) {
                    val polygonPoints = softBody.bodies.map { it.position.toOpenRNDR() }
                    drawer.fill = shapeColor.opacify(0.5)
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
                drawer.fill = shapeColor
                drawer.stroke = shapeColor.shade(0.7)
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

            // Draw "Create Blob" button in top-right corner (always visible)
            val buttonWidth = 120.0
            val buttonHeight = 40.0
            val buttonX = width - buttonWidth - 20.0
            val buttonY = 20.0
            createBlobButtonBounds = Pair(
                Vector2(buttonX, buttonY),
                Vector2(buttonX + buttonWidth, buttonY + buttonHeight)
            )

            drawer.fill = ColorRGBa.WHITE
            drawer.stroke = ColorRGBa.BLACK
            drawer.strokeWeight = 2.0
            drawer.rectangle(buttonX, buttonY, buttonWidth, buttonHeight)

            drawer.fill = ColorRGBa.BLACK
            drawer.stroke = null
            drawer.text("Create Blob", buttonX + 15.0, buttonY + 25.0)

            // Draw status text
            drawer.fill = ColorRGBa.BLACK
            drawer.stroke = null

            // When recording, only show red dot unless in debug mode
            if (recorder.outputToVideo) {
                drawer.fill = ColorRGBa.RED
                drawer.circle(30.0, 30.0, 10.0)

                if (debugMode) {
                    drawer.fill = ColorRGBa.RED
                    drawer.text("● RECORDING (press 'v' to stop)", 50.0, 35.0)
                    drawer.fill = ColorRGBa.BLACK
                }
            }

            // Show other info only if not recording OR in debug mode
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
                    drawer.text("Cyan = Edge joints | Magenta = Diagonal joints", 20.0, yPos)
                    yPos += 30.0

                    // Draw sliders for joint parameters
                    sliderRegions.clear()
                    drawer.text("Joint Parameters:", 20.0, yPos)
                    yPos += 20.0

                    val edgeFreqSliderX = 200.0
                    val sliderWidth = 200.0

                    // Edge frequency slider
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Edge Frequency: ${String.format("%.1f", edgeFrequency)}", 20.0, yPos)
                    var sliderY = yPos - 10.0
                    sliderRegions["edgeFreq"] = Pair(yPos - 5.0, edgeFreqSliderX)
                    drawer.fill = ColorRGBa.GRAY
                    drawer.rectangle(edgeFreqSliderX, sliderY, sliderWidth, 10.0)
                    drawer.fill = ColorRGBa.BLUE
                    val edgeFreqPos = ((edgeFrequency - 1f) / 9f) * sliderWidth
                    drawer.circle(edgeFreqSliderX + edgeFreqPos, sliderY + 5.0, 7.0)
                    yPos += 25.0

                    // Edge damping slider
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Edge Damping: ${String.format("%.2f", edgeDamping)}", 20.0, yPos)
                    sliderY = yPos - 10.0
                    sliderRegions["edgeDamp"] = Pair(yPos - 5.0, edgeFreqSliderX)
                    drawer.fill = ColorRGBa.GRAY
                    drawer.rectangle(edgeFreqSliderX, sliderY, sliderWidth, 10.0)
                    drawer.fill = ColorRGBa.BLUE
                    val edgeDampPos = edgeDamping * sliderWidth
                    drawer.circle(edgeFreqSliderX + edgeDampPos, sliderY + 5.0, 7.0)
                    yPos += 25.0

                    // Diagonal frequency slider
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Diagonal Frequency: ${String.format("%.1f", diagonalFrequency)}", 20.0, yPos)
                    sliderY = yPos - 10.0
                    sliderRegions["diagFreq"] = Pair(yPos - 5.0, edgeFreqSliderX)
                    drawer.fill = ColorRGBa.GRAY
                    drawer.rectangle(edgeFreqSliderX, sliderY, sliderWidth, 10.0)
                    drawer.fill = ColorRGBa.BLUE
                    val diagFreqPos = ((diagonalFrequency - 1f) / 9f) * sliderWidth
                    drawer.circle(edgeFreqSliderX + diagFreqPos, sliderY + 5.0, 7.0)
                    yPos += 25.0

                    // Diagonal damping slider
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Diagonal Damping: ${String.format("%.2f", diagonalDamping)}", 20.0, yPos)
                    sliderY = yPos - 10.0
                    sliderRegions["diagDamp"] = Pair(yPos - 5.0, edgeFreqSliderX)
                    drawer.fill = ColorRGBa.GRAY
                    drawer.rectangle(edgeFreqSliderX, sliderY, sliderWidth, 10.0)
                    drawer.fill = ColorRGBa.BLUE
                    val diagDampPos = diagonalDamping * sliderWidth
                    drawer.circle(edgeFreqSliderX + diagDampPos, sliderY + 5.0, 7.0)
                    yPos += 30.0

                    // Blob generation parameters section
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Blob Generation:", 20.0, yPos)
                    yPos += 20.0

                    // Blob segments slider
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Segments: $blobSegments", 20.0, yPos)
                    sliderY = yPos - 10.0
                    sliderRegions["blobSegs"] = Pair(yPos - 5.0, edgeFreqSliderX)
                    drawer.fill = ColorRGBa.GRAY
                    drawer.rectangle(edgeFreqSliderX, sliderY, sliderWidth, 10.0)
                    drawer.fill = ColorRGBa.GREEN
                    val blobSegsPos = ((blobSegments - 4).toDouble() / 96.0) * sliderWidth
                    drawer.circle(edgeFreqSliderX + blobSegsPos, sliderY + 5.0, 7.0)
                    yPos += 25.0

                    // Blob noise factor slider
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Noise Factor: ${String.format("%.1f", blobNoiseFactor)}", 20.0, yPos)
                    sliderY = yPos - 10.0
                    sliderRegions["blobNoise"] = Pair(yPos - 5.0, edgeFreqSliderX)
                    drawer.fill = ColorRGBa.GRAY
                    drawer.rectangle(edgeFreqSliderX, sliderY, sliderWidth, 10.0)
                    drawer.fill = ColorRGBa.GREEN
                    val blobNoisePos = (blobNoiseFactor / 100.0) * sliderWidth
                    drawer.circle(edgeFreqSliderX + blobNoisePos, sliderY + 5.0, 7.0)
                    yPos += 25.0

                    // Blob radius slider
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Blob Radius: ${String.format("%.0f", blobRadius)}", 20.0, yPos)
                    sliderY = yPos - 10.0
                    sliderRegions["blobRadius"] = Pair(yPos - 5.0, edgeFreqSliderX)
                    drawer.fill = ColorRGBa.GRAY
                    drawer.rectangle(edgeFreqSliderX, sliderY, sliderWidth, 10.0)
                    drawer.fill = ColorRGBa.GREEN
                    val blobRadiusPos = ((blobRadius - 50.0) / 150.0) * sliderWidth
                    drawer.circle(edgeFreqSliderX + blobRadiusPos, sliderY + 5.0, 7.0)
                }

                // Show controls hint
                if (allShapes.isEmpty() && currentPoints.isEmpty()) {
                    drawer.fill = ColorRGBa.BLACK
                    drawer.text("Click to create shapes | Drag shapes to throw them around", 20.0, height - 40.0)
                    drawer.text("'d' = debug | 'p' = pause | 'c' = clear | 'v' = record", 20.0, height - 20.0)
                } else if (allShapes.isNotEmpty() && currentPoints.isEmpty()) {
                    drawer.fill = ColorRGBa.BLACK.opacify(0.7)
                    drawer.text("Click & drag to throw shapes | Click empty space to draw new shape", 20.0, height - 20.0)
                }
            }
        }
    }
}