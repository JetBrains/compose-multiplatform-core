#!/usr/bin/env bash
set -e

./gradlew :mpp:publishComposeJbToMavenLocal \
:fleet:lifecycle:lifecycle-all-desktop:publishToMavenLocal \
:fleet:compose:runtime:runtime-all-desktop:publishToMavenLocal \
:fleet:compose:ui:ui-all-desktop:publishToMavenLocal \
-Pcompose.platforms=desktop \
-Pjetbrains.publication.libraries=COMPOSE,SAVEDSTATE,LIFECYCLE

