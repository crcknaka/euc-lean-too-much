package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.MathUtils
import com.eucleantoomuch.game.ecs.components.FlickeringLampComponent
import com.eucleantoomuch.game.ecs.components.ModelComponent

/**
 * System that updates lamp post flickering effects for night hardcore mode.
 * Controls lamp light on/off states and intensity variations.
 */
class LampFlickerSystem : IteratingSystem(
    Family.all(FlickeringLampComponent::class.java, ModelComponent::class.java).get(),
    8  // Priority
) {
    private val flickerMapper = ComponentMapper.getFor(FlickeringLampComponent::class.java)
    private val modelMapper = ComponentMapper.getFor(ModelComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val flicker = flickerMapper.get(entity)
        val model = modelMapper.get(entity)

        // Update flicker timer
        flicker.flickerTimer -= deltaTime * flicker.flickerSpeed

        if (flicker.flickerTimer <= 0f) {
            if (flicker.isActivelyFlickering) {
                // Toggle light state
                flicker.isLit = !flicker.isLit

                // Set next duration based on state
                flicker.stateDuration = if (flicker.isLit) {
                    MathUtils.random(0.5f, 2f)  // On for 0.5-2 seconds
                } else {
                    MathUtils.random(0.05f, 0.3f)  // Off for 0.05-0.3 seconds (quick flicker)
                }

                // Small chance to stop flickering and stay on
                if (flicker.isLit && MathUtils.random() < 0.1f) {
                    flicker.isActivelyFlickering = false
                    flicker.stateDuration = MathUtils.random(5f, 15f)  // Stay stable for a while
                }
            } else {
                // Not actively flickering - chance to start
                if (MathUtils.random() < 0.05f) {  // 5% chance per state change
                    flicker.isActivelyFlickering = true
                    flicker.stateDuration = 0.1f
                } else {
                    // Chance to turn off completely
                    if (MathUtils.random() < 0.02f) {  // 2% chance to blackout
                        flicker.isLit = false
                        flicker.stateDuration = MathUtils.random(2f, 8f)  // Stay off for a while
                    } else if (!flicker.isLit && MathUtils.random() < 0.3f) {
                        // If off, chance to turn back on
                        flicker.isLit = true
                        flicker.stateDuration = MathUtils.random(5f, 20f)
                    } else {
                        flicker.stateDuration = MathUtils.random(3f, 10f)
                    }
                }
            }

            flicker.flickerTimer = flicker.stateDuration
        }

        // Calculate current intensity
        flicker.currentIntensity = if (flicker.isLit) {
            if (flicker.isActivelyFlickering) {
                // Add subtle intensity variation during active flickering
                flicker.baseIntensity * MathUtils.random(0.7f, 1f)
            } else {
                flicker.baseIntensity
            }
        } else {
            0f
        }

        // Apply intensity to lamp model's emissive material and light pool
        model.modelInstance?.let { instance ->
            instance.materials.forEach { material ->
                // Update lamp bulb emissive
                val emissive = material.get(ColorAttribute.Emissive) as? ColorAttribute
                emissive?.let {
                    // Scale emissive color by intensity
                    val baseColor = 1f  // Warm yellow base
                    it.color.set(
                        baseColor * flicker.currentIntensity,
                        baseColor * 0.85f * flicker.currentIntensity,
                        baseColor * 0.5f * flicker.currentIntensity,
                        1f
                    )
                }

                // Update light pool on ground (has BlendingAttribute for additive blending)
                val blending = material.get(BlendingAttribute.Type) as? BlendingAttribute
                if (blending != null) {
                    val diffuse = material.get(ColorAttribute.Diffuse) as? ColorAttribute
                    diffuse?.let {
                        // Scale light pool color by intensity (base is warm light 1f, 0.95f, 0.7f, 0.12f)
                        it.color.set(
                            1f * flicker.currentIntensity,
                            0.95f * flicker.currentIntensity,
                            0.7f * flicker.currentIntensity,
                            0.12f * flicker.currentIntensity
                        )
                    }
                }
            }
        }
    }
}
