---
name: manage_commits
description: A skill for creating, amending, and formatting commits in the AndroidX support project
---

# Manage Commits Skill

This skill enforces AndroidX-specific rules and conventions when creating or modifying commits in the `support/` project.

## AndroidX Commit Rules

1. **Run ktlint First**: Before creating or amending a commit, you **must always run ktlint** on all modified `.kt` files to prevent build failures, using the exact command:
   `./gradlew :ktCheckFile --format --file <file>`
   Resolve any issues that arise before proceeding.

2. **Describe Change and Rationale**: The commit message must clearly describe what the change does and the rationale behind it.

3. **Test Stanza Required**: ALL commits must have a `Test:` stanza detailing what tests cover the change.
   - If the change does not require tests (e.g., a markdown-only change), you must still include the stanza with a rationale explaining why it doesn't need tests (e.g., `Test: markdown file change only`).

4. **Bug Tracking**: All commits addressing a bug must include the corresponding issue tracker ID.
   - Use the `Bug: <id>` stanza if the commit *partially* fixes the bug.
   - Use the `Fixes: <id>` stanza if the commit *fully* resolves the bug.

5. **Explicit Permission for Uploads**: NEVER upload a CL (Changelist) without explicitly asking the user for permission first.

6. **Preserve Change-Id**: When amending a commit, you **must always preserve the `Change-Id:` line** in the commit message if it already exists.
