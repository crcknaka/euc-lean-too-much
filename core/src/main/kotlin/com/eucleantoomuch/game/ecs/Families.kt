package com.eucleantoomuch.game.ecs

import com.badlogic.ashley.core.Family
import com.eucleantoomuch.game.ecs.components.*

object Families {
    val player: Family = Family.all(
        PlayerComponent::class.java,
        EucComponent::class.java,
        TransformComponent::class.java
    ).get()

    val renderable: Family = Family.all(
        TransformComponent::class.java,
        ModelComponent::class.java
    ).get()

    val collidable: Family = Family.all(
        TransformComponent::class.java,
        ColliderComponent::class.java
    ).get()

    val obstacles: Family = Family.all(
        ObstacleComponent::class.java,
        TransformComponent::class.java,
        ColliderComponent::class.java
    ).get()

    val pedestrians: Family = Family.all(
        PedestrianComponent::class.java,
        TransformComponent::class.java,
        VelocityComponent::class.java
    ).get()

    val cars: Family = Family.all(
        CarComponent::class.java,
        TransformComponent::class.java,
        VelocityComponent::class.java
    ).get()

    val movable: Family = Family.all(
        TransformComponent::class.java,
        VelocityComponent::class.java
    ).get()

    val ground: Family = Family.all(
        GroundComponent::class.java,
        TransformComponent::class.java
    ).get()

    val pigeons: Family = Family.all(
        PigeonComponent::class.java,
        TransformComponent::class.java,
        ModelComponent::class.java
    ).get()

    // Rider entities (have EucComponent, ArmComponent and HeadComponent for animation)
    val rider: Family = Family.all(
        EucComponent::class.java,
        ArmComponent::class.java,
        HeadComponent::class.java
    ).get()
}
