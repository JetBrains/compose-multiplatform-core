/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.xr.scenecore.testapp.meshentity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.AlphaMode
import androidx.xr.scenecore.CustomMesh
import androidx.xr.scenecore.KhronosPbrMaterial
import androidx.xr.scenecore.MeshBuffer
import androidx.xr.scenecore.MeshEntity
import androidx.xr.scenecore.MeshSubset
import androidx.xr.scenecore.MeshSubsetTopology
import androidx.xr.scenecore.VertexAttribute
import androidx.xr.scenecore.VertexAttributeDescriptor
import androidx.xr.scenecore.VertexAttributeType
import androidx.xr.scenecore.VertexLayout
import androidx.xr.scenecore.scene
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeshEntityActivity : ComponentActivity() {
    private var session: Session? = null
    private var meshEntity: MeshEntity? = null
    private var customMesh: CustomMesh? = null
    private var meshBuffer: MeshBuffer? = null
    private var material: KhronosPbrMaterial? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionResult = Session.create(this)
        if (sessionResult !is SessionCreateSuccess) {
            finish()
            return
        }
        session = sessionResult.session
        session!!.scene.keyEntity = session!!.scene.mainPanelEntity

        createMeshEntity()

        setContent { MeshEntityUI() }
    }

    private fun createMeshEntity() {
        CoroutineScope(Dispatchers.Main).launch {
            val currentSession = session ?: return@launch

            // Define vertex layout: Position (Float3), Normal (Float3), and Color (UByte4Norm)
            val vertexLayout =
                VertexLayout(
                    listOf(
                        VertexAttributeDescriptor(
                            VertexAttribute.POSITION,
                            VertexAttributeType.FLOAT3,
                            0,
                        ),
                        VertexAttributeDescriptor(
                            VertexAttribute.NORMAL,
                            VertexAttributeType.FLOAT3,
                            0,
                        ),
                        VertexAttributeDescriptor(
                            VertexAttribute.COLOR,
                            VertexAttributeType.UBYTE4_NORM,
                            0,
                        ),
                    )
                )

            // Cube vertices (24 vertices, 4 per face)
            // Position (Float3) + Normal (Float3) + Color (UByte4)
            // Stride = 12 + 12 + 4 = 28 bytes
            val vertexCount = 24
            val stride = 28
            val vertexDataSize = vertexCount * stride
            val vertexBuffer =
                ByteBuffer.allocateDirect(vertexDataSize).order(ByteOrder.nativeOrder())

            fun putVertex(
                x: Float,
                y: Float,
                z: Float,
                nx: Float,
                ny: Float,
                nz: Float,
                r: Int,
                g: Int,
                b: Int,
                a: Int,
            ) {
                vertexBuffer.putFloat(x)
                vertexBuffer.putFloat(y)
                vertexBuffer.putFloat(z)
                vertexBuffer.putFloat(nx)
                vertexBuffer.putFloat(ny)
                vertexBuffer.putFloat(nz)
                vertexBuffer.put(r.toByte())
                vertexBuffer.put(g.toByte())
                vertexBuffer.put(b.toByte())
                vertexBuffer.put(a.toByte())
            }

            // Front face (Red)
            putVertex(-0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 255, 0, 0, 255)
            putVertex(0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 255, 0, 0, 255)
            putVertex(0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 255, 0, 0, 255)
            putVertex(-0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 255, 0, 0, 255)

            // Back face (Green)
            putVertex(-0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 0, 255, 0, 255)
            putVertex(-0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 0, 255, 0, 255)
            putVertex(0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 0, 255, 0, 255)
            putVertex(0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 0, 255, 0, 255)

            // Top face (Blue)
            putVertex(-0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f, 0, 0, 255, 255)
            putVertex(-0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0, 0, 255, 255)
            putVertex(0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0, 0, 255, 255)
            putVertex(0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f, 0, 0, 255, 255)

            // Bottom face (Yellow)
            putVertex(-0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f, 255, 255, 0, 255)
            putVertex(0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f, 255, 255, 0, 255)
            putVertex(0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f, 255, 255, 0, 255)
            putVertex(-0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f, 255, 255, 0, 255)

            // Right face (Magenta)
            putVertex(0.5f, -0.5f, -0.5f, 1.0f, 0.0f, 0.0f, 255, 0, 255, 255)
            putVertex(0.5f, 0.5f, -0.5f, 1.0f, 0.0f, 0.0f, 255, 0, 255, 255)
            putVertex(0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.0f, 255, 0, 255, 255)
            putVertex(0.5f, -0.5f, 0.5f, 1.0f, 0.0f, 0.0f, 255, 0, 255, 255)

            // Left face (Cyan)
            putVertex(-0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f, 0, 255, 255, 255)
            putVertex(-0.5f, -0.5f, 0.5f, -1.0f, 0.0f, 0.0f, 0, 255, 255, 255)
            putVertex(-0.5f, 0.5f, 0.5f, -1.0f, 0.0f, 0.0f, 0, 255, 255, 255)
            putVertex(-0.5f, 0.5f, -0.5f, -1.0f, 0.0f, 0.0f, 0, 255, 255, 255)

            vertexBuffer.position(0)

            // Indices for triangles
            val indices =
                intArrayOf(
                    // Front
                    0,
                    1,
                    2,
                    0,
                    2,
                    3,
                    // Back
                    4,
                    5,
                    6,
                    4,
                    6,
                    7,
                    // Top
                    8,
                    9,
                    10,
                    8,
                    10,
                    11,
                    // Bottom
                    12,
                    13,
                    14,
                    12,
                    14,
                    15,
                    // Right
                    16,
                    17,
                    18,
                    16,
                    18,
                    19,
                    // Left
                    20,
                    21,
                    22,
                    20,
                    22,
                    23,
                )

            val indexBuffer =
                ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder())
            val intBuffer = indexBuffer.asIntBuffer()
            intBuffer.put(indices)
            intBuffer.position(0) // Reset position!

            val vertexBuffers = arrayOf(vertexBuffer)
            val vertexBufferSizes = intArrayOf(vertexDataSize)
            meshBuffer =
                MeshBuffer.create(
                    currentSession,
                    vertexLayout,
                    24,
                    indices.size,
                    vertexBuffers,
                    vertexBufferSizes,
                    indexBuffer,
                    indices.size * 4,
                )

            val subsets = listOf(MeshSubset(MeshSubsetTopology.TRIANGLES, 0, indices.size))

            customMesh = CustomMesh.create(currentSession, meshBuffer!!, subsets)

            material = KhronosPbrMaterial.create(currentSession, AlphaMode.OPAQUE)
            material?.setMetallicFactor(0f)
            material?.setRoughnessFactor(1f)

            meshEntity =
                MeshEntity.create(
                    currentSession,
                    customMesh!!,
                    listOf(material!!),
                    Pose(Vector3(2f, 0f, -1f)), // Place 2m to the right and 1m in front
                )
        }
    }

    @Composable
    fun MeshEntityUI() {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Mesh Entity Test", color = Color.White)
            Text(text = "A cube should appear in front of you.", color = Color.White)
        }
    }
}
