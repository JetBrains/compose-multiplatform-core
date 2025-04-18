/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.xr.scenecore.samples.svxr_example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

@Suppress("GlobalCoroutineDispatchers")
class SceneViewerSampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intentButton = findViewById(R.id.intentButton) as Button

        intentButton.setOnClickListener() {
            val sceneViewerIntent = Intent(Intent.ACTION_VIEW)
            val intentUri =
                Uri.parse("https://arvr.google.com/scene-viewer/1.2")
                    .buildUpon()
                    .appendQueryParameter("file", THREED_MODEL_URL)
                    .build()
            sceneViewerIntent.setDataAndType(intentUri, MIME_TYPE)
            startActivity(sceneViewerIntent)
        }
    }

    private companion object {
        const val THREED_MODEL_URL =
            "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/FlightHelmet/glTF/FlightHelmet.gltf"
        const val MIME_TYPE = "model/gltf-binary"
    }
}
