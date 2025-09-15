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

package androidx.xr.scenecore.spatial.rendering;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;

import androidx.xr.runtime.math.FloatSize2d;
import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Quaternion;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.impress.FakeImpressApiImpl;
import androidx.xr.scenecore.impl.impress.ImpressApi;
import androidx.xr.scenecore.internal.CameraViewActivityPose;
import androidx.xr.scenecore.internal.PixelDimensions;
import androidx.xr.scenecore.internal.SurfaceEntity;
import androidx.xr.scenecore.internal.SurfaceEntity.Shape;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.androidxr.splitengine.SubspaceNode;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
public final class SurfaceFeatureImplTest {
    private static final int SUBSPACE_ID = 5;

    private SurfaceFeatureImpl mSurfaceFeature;
    // TODO: Update this test to handle Entity.dispose() for Exceptions when FakeImpress is
    //       updated.
    private ImpressApi mImpressApi;
    private FakeImpressApiImpl mFakeImpressApi = new FakeImpressApiImpl();

    private final ActivityController<Activity> mActivityController =
            Robolectric.buildActivity(Activity.class);
    private final Activity mActivity = mActivityController.create().start().get();

    @Before
    public void setUp() {
        mImpressApi = mock(ImpressApi.class);
        when(mImpressApi.createImpressNode()).thenReturn(mFakeImpressApi.createImpressNode());
        createDefaultSurfaceEntity(new SurfaceEntity.Shape.Quad(new FloatSize2d(1f, 1f)));
    }

    @After
    public void tearDown() {
        if (mSurfaceFeature != null) {
            mSurfaceFeature.dispose();
        }
    }

    private SurfaceFeatureImpl createDefaultSurfaceEntity(Shape shape) {
        XrExtensions xrExtensions = XrExtensionsProvider.getXrExtensions();

        SplitEngineSubspaceManager splitEngineSubspaceManager =
                mock(SplitEngineSubspaceManager.class);

        Assert.assertNotNull(xrExtensions);
        Node node = xrExtensions.createNode();
        SubspaceNode expectedSubspaceNode = new SubspaceNode(SUBSPACE_ID, node);

        when(splitEngineSubspaceManager.createSubspace(anyString(), anyInt()))
                .thenReturn(expectedSubspaceNode);

        int stereoMode = SurfaceEntity.StereoMode.MONO;
        int surfaceProtection = 0;
        int useSuperSampling = 0;

        mSurfaceFeature =
                new SurfaceFeatureImpl(
                        mImpressApi,
                        splitEngineSubspaceManager,
                        xrExtensions,
                        stereoMode,
                        shape,
                        surfaceProtection,
                        useSuperSampling);

        return mSurfaceFeature;
    }

    private CameraViewActivityPose setupDefaultMockCameraView() {
        CameraViewActivityPose cameraView = mock(CameraViewActivityPose.class);
        when(cameraView.getCameraType())
                .thenReturn(CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE);
        when(cameraView.getActivitySpacePose())
                .thenReturn(new Pose(new Vector3(0f, 0f, 0f), Quaternion.Identity));

        CameraViewActivityPose.Fov fov =
                new CameraViewActivityPose.Fov(
                        (float) Math.atan(1.0), (float) Math.atan(1.0),
                        (float) Math.atan(1.0), (float) Math.atan(1.0));
        when(cameraView.getFov()).thenReturn(fov);
        when(cameraView.getDisplayResolutionInPixels()).thenReturn(new PixelDimensions(1000, 1000));
        return cameraView;
    }

