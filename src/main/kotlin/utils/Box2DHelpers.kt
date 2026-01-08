package utils

import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.joints.DistanceJointDef
import org.jbox2d.dynamics.joints.Joint

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