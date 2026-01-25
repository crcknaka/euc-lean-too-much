package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.utils.Pool
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute
import net.mgsx.gltf.scene3d.scene.Scene

class ModelComponent : Component, Pool.Poolable {
    var modelInstance: ModelInstance? = null
    var visible: Boolean = true

    // LOD support - simple model for far distances
    var modelInstanceLod: ModelInstance? = null
    var useLod: Boolean = false  // Currently using LOD model

    // PBR flag - true for GLTF/GLB models that need SceneManager rendering
    var isPbr: Boolean = false

    // Scene for gdx-gltf PBR models (contains proper materials)
    var scene: Scene? = null

    // Brake light material (for EUC models with "BrakeLights" node)
    var brakeLightMaterial: Material? = null
    private var _brakeLightsOn: Boolean = false
    private var blinkTimer: Float = 0f
    private var blinkState: Boolean = false
    private val brakeLightColor = Color(5f, 0f, 0f, 1f)  // Bright red HDR emissive

    override fun reset() {
        modelInstance = null
        modelInstanceLod = null
        visible = true
        useLod = false
        isPbr = false
        scene = null
        brakeLightMaterial = null
        _brakeLightsOn = false
        blinkTimer = 0f
        blinkState = false
    }

    /**
     * Find and store brake light material from the model.
     * Searches by material name, then node name.
     * Call this after model is loaded.
     */
    fun findBrakeLightMaterial() {
        val instance = modelInstance ?: return

        // First: search by material name
        for (material in instance.materials) {
            if (material.id?.contains("BrakeLights", ignoreCase = true) == true) {
                brakeLightMaterial = material
                return
            }
        }

        // Second: search by node name
        for (node in instance.nodes) {
            if (node.id?.contains("BrakeLights", ignoreCase = true) == true) {
                for (part in node.parts) {
                    brakeLightMaterial = part.material
                    return
                }
            }
        }

        // Third: check child nodes recursively
        fun searchChildren(nodes: Iterable<com.badlogic.gdx.graphics.g3d.model.Node>) {
            for (node in nodes) {
                if (node.id?.contains("BrakeLights", ignoreCase = true) == true) {
                    for (part in node.parts) {
                        brakeLightMaterial = part.material
                        return
                    }
                }
                if (node.hasChildren()) {
                    searchChildren(node.children)
                }
            }
        }
        searchChildren(instance.nodes)
    }

    /**
     * Update brake light state - blinking on/off like hazard lights when decelerating
     */
    fun updateBrakeLights(on: Boolean, deltaTime: Float) {
        val material = brakeLightMaterial ?: return

        if (on) {
            // Blinking effect - toggle every 0.2 seconds (like hazard lights)
            blinkTimer += deltaTime
            if (blinkTimer >= 0.2f) {
                blinkTimer = 0f
                blinkState = !blinkState
            }

            // On/off blinking - emissive when blinkState is true, nothing when false
            if (blinkState) {
                if (isPbr) {
                    material.set(PBRColorAttribute.createEmissive(brakeLightColor))
                    material.set(PBRFloatAttribute(PBRFloatAttribute.EmissiveIntensity, 5f))
                } else {
                    material.set(ColorAttribute.createEmissive(brakeLightColor))
                }
            } else {
                if (isPbr) {
                    material.remove(PBRColorAttribute.Emissive)
                    material.remove(PBRFloatAttribute.EmissiveIntensity)
                } else {
                    material.remove(ColorAttribute.Emissive)
                }
            }
            _brakeLightsOn = true
        } else if (_brakeLightsOn) {
            // Turn off completely
            _brakeLightsOn = false
            blinkTimer = 0f
            blinkState = false
            if (isPbr) {
                material.remove(PBRColorAttribute.Emissive)
                material.remove(PBRFloatAttribute.EmissiveIntensity)
            } else {
                material.remove(ColorAttribute.Emissive)
            }
        }
    }

    /**
     * Get the current active model instance based on LOD state
     */
    fun getActiveModel(): ModelInstance? {
        return if (useLod && modelInstanceLod != null) modelInstanceLod else modelInstance
    }
}
