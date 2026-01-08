package utils

import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
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
    body.createFixture(PolygonShape().apply {
        setAsBox(halfWidth, halfHeight)
    }, 0f)
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

    // Create a dynamic body for each point
    val createdBodies = mutableListOf<Body>()

    for (point in points) {
        val bodyDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(point.toBox2D())
        }

        val body = world.createBody(bodyDef)

        // Add a small circle shape
        val circle = CircleShape().apply {
            radius = 0.05f
        }

        body.createFixture(circle, 1f) // density = 1
        createdBodies.add(body)
    }

    // Create spring joints between consecutive points
    val edgeJoints = mutableListOf<Joint>()
    for (i in createdBodies.indices) {
        val nextIndex = (i + 1) % createdBodies.size
        val joint = createSpringJoint(world, createdBodies[i], createdBodies[nextIndex], frequency = 3f, damping = 0.7f)
        edgeJoints.add(joint)
    }

    // Create diagonal joints for stability (connect each point to its neighbors' neighbors)
    val diagonalJoints = mutableListOf<Joint>()
    for (i in createdBodies.indices) {
        val skipIndex = (i + 2) % createdBodies.size
        // Only create if the shape has enough points to avoid duplicate connections
        if (createdBodies.size > 3) {
            val joint = createSpringJoint(world, createdBodies[i], createdBodies[skipIndex], frequency = 2f, damping = 0.8f)
            diagonalJoints.add(joint)
        }
    }

    return SoftBody(createdBodies, edgeJoints, diagonalJoints)
}