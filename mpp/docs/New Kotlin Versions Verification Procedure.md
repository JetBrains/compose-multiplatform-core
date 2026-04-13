# New Kotlin Versions Verification Procedure

Internal procedure for verifying new Kotlin versions for Compose Multiplatform.

## Scope

- Applies to Kotlin Beta1, Beta2, and RC* builds only.
- Repositories in scope (in order):
  1) https://jetbrains.team/p/ui/repositories/compose-teamcity-config
  2) https://github.com/JetBrains/compose-multiplatform-core
  3) https://github.com/JetBrains/compose-multiplatform

## Procedure

### 0) compose-teamcity-config

1. Create a new branch and use the same branch name as the other repositories. TeamCity matches branches by name across repos, so this keeps the verification inputs aligned.
2. Update Kotlin versions for integration tests:
   - https://jetbrains.team/p/ui/repositories/compose-teamcity-config/files/main/.teamcity/compose/IntegrationTestVersions.kt
3. Do not merge this branch to `main`; keep it for the verification cycle.

### 1) compose-multiplatform-core

1. Ensure the branch name matches the one used in `compose-teamcity-config` so TeamCity picks them up together.
2. Bump Kotlin versions in the repo. Use the `update-kotlin-version` skill with a coding agent of your choice:
   - `ai-skills/update-kotlin-version`
3. Publish all libraries to Maven Local:
   ```bash
   ./gradlew :mpp:publishComposeJbToMavenLocal -Pcompose.platforms=all
   ```
4. Fix any build/publication issues until the task succeeds. New Kotlin builds may have bugs; if you hit an issue with no feasible workaround, report it ASAP to Kotlin:
   - https://youtrack.jetbrains.com/projects/KT
   - Notify the release team in Slack `#kotlin-release-activity` about actual and potential blockers that prevent us from adopting the new Kotlin version
   - Follow up on the issues and try dev builds with fixes as soon as they are available
5. Run the demo via IDE run configurations:
   - Desktop
   - Web
   - macOS native
6. Verify CI is green on GitHub Actions for the branch. It is not rare that a new Kotlin version requires test code changes due to new compiler checks.

### 2) compose-multiplatform

1. Ensure the branch name matches the one used in `compose-multiplatform-core` and `compose-teamcity-config`.
2. Update version references using the provided scripts:
   - https://github.com/JetBrains/compose-multiplatform/blob/master/tools/replaceVersionInLibs.sh
   - https://github.com/JetBrains/compose-multiplatform/blob/master/tools/replaceVersionInExamples.sh
3. Verify CI is green on GitHub Actions for the branch.

## Final Verification

After all repositories are updated, run the SNAPSHOT build from the chosen branch name and verify it is published via TeamCity:
- https://teamcity.jetbrains.com/buildConfiguration/JetBrainsPublicProjects_Compose_AllPersonalBuild
