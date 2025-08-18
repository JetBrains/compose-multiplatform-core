/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.xr.runtime.testing

import android.app.Activity
import androidx.annotation.RestrictTo
import androidx.xr.runtime.internal.ExrImageResource
import androidx.xr.runtime.internal.GltfModelResource
import androidx.xr.runtime.internal.KhronosPbrMaterialSpec
import androidx.xr.runtime.internal.MaterialResource
import androidx.xr.runtime.internal.RenderingRuntime
import androidx.xr.runtime.internal.SceneRuntime
import androidx.xr.runtime.internal.TextureResource
import androidx.xr.runtime.internal.TextureSampler
import androidx.xr.runtime.math.Matrix3
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.Vector4
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture

/** Test-only implementation of [SceneRuntime] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeRenderingRuntime(
    private val sceneRuntime: SceneRuntime,
    private val activity: Activity,
) : RenderingRuntime {
    @Suppress("AsyncSuffixFuture")
    override fun loadGltfByAssetName(assetName: String): ListenableFuture<GltfModelResource> =
        immediateFuture(FakeGltfModelResource(0))

    @Suppress("AsyncSuffixFuture")
    override fun loadGltfByByteArray(
        assetData: ByteArray,
        assetKey: String,
    ): ListenableFuture<GltfModelResource> = immediateFuture(FakeGltfModelResource(0))

    @Suppress("AsyncSuffixFuture")
    override fun loadExrImageByAssetName(assetName: String): ListenableFuture<ExrImageResource> =
        immediateFuture(FakeExrImageResource(0))

    @Suppress("AsyncSuffixFuture")
    override fun loadExrImageByByteArray(
        assetData: ByteArray,
        assetKey: String,
    ): ListenableFuture<ExrImageResource> = immediateFuture(FakeExrImageResource(1))

    @Suppress("AsyncSuffixFuture")
    override fun loadTexture(
        assetName: String,
        sampler: TextureSampler,
    ): ListenableFuture<TextureResource> = immediateFailedFuture(NotImplementedError())

    /**
     * For test purposes only.
     *
     * Controls the `TextureResource` instance returned by [borrowReflectionTexture] and
     * [getReflectionTextureFromIbl].
     *
     * <p>Tests can set this property to a [FakeResource] instance to simulate the availability of a
     * reflection texture. This allows verification that the code under test correctly handles the
     * borrowed or retrieved texture. Calling [destroyTexture] will reset this property to `null`,
     * enabling tests to also verify resource cleanup behavior.
     */
    internal var reflectionTexture: FakeResource? = null

    override fun borrowReflectionTexture(): TextureResource? {
        return reflectionTexture
    }

    override fun destroyTexture(texture: TextureResource) {
        reflectionTexture = null
    }

    override fun getReflectionTextureFromIbl(iblToken: ExrImageResource): TextureResource? {
        return reflectionTexture
    }

    /**
     * For test purposes only.
     *
     * A fake implementation of [MaterialResource] used to simulate a water material within the test
     * environment.
     *
     * <p>Instances of this class are created by [createWaterMaterial] and can be accessed for
     * verification via the [createdWaterMaterials] list. Tests can inspect the public properties of
     * this class (e.g., [reflectionMap], [normalTiling]) to confirm that the code under test
     * correctly configures the material's attributes.
     *
     * @param isAlphaMapVersion The value provided during creation, indicating which version of the
     *   water material was requested.
     */
    public class FakeWaterMaterial(public val isAlphaMapVersion: Boolean) : MaterialResource {
        public var reflectionMap: TextureResource? = null
        public var normalMap: TextureResource? = null
        public var normalTiling: Float = 0.0f
        public var normalSpeed: Float = 0.0f
        public var alphaStepMultiplier: Float = 0.0f
        public var alphaMap: TextureResource? = null
        public var normalZ: Float = 0.0f
        public var normalBoundary: Float = 0.0f
    }

    /**
     * For test purposes only.
     *
     * A list of all [FakeWaterMaterial] instances created via [createWaterMaterial]. Tests can
     * inspect this list to verify the number of materials created and to access their properties
     * for further assertions.
     */
    public val createdWaterMaterials: MutableList<FakeWaterMaterial> =
        mutableListOf<FakeWaterMaterial>()

    /**
     * For test purposes only.
     *
     * A fake implementation of [MaterialResource] used to simulate a Khronos PBR material within
     * the test environment.
     *
     * <p>Instances of this class are created by [createKhronosPbrMaterial]. Tests can inspect the
     * public properties of this class (e.g., [baseColorTexture], [metallicFactor]) to confirm that
     * the code under test correctly configures the material's attributes according to the provided
     * specification.
     *
     * @param spec The [KhronosPbrMaterialSpec] provided during creation, which defines the initial
     *   configuration of the material.
     */
    public class FakeKhronosPbrMaterial(public val spec: KhronosPbrMaterialSpec) :
        MaterialResource {
        public var baseColorTexture: TextureResource? = null
        public var baseColorUvTransform: Matrix3? = null
        public var baseColorFactors: Vector4? = null
        public var metallicRoughnessTexture: TextureResource? = null
        public var metallicRoughnessUvTransform: Matrix3? = null
        public var metallicFactor: Float? = null
        public var roughnessFactor: Float? = null
        public var normalTexture: TextureResource? = null
        public var normalUvTransform: Matrix3? = null
        public var normalFactor: Float? = null
        public var ambientOcclusionTexture: TextureResource? = null
        public var ambientOcclusionUvTransform: Matrix3? = null
        public var ambientOcclusionFactor: Float? = null
        public var emissiveTexture: TextureResource? = null
        public var emissiveUvTransform: Matrix3? = null
        public var emissiveFactors: Vector3? = null
        public var clearcoatTexture: TextureResource? = null
        public var clearcoatNormalTexture: TextureResource? = null
        public var clearcoatRoughnessTexture: TextureResource? = null
        public var clearcoatIntensity: Float? = null
        public var clearcoatRoughness: Float? = null
        public var clearcoatNormalFactor: Float? = null
        public var sheenColorTexture: TextureResource? = null
        public var sheenColorFactors: Vector3? = null
        public var sheenRoughnessTexture: TextureResource? = null
        public var sheenRoughnessFactor: Float? = null
        public var transmissionTexture: TextureResource? = null
        public var transmissionUvTransform: Matrix3? = null
        public var transmissionFactor: Float? = null
        public var indexOfRefraction: Float? = null
        public var alphaCutoff: Float? = null
    }

    public val createdKhronosPbrMaterials: MutableList<FakeKhronosPbrMaterial> =
        mutableListOf<FakeKhronosPbrMaterial>()

    @Suppress("AsyncSuffixFuture")
    override fun createWaterMaterial(
        isAlphaMapVersion: Boolean
    ): ListenableFuture<MaterialResource> {
        val newMaterial = FakeWaterMaterial(isAlphaMapVersion)
        createdWaterMaterials.add(newMaterial)
        return immediateFuture(newMaterial)
    }

    override fun destroyWaterMaterial(material: MaterialResource) {
        createdWaterMaterials.remove(material)
    }

    override fun setReflectionMapOnWaterMaterial(
        material: MaterialResource,
        reflectionMap: TextureResource,
    ) {
        (material as? FakeWaterMaterial)?.reflectionMap = reflectionMap
    }

    override fun setNormalMapOnWaterMaterial(
        material: MaterialResource,
        normalMap: TextureResource,
    ) {
        (material as? FakeWaterMaterial)?.normalMap = normalMap
    }

    override fun setNormalTilingOnWaterMaterial(material: MaterialResource, normalTiling: Float) {
        (material as? FakeWaterMaterial)?.normalTiling = normalTiling
    }

    override fun setNormalSpeedOnWaterMaterial(material: MaterialResource, normalSpeed: Float) {
        (material as? FakeWaterMaterial)?.normalSpeed = normalSpeed
    }

    override fun setAlphaStepMultiplierOnWaterMaterial(
        material: MaterialResource,
        alphaStepMultiplier: Float,
    ) {
        (material as? FakeWaterMaterial)?.alphaStepMultiplier = alphaStepMultiplier
    }

    override fun setAlphaMapOnWaterMaterial(material: MaterialResource, alphaMap: TextureResource) {
        (material as? FakeWaterMaterial)?.alphaMap = alphaMap
    }

    override fun setNormalZOnWaterMaterial(material: MaterialResource, normalZ: Float) {
        (material as? FakeWaterMaterial)?.normalZ = normalZ
    }

    override fun setNormalBoundaryOnWaterMaterial(
        material: MaterialResource,
        normalBoundary: Float,
    ) {
        (material as? FakeWaterMaterial)?.normalBoundary = normalBoundary
    }

    @Suppress("AsyncSuffixFuture")
    override fun createKhronosPbrMaterial(
        spec: KhronosPbrMaterialSpec
    ): ListenableFuture<MaterialResource>? {
        val newMaterial = FakeKhronosPbrMaterial(spec)
        createdKhronosPbrMaterials.add(newMaterial)
        return immediateFuture(newMaterial)
    }

    override fun destroyKhronosPbrMaterial(material: MaterialResource) {
        createdKhronosPbrMaterials.remove(material)
    }

    override fun setBaseColorTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        baseColor: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.baseColorTexture = baseColor
    }

    override fun setBaseColorUvTransformOnKhronosPbrMaterial(
        material: MaterialResource,
        uvTransform: Matrix3,
    ) {
        (material as? FakeKhronosPbrMaterial)?.baseColorUvTransform = uvTransform
    }

    override fun setBaseColorFactorsOnKhronosPbrMaterial(
        material: MaterialResource,
        factors: Vector4,
    ) {
        (material as? FakeKhronosPbrMaterial)?.baseColorFactors = factors
    }

    override fun setMetallicRoughnessTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        metallicRoughness: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.metallicRoughnessTexture = metallicRoughness
    }

    override fun setMetallicRoughnessUvTransformOnKhronosPbrMaterial(
        material: MaterialResource,
        uvTransform: Matrix3,
    ) {
        (material as? FakeKhronosPbrMaterial)?.metallicRoughnessUvTransform = uvTransform
    }

    override fun setMetallicFactorOnKhronosPbrMaterial(material: MaterialResource, factor: Float) {
        (material as? FakeKhronosPbrMaterial)?.metallicFactor = factor
    }

    override fun setRoughnessFactorOnKhronosPbrMaterial(material: MaterialResource, factor: Float) {
        (material as? FakeKhronosPbrMaterial)?.roughnessFactor = factor
    }

    override fun setNormalTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        normal: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.normalTexture = normal
    }

    override fun setNormalUvTransformOnKhronosPbrMaterial(
        material: MaterialResource,
        uvTransform: Matrix3,
    ) {
        (material as? FakeKhronosPbrMaterial)?.normalUvTransform = uvTransform
    }

    override fun setNormalFactorOnKhronosPbrMaterial(material: MaterialResource, factor: Float) {
        (material as? FakeKhronosPbrMaterial)?.normalFactor = factor
    }

    override fun setAmbientOcclusionTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        ambientOcclusion: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.ambientOcclusionTexture = ambientOcclusion
    }

    override fun setAmbientOcclusionUvTransformOnKhronosPbrMaterial(
        material: MaterialResource,
        uvTransform: Matrix3,
    ) {
        (material as? FakeKhronosPbrMaterial)?.ambientOcclusionUvTransform = uvTransform
    }

    override fun setAmbientOcclusionFactorOnKhronosPbrMaterial(
        material: MaterialResource,
        factor: Float,
    ) {
        (material as? FakeKhronosPbrMaterial)?.ambientOcclusionFactor = factor
    }

    override fun setEmissiveTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        emissive: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.emissiveTexture = emissive
    }

    override fun setEmissiveUvTransformOnKhronosPbrMaterial(
        material: MaterialResource,
        uvTransform: Matrix3,
    ) {
        (material as? FakeKhronosPbrMaterial)?.emissiveUvTransform = uvTransform
    }

    override fun setEmissiveFactorsOnKhronosPbrMaterial(
        material: MaterialResource,
        factors: Vector3,
    ) {
        (material as? FakeKhronosPbrMaterial)?.emissiveFactors = factors
    }

    override fun setClearcoatTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        clearcoat: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.clearcoatTexture = clearcoat
    }

    override fun setClearcoatNormalTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        clearcoatNormal: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.clearcoatNormalTexture = clearcoatNormal
    }

    override fun setClearcoatRoughnessTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        clearcoatRoughness: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.clearcoatRoughnessTexture = clearcoatRoughness
    }

    override fun setClearcoatFactorsOnKhronosPbrMaterial(
        material: MaterialResource,
        intensity: Float,
        roughness: Float,
        normal: Float,
    ) {
        (material as? FakeKhronosPbrMaterial)?.clearcoatIntensity = intensity
        (material as? FakeKhronosPbrMaterial)?.clearcoatRoughness = roughness
        (material as? FakeKhronosPbrMaterial)?.clearcoatNormalFactor = normal
    }

    override fun setSheenColorTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        sheenColor: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.sheenColorTexture = sheenColor
    }

    override fun setSheenColorFactorsOnKhronosPbrMaterial(
        material: MaterialResource,
        factors: Vector3,
    ) {
        (material as? FakeKhronosPbrMaterial)?.sheenColorFactors = factors
    }

    override fun setSheenRoughnessTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        sheenRoughness: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.sheenRoughnessTexture = sheenRoughness
    }

    override fun setSheenRoughnessFactorOnKhronosPbrMaterial(
        material: MaterialResource,
        factor: Float,
    ) {
        (material as? FakeKhronosPbrMaterial)?.sheenRoughnessFactor = factor
    }

    override fun setTransmissionTextureOnKhronosPbrMaterial(
        material: MaterialResource,
        transmission: TextureResource,
    ) {
        (material as? FakeKhronosPbrMaterial)?.transmissionTexture = transmission
    }

    override fun setTransmissionUvTransformOnKhronosPbrMaterial(
        material: MaterialResource,
        uvTransform: Matrix3,
    ) {
        (material as? FakeKhronosPbrMaterial)?.transmissionUvTransform = uvTransform
    }

    override fun setTransmissionFactorOnKhronosPbrMaterial(
        material: MaterialResource,
        factor: Float,
    ) {
        (material as? FakeKhronosPbrMaterial)?.transmissionFactor = factor
    }

    override fun setIndexOfRefractionOnKhronosPbrMaterial(
        material: MaterialResource,
        indexOfRefraction: Float,
    ) {
        (material as? FakeKhronosPbrMaterial)?.indexOfRefraction = indexOfRefraction
    }

    override fun setAlphaCutoffOnKhronosPbrMaterial(
        material: MaterialResource,
        alphaCutoff: Float,
    ) {
        (material as? FakeKhronosPbrMaterial)?.alphaCutoff = alphaCutoff
    }

    override fun startRenderer() {}

    override fun stopRenderer() {}

    override fun dispose() {}
}
