# Project: Room

## General Instructions:

When modifying any .kt file, format it via the following command:
`./gradlew :ktCheckFile --format --file <space-separated-list-of-files-to-format>`

## Description of sub-projects:

- room3-common: Contains the annotation APIs from the library.
- room3-runtime: Contains the runtime part of the library.
- room3-compiler: Contains the KSP processor part of the library.
- room3-compiler-processing: Contains the XProcessing library used for KSP processing and code 
  generation.
- room3-migration: Contains the migration serialization structure for reading and storing schemas.
- room3-testing: Contains the testing helper library for validating migrations.
- room3-gradle-plugin: Contains the Room Gradle Plugin used to configure schema directories.

## Documentation links:

- https://developer.android.com/training/data-storage/room
- https://developer.android.com/kotlin/multiplatform/room
- https://developer.android.com/training/data-storage/room/defining-data
- https://developer.android.com/training/data-storage/room/accessing-data
- https://developer.android.com/training/data-storage/room/relationships
- https://developer.android.com/training/data-storage/room/async-queries
- https://developer.android.com/training/data-storage/room/creating-views
- https://developer.android.com/training/data-storage/room/prepopulate
- https://developer.android.com/training/data-storage/room/migrating-db-versions
- https://developer.android.com/training/data-storage/room/testing-db
- https://developer.android.com/training/data-storage/room/referencing-data
- https://developer.android.com/training/data-storage/room/sqlite-room-migration