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

package androidx.compose.integration.hero.pokedex.macrobenchmark.target

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This setup activity is responsible for setting up any required data, like fake images for the
 * pokedex, for both Pokedex-Views and Pokedex-Compose.
 *
 * It emits [POKEDEX_SETUP_COMPLETE] when it finishes its operations. It *does not* call [finish] on
 * itself in order to allow benchmarks more control over its lifecycle.
 */
class PokedexSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val amountOfImagesRequested = intent.getIntExtra(EXTRA_AMOUNT_OF_IMAGES, 150)
        val container = FrameLayout(this)
        setContentView(container)
        val statusText =
            TextView(this).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                text = POKEDEX_SETTING_UP_IMAGES
            }
        container.addView(statusText)
        lifecycleScope.launch(Dispatchers.IO) {
            val pokemonToCreateImagesFor =
                findPokemonNamesWithoutCachedImage(filesDir, amountOfImagesRequested)
            createAndStoreGradientImages(pokemonToCreateImagesFor, directory = filesDir)
            withContext(Dispatchers.Main) { statusText.text = POKEDEX_SETUP_COMPLETE }
        }
    }

    companion object {
        private const val POKEDEX_SETUP_COMPLETE = "pokedex-setup-complete"
        private const val POKEDEX_SETTING_UP_IMAGES = "pokedex-setting-up-images"
        private const val EXTRA_AMOUNT_OF_IMAGES = "AMOUNT_OF_IMAGES"
    }
}
