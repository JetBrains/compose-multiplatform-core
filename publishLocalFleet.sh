#!/usr/bin/env bash
set -e

export COMPOSE_CUSTOM_VERSION=0.0.0-SNAPSHOT
./gradlew :mpp:publishComposeJbToMavenLocal -Pcompose.platforms=desktop
./gradlew :mpp:fleet:publishMavenPublicationToMavenLocal -Pcompose.platforms=desktop
