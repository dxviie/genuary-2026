package utils

import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.joints.DistanceJointDef
import org.jbox2d.dynamics.joints.Joint
import org.openrndr.math.Vector2

// Scale factor for physics (Box2D works best with objects sized 0.1 to 10 units)
val PHYSICS_SCALE = 100.0

fun createWall(world: World, x: Float, y: Float, halfWidth: Float, halfHeight: Float): Body {
    val bodyDef = BodyDef().apply {
        position.set(x, y)
        type = BodyType.STATIC
    }
    val body = world.createBody(bodyDef)

    val wallShape = PolygonShape().apply {
        setAsBox(halfWidth, halfHeight)
    }

    val fixtureDef = FixtureDef().apply {
        shape = wallShape
        density = 0f
        restitution = 0.5f  // Walls also bounce
        friction = 0.3f
    }

    body.createFixture(fixtureDef)
    return body
}

// Connect with spring joints (DistanceJoint with frequency)
fun createSpringJoint(world: World, b1: Body, b2: Body, frequency: Float = 5f, damping: Float = 0.5f): Joint {
    val jointDef = DistanceJointDef().apply {
        initialize(b1, b2, b1.position, b2.position)
        frequencyHz = frequency  // Spring stiffness
        dampingRatio = damping   // Spring damping
        collideConnected = false
    }
    return world.createJoint(jointDef)
}

data class SoftBody(
    val bodies: List<Body>,
    val edgeJoints: List<Joint>,
    val diagonalJoints: List<Joint>
)

fun Vector2.toBox2D() = Vec2((x / PHYSICS_SCALE).toFloat(), (y / PHYSICS_SCALE).toFloat())
fun Vec2.toOpenRNDR() = Vector2(x * PHYSICS_SCALE, y * PHYSICS_SCALE)

fun createSoftBody(world: World, points: List<Vector2>): SoftBody {
    if (points.size < 3) return SoftBody(emptyList(), emptyList(), emptyList())

    // Calculate average distance between consecutive points to size the circles appropriately
    var totalDist = 0f
    for (i in points.indices) {
        val nextIndex = (i + 1) % points.size
        val dist = (points[nextIndex] - points[i]).length
        totalDist += dist.toFloat()
    }
    val avgDist = totalDist / points.size
    val circleRadius = 0.05 ;//(avgDist / PHYSICS_SCALE * 0.6f).coerceAtLeast(0.15) // Large enough to overlap

    // Create a dynamic body for each point
    val createdBodies = mutableListOf<Body>()

    for (point in points) {
        val bodyDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(point.toBox2D())
        }

        val body = world.createBody(bodyDef)

        // Add circle shape large enough to overlap with neighbors, creating continuous boundaries
        val circle = CircleShape().apply {
            radius = circleRadius.toFloat()
        }

        val fixtureDef = FixtureDef().apply {
            shape = circle
            density = 1f
            restitution = 0.6f  // Bounciness (0 = no bounce, 1 = perfect bounce)
            friction = 0.3f     // Surface friction
        }

        body.createFixture(fixtureDef)
        createdBodies.add(body)
    }

    // Create spring joints between consecutive points
    val edgeJoints = mutableListOf<Joint>()
    for (i in createdBodies.indices) {
        val nextIndex = (i + 1) % createdBodies.size
        val joint = createSpringJoint(world, createdBodies[i], createdBodies[nextIndex], frequency = 3f, damping = 0.7f)
        edgeJoints.add(joint)
    }

    // Create diagonal joints for stability
    // More complex shapes get more internal cross-bracing
    val diagonalJoints = mutableListOf<Joint>()
    val numBodies = createdBodies.size

    // Skip-2 connections (connect to neighbor's neighbor) for shapes with > 3 vertices
    if (numBodies > 3) {
        for (i in createdBodies.indices) {
            val skipIndex = (i + 2) % numBodies
            val joint = createSpringJoint(world, createdBodies[i], createdBodies[skipIndex], frequency = 2f, damping = 0.8f)
            diagonalJoints.add(joint)
        }
    }

    // Skip-3 connections for shapes with > 5 vertices
    if (numBodies > 5) {
        for (i in createdBodies.indices) {
            val skipIndex = (i + 3) % numBodies
            val joint = createSpringJoint(world, createdBodies[i], createdBodies[skipIndex], frequency = 1.5f, damping = 0.85f)
            diagonalJoints.add(joint)
        }
    }

    // Skip-4 connections for shapes with > 7 vertices
    if (numBodies > 7) {
        for (i in createdBodies.indices) {
            val skipIndex = (i + 4) % numBodies
            val joint = createSpringJoint(world, createdBodies[i], createdBodies[skipIndex], frequency = 1.2f, damping = 0.9f)
            diagonalJoints.add(joint)
        }
    }

    return SoftBody(createdBodies, edgeJoints, diagonalJoints)
}