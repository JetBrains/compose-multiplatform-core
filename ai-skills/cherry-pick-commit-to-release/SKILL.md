---
name: cherry-pick-commit-to-release
description: This skill instructs how to cherry-pick a commit to the release branch.
version: 1.0.0
---

# Input

- GitHub pull request URL, number or commit SHA
- release version or release branch name
- name of QA from @jetbrains.com team responsible for the given release

If any of this data is missing, ask the user to provide it.

# Action

The release branch name format: 'release/<release_version>'.
Get the latest commit from the release branch.
From this point, create a new branch with the following name format: author.name/cherry-pick-<PR Number or Commit SHA>
Cherry-pick the given commit, or the PR's resulting commit, to the new branch.
In case of any merge conflicts, stop and ask the user to fix conflicts.
Commit changes. Use the EXACT commit message from the original commit.
Push the new branch to the remote repository.
Create a pull request from the new branch using gh tools.
Update the pull request title to the following format: [Cherry-pick] <Old PR title> (#<Original PR number>).
As the pull request reviewer, set the QA responsible for the given release. Ensure the QA belong to the JetBrains team.