    //@Ignore // b/428211243 this test currently leaks android.view.Surface
    @Test
    public void setShape_setsShape() {
        SurfaceEntity.Shape expectedShape = new SurfaceEntity.Shape.Quad(new FloatSize2d(12f, 12f));
        mSurfaceFeature.setShape(expectedShape);
        SurfaceEntity.Shape shape = mSurfaceFeature.getShape();

        assertThat(shape.getClass()).isEqualTo(expectedShape.getClass());
        assertThat(shape.getDimensions()).isEqualTo(expectedShape.getDimensions());
        verify(mImpressApi).setStereoSurfaceEntityCanvasShapeQuad(
                mSurfaceFeature.getEntityImpressNode(), 12f, 12f);

        expectedShape = new SurfaceEntity.Shape.Sphere(11f);
        mSurfaceFeature.setShape(expectedShape);
        shape = mSurfaceFeature.getShape();

        assertThat(shape.getClass()).isEqualTo(expectedShape.getClass());
        assertThat(shape.getDimensions()).isEqualTo(expectedShape.getDimensions());
        verify(mImpressApi).setStereoSurfaceEntityCanvasShapeSphere(
                mSurfaceFeature.getEntityImpressNode(), 11f);

        expectedShape = new SurfaceEntity.Shape.Hemisphere(10f);
        mSurfaceFeature.setShape(expectedShape);
        shape = mSurfaceFeature.getShape();

        assertThat(shape.getClass()).isEqualTo(expectedShape.getClass());
        assertThat(shape.getDimensions()).isEqualTo(expectedShape.getDimensions());
        verify(mImpressApi).setStereoSurfaceEntityCanvasShapeHemisphere(
                mSurfaceFeature.getEntityImpressNode(), 10f);
    }

    //@Ignore // b/428211243 this test currently leaks android.view.Surface
    @Test
    public void setStereoMode_setsStereoMode() {
        int expectedStereoMode = SurfaceEntity.StereoMode.MONO;
        mSurfaceFeature.setStereoMode(expectedStereoMode);
        int stereoMode = mSurfaceFeature.getStereoMode();

        assertThat(stereoMode).isEqualTo(expectedStereoMode);
        verify(mImpressApi).setStereoModeForStereoSurface(
                mSurfaceFeature.getEntityImpressNode(), expectedStereoMode);

        expectedStereoMode = SurfaceEntity.StereoMode.TOP_BOTTOM;
        mSurfaceFeature.setStereoMode(expectedStereoMode);
        stereoMode = mSurfaceFeature.getStereoMode();

        assertThat(stereoMode).isEqualTo(expectedStereoMode);
        verify(mImpressApi).setStereoModeForStereoSurface(
                mSurfaceFeature.getEntityImpressNode(), expectedStereoMode);
    }

    //@Ignore // b/428211243 this test currently leaks android.view.Surface
    @Test
    public void dispose_supports_reentry() {
        Shape.Quad quadShape = new Shape.Quad(new FloatSize2d(1.0f, 1.0f)); // 1m x 1m local
        mSurfaceFeature = createDefaultSurfaceEntity(quadShape);

        // Note that we don't test that dispose prevents manipulating other properties because that
        // is enforced at the API level, rather than the implementation level.
        mSurfaceFeature.dispose();
        mSurfaceFeature.dispose(); // shouldn't crash
    }

    //@Ignore // b/428211243 this test currently leaks android.view.Surface
    @Test
    public void setEdgeFeather_forwardsToImpress() {
        float kFeatherRadiusX = 0.14f;
        float kFeatherRadiusY = 0.28f;
        SurfaceEntity.EdgeFeather expectedFeather =
                new SurfaceEntity.EdgeFeather.RectangleFeather(kFeatherRadiusX, kFeatherRadiusY);
        mSurfaceFeature.setEdgeFeather(expectedFeather);
        SurfaceEntity.EdgeFeather returnedFeather = mSurfaceFeature.getEdgeFeather();

        assertThat(returnedFeather).isEqualTo(expectedFeather);
        verify(mImpressApi).setFeatherRadiusForStereoSurface(
                mSurfaceFeature.getEntityImpressNode(), kFeatherRadiusX, kFeatherRadiusY);

        // Set back to NoFeathering to simulate turning feathering off
        expectedFeather = new SurfaceEntity.EdgeFeather.NoFeathering();
        mSurfaceFeature.setEdgeFeather(expectedFeather);
        returnedFeather = mSurfaceFeature.getEdgeFeather();

        assertThat(returnedFeather).isEqualTo(expectedFeather);
        verify(mImpressApi).setFeatherRadiusForStereoSurface(
                mSurfaceFeature.getEntityImpressNode(), 0f, 0f);
    }
}
