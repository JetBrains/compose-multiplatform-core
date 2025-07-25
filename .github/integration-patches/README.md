# Integration Patches

This directory contains patch files needed to run workflows which test using the latest version of a
build dependency.

If a dependency makes a breaking change requiring a fix in AndroidX, but the fix can't be applied
until the dependency version is actually updated, adding the patch here will allow the integration
workflow to run successfully.

After the dependency version is updated for AndroidX, the patch file should be deleted.

Patches can be generated with `git diff > patch-file-name.patch`.

| Workflow                    | Patch File Name              |
|-----------------------------|------------------------------|
| Gradle Nightly Test         | gradle-nightly.patch         |
| Gradle Release Nightly Test | gradle-release-nightly.patch |
| KGP Nightly Test            | kgp-nightly.patch            |
