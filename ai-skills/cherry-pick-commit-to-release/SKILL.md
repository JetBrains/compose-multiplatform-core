---
name: cherry-pick-commit-to-release
description: This skill instructs how to cherry-pick a commit to the release branch.
version: 2.0.0
---

# Input

- GitHub pull request URL, number or commit SHA
- release version or release branch name

If any of this data is missing, ask the user to provide it.

# Action

The whole cherry-pick flow is implemented by a deterministic script:
`scripts/cherry-pick-to-release.sh`. Do not perform the git/gh steps manually — always
run the script.

1. Extract from the user's request the two arguments the script needs:
   - **commit / PR**: the commit SHA, PR number, or PR URL (a PR URL is accepted as-is).
   - **release**: the release version (e.g. `1.10`) or a full branch (e.g. `release/1.10`).
2. From the repository root, run:

   ```sh
   ai-skills/cherry-pick-commit-to-release/scripts/cherry-pick-to-release.sh <commit-or-pr> <release>
   ```

3. The script creates the branch, cherry-picks the commit preserving its exact message,
   pushes, opens the PR titled `[Cherry-pick] <Old PR title> (#<Original PR number>)`, and
   automatically assigns the QA reviewer team `JetBrains/compose-multiplatform-qa`
   (do NOT ask the user for a QA reviewer).
4. If the script stops because of merge conflicts, relay its message to the user and ask
   them to resolve the conflicts (the script prints the exact commands to continue).
5. On success, report the pull request URL printed by the script.
