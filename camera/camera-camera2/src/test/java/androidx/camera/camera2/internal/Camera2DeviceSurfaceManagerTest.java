/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.camera2.internal;

import static androidx.testutils.AssertionsKt.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.os.Looper;
import android.util.Size;
import android.view.WindowManager;

import androidx.camera.camera2.internal.compat.CameraCharacteristicsCompat;
import androidx.camera.camera2.internal.compat.CameraManagerCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraXConfig;
import androidx.camera.core.InitializationException;
import androidx.camera.core.impl.CameraDeviceSurfaceManager;
import androidx.camera.core.impl.CameraMode;
import androidx.camera.core.impl.CameraUpdateException;
import androidx.camera.core.impl.ImageFormatConstants;
import androidx.camera.core.impl.StreamUseCase;
import androidx.camera.core.impl.SurfaceConfig;
import androidx.camera.core.impl.SurfaceConfig.ConfigSize;
import androidx.camera.core.impl.SurfaceConfig.ConfigType;
import androidx.camera.core.impl.UseCaseConfigFactory;
import androidx.camera.testing.fakes.FakeCamera;
import androidx.camera.testing.impl.CameraUtil;
import androidx.camera.testing.impl.CameraXUtil;
import androidx.camera.testing.impl.fakes.FakeCameraFactory;
import androidx.test.core.app.ApplicationProvider;

import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowCameraCharacteristics;
import org.robolectric.shadows.ShadowCameraManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Robolectric test for {@link Camera2DeviceSurfaceManager} class */
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
public final class Camera2DeviceSurfaceManagerTest {
    private static final String LEGACY_CAMERA_ID = "0";
    private static final String LIMITED_CAMERA_ID = "1";
    private static final String FULL_CAMERA_ID = "2";
    private static final String LEVEL3_CAMERA_ID = "3";
    private static final int DEFAULT_SENSOR_ORIENTATION = 90;
    private static final StreamUseCase DEFAULT_STREAM_USE_CASE =
            SurfaceConfig.DEFAULT_STREAM_USE_CASE;
    private final Size mDisplaySize = new Size(1280, 720);
    private final Size mAnalysisSize = new Size(640, 480);
    private final Size mPreviewSize = mDisplaySize;
    private final Size mRecordSize = new Size(3840, 2160);
    private final Size mMaximumSize = new Size(4032, 3024);
    private final Size mMaximumVideoSize = new Size(1920, 1080);
    private final CamcorderProfileHelper mMockCamcorderProfileHelper =
            Mockito.mock(CamcorderProfileHelper.class);
    private final CamcorderProfile mMockCamcorderProfile = Mockito.mock(CamcorderProfile.class);
    /**
     * Except for ImageFormat.JPEG or ImageFormat.YUV, other image formats will be mapped to
     * ImageFormat.PRIVATE (0x22) including SurfaceTexture or MediaCodec classes. Before Android
     * level 23, there is no ImageFormat.PRIVATE. But there is same internal code 0x22 for internal
     * corresponding format HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED. Therefore, set 0x22 as default
     * image formate.
     */
    private final int[] mSupportedFormats =
            new int[]{
                    ImageFormat.YUV_420_888,
                    ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_JPEG,
                    ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE
            };

    private final Size[] mSupportedSizes =
            new Size[]{
                    new Size(4032, 3024),
                    new Size(3840, 2160),
                    new Size(1920, 1080),
                    new Size(1280, 720),
                    new Size(640, 480),
                    new Size(320, 240),
                    new Size(320, 180)
            };

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private CameraDeviceSurfaceManager mSurfaceManager;
    private FakeCameraFactory mCameraFactory;

