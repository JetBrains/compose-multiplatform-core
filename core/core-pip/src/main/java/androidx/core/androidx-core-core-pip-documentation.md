# Module root

androidx.core core-pip

# Package androidx.core.pip

The PiP Jetpack library addresses several challenges in Android's Picture-in-Picture (PiP) mode:
- OS Fragmentation: The library handles differences in PiP API calls across Android versions, such as enterPictureInPictureMode before Android S and isAutoEnterEnabled after.
- Incorrect PiP Parameters: It provides a unified solution for setting correct PiP parameters, especially for playback, to ensure smooth animations (e.g., source rect hint).
- Unified PiP State Callbacks: The library consolidates onPictureInPictureModeChanged and onPictureInPictureUiStateChanged into a single, unified callback interface for simplified state management.
- Boilerplate Code: It reduces boilerplate by offering predefined RemoteAction sets for common use cases like playback and video calls.

Furthermore, all new PiP features will be delivered through the Jetpack library, ensuring that library adopters can access these features with minimal to no effort.