    @Before
    @SuppressWarnings("deprecation")  /* defaultDisplay */
    public void setUp() throws IllegalAccessException {
        DisplayInfoManager.releaseInstance();
        WindowManager windowManager =
                (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Shadows.shadowOf(windowManager.getDefaultDisplay()).setRealWidth(mDisplaySize.getWidth());
        Shadows.shadowOf(windowManager.getDefaultDisplay()).setRealHeight(mDisplaySize.getHeight());

        when(mMockCamcorderProfileHelper.hasProfile(anyInt(), anyInt())).thenReturn(true);
        ReflectionUtils.setVariableValueInObject(mMockCamcorderProfile, "videoFrameWidth", 3840);
        ReflectionUtils.setVariableValueInObject(mMockCamcorderProfile, "videoFrameHeight", 2160);
        when(mMockCamcorderProfileHelper.get(anyInt(), anyInt())).thenReturn(mMockCamcorderProfile);

        setupCamera();
    }

    @After
    public void tearDown() throws ExecutionException, InterruptedException, TimeoutException {
        // Executes any pending tasks on the main looper, including LiveData observers.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        CameraXUtil.shutdown().get(10000, TimeUnit.MILLISECONDS);
    }

    private CameraManagerCompat getCameraManagerCompat() {
        return CameraManagerCompat.from((Context) ApplicationProvider.getApplicationContext());
    }

    private CameraCharacteristicsCompat getCameraCharacteristicsCompat(String cameraId)
            throws CameraAccessException {
        CameraManager cameraManager =
                (CameraManager) ApplicationProvider.getApplicationContext().getSystemService(
                        Context.CAMERA_SERVICE);

        return CameraCharacteristicsCompat.toCameraCharacteristicsCompat(
                cameraManager.getCameraCharacteristics(cameraId), cameraId);
    }

    @Test
    public void transformSurfaceConfigWithYUVAnalysisSize() {
        SurfaceConfig surfaceConfig = mSurfaceManager.transformSurfaceConfig(
                CameraMode.DEFAULT,
                LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mAnalysisSize, DEFAULT_STREAM_USE_CASE);
        SurfaceConfig expectedSurfaceConfig =
                SurfaceConfig.create(ConfigType.YUV, ConfigSize.VGA);
        assertEquals(expectedSurfaceConfig, surfaceConfig);
    }

    @Test
    public void transformSurfaceConfigWithYUVPreviewSize() {
        SurfaceConfig surfaceConfig = mSurfaceManager.transformSurfaceConfig(
                CameraMode.DEFAULT,
                LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mPreviewSize, DEFAULT_STREAM_USE_CASE);
        SurfaceConfig expectedSurfaceConfig =
                SurfaceConfig.create(ConfigType.YUV, ConfigSize.PREVIEW);
        assertEquals(expectedSurfaceConfig, surfaceConfig);
    }

    @Test
    public void transformSurfaceConfigWithYUVRecordSize() {
        SurfaceConfig surfaceConfig = mSurfaceManager.transformSurfaceConfig(
                CameraMode.DEFAULT,
                LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mRecordSize, DEFAULT_STREAM_USE_CASE);
        SurfaceConfig expectedSurfaceConfig =
                SurfaceConfig.create(ConfigType.YUV, SurfaceConfig.ConfigSize.RECORD);
        assertEquals(expectedSurfaceConfig, surfaceConfig);
    }

    @Test
    public void transformSurfaceConfigWithYUVMaximumSize() {
        SurfaceConfig surfaceConfig = mSurfaceManager.transformSurfaceConfig(
                CameraMode.DEFAULT,
                LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mMaximumSize, DEFAULT_STREAM_USE_CASE);
        SurfaceConfig expectedSurfaceConfig =
                SurfaceConfig.create(SurfaceConfig.ConfigType.YUV, ConfigSize.MAXIMUM);
        assertEquals(expectedSurfaceConfig, surfaceConfig);
    }

    @Test
    public void transformSurfaceConfigWithJPEGAnalysisSize() {
        SurfaceConfig surfaceConfig =
                mSurfaceManager.transformSurfaceConfig(
                        CameraMode.DEFAULT,
                        LEGACY_CAMERA_ID, ImageFormat.JPEG, mAnalysisSize, DEFAULT_STREAM_USE_CASE);
        SurfaceConfig expectedSurfaceConfig =
                SurfaceConfig.create(SurfaceConfig.ConfigType.JPEG, ConfigSize.VGA);
        assertEquals(expectedSurfaceConfig, surfaceConfig);
    }

    @Test
    public void transformSurfaceConfigWithJPEGPreviewSize() {
        SurfaceConfig surfaceConfig =
                mSurfaceManager.transformSurfaceConfig(
                        CameraMode.DEFAULT,
                        LEGACY_CAMERA_ID, ImageFormat.JPEG, mPreviewSize, DEFAULT_STREAM_USE_CASE);
        SurfaceConfig expectedSurfaceConfig =
                SurfaceConfig.create(ConfigType.JPEG, ConfigSize.PREVIEW);
        assertEquals(expectedSurfaceConfig, surfaceConfig);
    }

    @Test
    public void transformSurfaceConfigWithJPEGRecordSize() {
        SurfaceConfig surfaceConfig =
                mSurfaceManager.transformSurfaceConfig(
                        CameraMode.DEFAULT,
                        LEGACY_CAMERA_ID, ImageFormat.JPEG, mRecordSize, DEFAULT_STREAM_USE_CASE);
        SurfaceConfig expectedSurfaceConfig =
                SurfaceConfig.create(ConfigType.JPEG, ConfigSize.RECORD);
        assertEquals(expectedSurfaceConfig, surfaceConfig);
    }

    @Test
    public void transformSurfaceConfigWithJPEGMaximumSize() {
        SurfaceConfig surfaceConfig =
                mSurfaceManager.transformSurfaceConfig(
                        CameraMode.DEFAULT,
                        LEGACY_CAMERA_ID, ImageFormat.JPEG, mMaximumSize, DEFAULT_STREAM_USE_CASE);
        SurfaceConfig expectedSurfaceConfig =
                SurfaceConfig.create(ConfigType.JPEG, ConfigSize.MAXIMUM);
        assertEquals(expectedSurfaceConfig, surfaceConfig);
    }

    @Test
    public void onCamerasUpdated_removesCombinationForUnavailableCamera()
            throws CameraUpdateException {
        // Arrange: Initial state contains LEGACY_CAMERA_ID.
        // Verify we can get a config for it.
        SurfaceConfig initialConfig = mSurfaceManager.transformSurfaceConfig(
                CameraMode.DEFAULT,
                LEGACY_CAMERA_ID, ImageFormat.YUV_420_888, mAnalysisSize, DEFAULT_STREAM_USE_CASE);
        assertNotNull(initialConfig);

        // Act: Update the list to no longer include the legacy camera.
        List<String> newCameraIds = Arrays.asList(LIMITED_CAMERA_ID, FULL_CAMERA_ID);
        mSurfaceManager.onCamerasUpdated(newCameraIds);

        // Assert: Querying the removed camera now throws an exception, while others still work.
        assertThrows(IllegalArgumentException.class, () ->
                mSurfaceManager.transformSurfaceConfig(CameraMode.DEFAULT, LEGACY_CAMERA_ID,
                        ImageFormat.YUV_420_888, mAnalysisSize, DEFAULT_STREAM_USE_CASE));

        SurfaceConfig configForRemainingCamera = mSurfaceManager.transformSurfaceConfig(
                CameraMode.DEFAULT,
                LIMITED_CAMERA_ID, ImageFormat.YUV_420_888, mAnalysisSize, DEFAULT_STREAM_USE_CASE);
        assertNotNull(configForRemainingCamera);
    }

    @Test
    public void onCamerasUpdated_addsNewCombination() throws CameraUpdateException {
        // Arrange: Add a new camera to the system that wasn't in the initial set.
        // Verify it doesn't exist in the manager yet.
        String newCameraId = "20";
        assertThrows(IllegalArgumentException.class, () ->
                mSurfaceManager.transformSurfaceConfig(CameraMode.DEFAULT, newCameraId,
                        ImageFormat.YUV_420_888, mAnalysisSize, DEFAULT_STREAM_USE_CASE));
        addCamera(newCameraId, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                null, CameraCharacteristics.LENS_FACING_BACK);

        // Act: Update the surface manager with the new camera ID included.
        List<String> newCameraIds = Arrays.asList(
                LEGACY_CAMERA_ID, LIMITED_CAMERA_ID, FULL_CAMERA_ID, LEVEL3_CAMERA_ID, newCameraId
        );
        mSurfaceManager.onCamerasUpdated(newCameraIds);

        // Assert: We can now get a valid configuration for the newly added camera.
        SurfaceConfig newConfig = mSurfaceManager.transformSurfaceConfig(
                CameraMode.DEFAULT,
                newCameraId, ImageFormat.YUV_420_888, mAnalysisSize, DEFAULT_STREAM_USE_CASE);
        assertNotNull(newConfig);
    }

    @Test
    public void onCamerasUpdated_throwsException_andAbortsTransaction() {
        // Arrange: The manager is initialized with cameras "0", "1", "2", "3".
        // Add a new "bad" camera that will cause an exception during processing.
        String badCameraId = "bad-camera";
        CameraManager cameraManager = (CameraManager) ApplicationProvider.getApplicationContext()
                .getSystemService(Context.CAMERA_SERVICE);

        // This will cause an exception
        CameraCharacteristics badCharacteristic =
                ShadowCameraCharacteristics.newCameraCharacteristics();
        ((ShadowCameraManager) Shadow.extract(cameraManager))
                .addCamera(badCameraId, badCharacteristic);

        // Act & Assert: Attempt to update to a list containing the good and bad new cameras.
        // This should throw a CameraUpdateException.
        List<String> newCameraIds = Collections.singletonList(badCameraId);
        try {
            mSurfaceManager.onCamerasUpdated(newCameraIds);
            // If this line is reached, the test should fail.
            fail("Expected CameraUpdateException was not thrown.");
        } catch (Exception e) {
            // The correct exception was thrown. Test passes.
            // We can also assert that the cause is what we expect.
            assertThat(e).isInstanceOf(CameraUpdateException.class);
        }

        // Assert: The state should not have changed. The transaction must be fully aborted.
        // 1. The 'bad' new camera should NOT have been added.
        assertThrows(IllegalArgumentException.class, () ->
                mSurfaceManager.transformSurfaceConfig(CameraMode.DEFAULT, badCameraId,
                        ImageFormat.YUV_420_888, mAnalysisSize, DEFAULT_STREAM_USE_CASE));

        // 2. An original camera should still exist.
        assertNotNull(mSurfaceManager.transformSurfaceConfig(CameraMode.DEFAULT, LIMITED_CAMERA_ID,
                ImageFormat.YUV_420_888, mAnalysisSize, DEFAULT_STREAM_USE_CASE));
    }

    private void setupCamera() {
        mCameraFactory = new FakeCameraFactory();

        addCamera(
                LEGACY_CAMERA_ID, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY, null,
                CameraCharacteristics.LENS_FACING_FRONT);

        addCamera(
                LIMITED_CAMERA_ID, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                null,
                CameraCharacteristics.LENS_FACING_BACK);

        addCamera(
                FULL_CAMERA_ID, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL, null,
                CameraCharacteristics.LENS_FACING_BACK);
        addCamera(
                LEVEL3_CAMERA_ID, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3, null,
                CameraCharacteristics.LENS_FACING_BACK);
        initCameraX();
    }

    private void addCamera(String cameraId, int hardwareLevel, int[] capabilities, int lensFacing) {
        CameraCharacteristics characteristics =
                ShadowCameraCharacteristics.newCameraCharacteristics();

        ShadowCameraCharacteristics shadowCharacteristics = Shadow.extract(characteristics);

        shadowCharacteristics.set(
                CameraCharacteristics.LENS_FACING, lensFacing);

        shadowCharacteristics.set(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL, hardwareLevel);

        shadowCharacteristics.set(
                CameraCharacteristics.SENSOR_ORIENTATION, DEFAULT_SENSOR_ORIENTATION);

        if (capabilities != null) {
            shadowCharacteristics.set(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES, capabilities);
        }

        CameraManager cameraManager = (CameraManager) ApplicationProvider.getApplicationContext()
                .getSystemService(Context.CAMERA_SERVICE);

        ((ShadowCameraManager) Shadow.extract(cameraManager))
                .addCamera(cameraId, characteristics);

        CameraManagerCompat cameraManagerCompat =
                CameraManagerCompat.from((Context) ApplicationProvider.getApplicationContext());
        StreamConfigurationMap mockMap = mock(StreamConfigurationMap.class);
        when(mockMap.getOutputSizes(anyInt())).thenReturn(mSupportedSizes);
        // ImageFormat.PRIVATE was supported since API level 23. Before that, the supported
        // output sizes need to be retrieved via SurfaceTexture.class.
        when(mockMap.getOutputSizes(SurfaceTexture.class)).thenReturn(mSupportedSizes);
        shadowCharacteristics.set(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP, mockMap);

        @CameraSelector.LensFacing int lensFacingEnum = CameraUtil.getLensFacingEnumFromInt(
                lensFacing);
        mCameraFactory.insertCamera(lensFacingEnum, cameraId, () -> new FakeCamera(cameraId, null,
                new Camera2CameraInfoImpl(cameraId, cameraManagerCompat)));
    }

    private void initCameraX() {
        CameraXConfig cameraXConfig = createFakeAppConfig();
        CameraX cameraX;
        try {
            cameraX = CameraXUtil.getOrCreateInstance(mContext, () -> cameraXConfig).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Unable to initialize CameraX for test.");
        }
        // Executes any pending tasks on the main looper, including LiveData observers.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        mSurfaceManager = cameraX.getCameraDeviceSurfaceManager();
    }

    private CameraXConfig createFakeAppConfig() {

        // Create the DeviceSurfaceManager for Camera2
        CameraDeviceSurfaceManager.Provider surfaceManagerProvider =
                (context, cameraManager, availableCameraIds) -> {
                    try {
                        return new Camera2DeviceSurfaceManager(context,
                                mMockCamcorderProfileHelper,
                                (CameraManagerCompat) cameraManager, availableCameraIds);
                    } catch (CameraUnavailableException e) {
                        throw new InitializationException(e);
                    }
                };

        // Create default configuration factory
        UseCaseConfigFactory.Provider factoryProvider = Camera2UseCaseConfigFactory::new;

        CameraXConfig.Builder appConfigBuilder =
                new CameraXConfig.Builder()
                        .setCameraFactoryProvider(
                                (ignored0, ignored1, ignored2,
                                        ignored3, ignored4, ignored5) -> mCameraFactory)
                        .setDeviceSurfaceManagerProvider(surfaceManagerProvider)
                        .setUseCaseConfigFactoryProvider(factoryProvider);

        return appConfigBuilder.build();
    }
}
