---
name: submit-github-pr
description: Prepare and submit an AI Bugathon pull request for a completed YouTrack issue branch in JetBrains/compose-multiplatform-core. Verifies the gh CLI, GitHub access, jb-main target, clean committed state, human Git authorship, reviewers, PR metadata, and the ios-bugathon label, then fills the repository PR template. Use only when the user explicitly asks to submit a PR.
argument-hint: "YouTrack issue URL; reviewer usernames are optional overrides"
---

# Submit GitHub PR

Turn a completed, verified YouTrack issue branch into a pull request in `JetBrains/compose-multiplatform-core`. Use this after `$evidence-first-bug-debugger` has delivered its final report, or after the user explicitly says that the fix is ready to submit. Do not use plugins; use the GitHub CLI directly.

This skill performs external actions: creating a Git commit when needed, pushing a branch, creating a pull request, and requesting reviewers. Treat an explicit request to submit the PR as authorization for those actions, but first show the user the branch, target, changed files, commit plan, and reviewers. If the user asks only to prepare or check the PR, stop before the first mutation.

Do not include the YouTrack issue ID in the commit subject, commit body, or PR title. Use a clear, human-readable summary such as `Share iOS font family resolver across scenes`.

## Required values

- **YouTrack issue URL or issue ID:** Use the exact uppercase ID, such as `CMP-1234`.
- **Human author identity:** Required before committing. The Git author and committer must use the same human author name and email. Do not require or ask for CLA confirmation; GitHub’s PR checks will report CLA status.
- **Reviewer GitHub usernames:** Optional overrides for the default reviewer list below. Do not guess additional usernames or substitute a team name unless the user supplies it.
- **Verification evidence:** Use the issue title/description and the final report from the debugging skill. Do not invent test results.

Default reviewers, used unless the user explicitly asks for a different list:

- `svastven`
- `ASalavei`
- `mazunin-v-jb`

The target repository and base branch are fixed for this skill:

- Repository: `JetBrains/compose-multiplatform-core`
- Base branch: `jb-main`
- Source branch: the exact issue ID, for example `CMP-1234`
- PR label: `ios-bugathon`

## Pre-flight checks

Run these checks from the repository root before committing, pushing, or creating a PR. Stop at the first failed prerequisite and tell the user exactly what they must do; do not try to install the CLI, log in, grant access, or create a fork on their behalf.

### 1. Verify the repository and branch

Check that `origin` points to the target repository:

```bash
git remote -v
git remote get-url origin
```

If `origin` points elsewhere, ask the user which remote is the target repository. Do not push a branch to a guessed remote.

Confirm the source branch is exactly the issue ID and the working tree is clean:

```bash
git branch --show-current
git status --porcelain=v1
```

Use `git -c core.fsmonitor=false status --porcelain=v1` only if this checkout reports `fsmonitor_ipc__send_query`; do not change global Git configuration. If the working tree is not clean, ask the user whether the changes belong to the issue and should be committed, or whether they should be preserved or removed. Never include unrelated files such as local notes or editor state.

Refresh and verify the target branch:

```bash
git fetch origin jb-main
git rev-parse --verify refs/remotes/origin/jb-main
```

Confirm the issue branch is based on the current target branch:

```bash
git merge-base --is-ancestor origin/jb-main HEAD
```

If this fails, ask the user to authorize a rebase. Do not rebase, reset, or force-push on your own initiative.

Inspect exactly what will be included:

```bash
git diff --stat origin/jb-main...HEAD
git diff --name-status origin/jb-main...HEAD
git diff --check origin/jb-main...HEAD
```

If there is no task-related diff, stop instead of creating an empty PR. If `git diff --check` reports whitespace or conflict-marker problems, fix them before submitting.

Review the public API impact from the changed files and their diffs. Pay particular attention to API baselines such as `api/current.txt`, `*.api`, and `*.klib.api`, as well as public declarations in changed production source. A major public API change is not allowed. If no public API changed, omit API wording from the PR body. If a small public API change exists, require explicit user approval and describe only that specific change in the PR. If a major public API change exists, stop and ask the user instead of submitting it.

Reject debugging-only instrumented test configuration changes. If `compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/Configuration.kt` appears in the working tree or branch diff, restore it before committing. If it appears in an existing commit, stop and ask the user to approve the required history correction; do not submit a PR containing that file.


### 2. Verify that GitHub CLI is installed

Run:

```bash
command -v gh
gh --version
```

If `gh` is missing, tell the user to install it and stop. On macOS, for example, they can run:

```bash
brew install gh
```

Do not install GitHub CLI or otherwise modify the user's machine on your own initiative.

### 3. Verify GitHub login and scopes

Run:

```bash
gh auth status --hostname github.com
```

Never use `--show-token`, print a token, or store credentials in the PR or a temporary file.

Handle the results as follows:

- **Not logged in:** Ask the user to run `gh auth login`, select `GitHub.com`, complete the browser authentication, and confirm to you when they are logged in. Do not start the login flow yourself and do not handle or display the one-time code. Wait for the user’s confirmation before retrying.
- **Wrong account:** Ask the user to switch to the account that has repository access, usually with `gh auth switch`, or to log in with the intended account.
- **Missing or insufficient `repo` scope:** Ask the user to run:

  ```bash
  gh auth refresh --hostname github.com --scopes repo
  ```

- **Expired or invalid authentication:** Ask the user to reauthenticate with `gh auth login` or follow the message from `gh auth status`.

Only proceed after `gh auth status` shows a valid login for `github.com`.

### 4. Verify repository access

Run:

```bash
gh repo view JetBrains/compose-multiplatform-core \
  --json nameWithOwner,viewerPermission,isPrivate
```

- `ADMIN`, `MAINTAIN`, or `WRITE` permission is sufficient for the normal team workflow of pushing `CMP-1234` directly to `JetBrains/compose-multiplatform-core` and opening a PR from that branch.
- `READ` is not sufficient for that workflow. Stop and ask the user to sign in with the correct account, request repository access, or explicitly authorize a separate fork-based flow. Do not silently create or configure a fork.
- No permission or a repository-not-found error means the user cannot access the target repository with the current account. Ask them to correct the GitHub account or request access; do not guess a different repository.

For HTTPS Git remotes, if pushing later fails authentication, ask the user to run:

```bash
gh auth setup-git
```

For SSH remotes, ask the user to ensure their GitHub SSH key is configured; do not replace their remote URL on your own initiative.

### 5. Verify human Git identity and existing PRs

Check the configured Git identity:

```bash
git config --get user.name
git config --get user.email
git var GIT_AUTHOR_IDENT
git var GIT_COMMITTER_IDENT
```

Require all of the following before committing:

- the author and committer names and emails are identical;
- the identity is the confirmed human author, not a generic machine, bot, or AI identity.

If any item is missing or unclear, ask the user for the correct human identity, then stop until it is resolved. Do not commit with an unverified identity. Do not ask about CLA status; the PR check will handle it.

Before creating a PR, check whether one already exists for the branch:

```bash
gh pr list --repo JetBrains/compose-multiplatform-core \
  --head CMP-1234 --state open \
  --json number,title,url
```

If an open PR already exists, report its URL. Do not create a duplicate.

### 6. Verify the AI Bugathon label

Check that the required label exists:

```bash
gh label list --repo JetBrains/compose-multiplatform-core \
  --search ios-bugathon \
  --json name
```

If `ios-bugathon` is missing, tell the user and ask whether to create it. Do not create or change repository labels without explicit approval. With approval, the user action or command is:

```bash
gh label create ios-bugathon \
  --repo JetBrains/compose-multiplatform-core \
  --description "AI Bugathon"
```

Do not submit a PR without the `ios-bugathon` label.

## Commit quality, authorship, and push

Only perform these steps when the user has explicitly asked to submit the PR. All task work must be committed before the PR is created; do not submit uncommitted changes.

1. If the issue branch contains uncommitted task changes, stage the explicit changed files—not unrelated files—and create focused commits:

   ```bash
   git add <changed-files>
   git commit -m "Short fix summary"
   ```

   Use a clear, concise subject for every commit. Add a short body only when the motivation is not obvious from the subject. Do not create debug, cleanup-only, or mixed-purpose commits. Do not include the YouTrack issue ID in the commit subject or body.

2. Verify every commit between the target branch and the issue branch:

   ```bash
   git log --reverse --format='%H%nAuthor: %an <%ae>%nCommitter: %cn <%ce>%nSubject: %s%n%n%b' origin/jb-main..HEAD
   ```

   Every commit must:

   - have the confirmed human as both Git author and Git committer, with identical name and email;
   - contain no `Co-Authored-By`, `Generated with`, or other AI attribution metadata;
   - be focused, readable, and related only to the issue;
   - exclude temporary debug logs, unnecessary comments, and changes to `compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/Configuration.kt`.

   If authorship or committer metadata is wrong, stop and ask the user how to correct it. Do not rewrite an already pushed branch without explicit approval.

3. Confirm the working tree is clean after committing:

   ```bash
   git status --porcelain=v1
   ```

4. Push the source branch to the target repository:

   ```bash
   git push -u origin CMP-1234
   ```

   If the push is rejected because of permissions, remote authentication, or branch protection, report the rejection and the user action from the corresponding pre-flight check. Do not force-push without explicit user approval.

## PR title, description, and reviewers

Use the issue title and actual verification evidence to write a clear, non-speculative PR.

- Title: `Short fix summary`
- Base: `jb-main`
- Head: `CMP-1234`
- Reviewers: use `svastven`, `ASalavei`, and `mazunin-v-jb` by default. Use the user-supplied list only when they explicitly request different reviewers. Use `--reviewer` once per username. Do not use `--assignee` unless the user explicitly wants a PR assignee rather than a reviewer.
- Do not include the YouTrack issue ID in the PR title.

Read the repository PR template and use it as the exact body structure:

```bash
cat .github/PULL_REQUEST_TEMPLATE.md
```

Fill it as follows:

1. Replace the opening instruction with two to four concise sentences that describe the issue, the evidence-supported root cause, and how the focused fix resolves it. Do not paste long logs or turn the PR into an implementation walkthrough.
2. Do not state anything about the API when no public API changed. A major public API change is not allowed; if a small public API change was explicitly approved, describe only that specific change.
3. Add `Fixes [CMP-1234](<exact YouTrack URL>)` when the PR resolves the issue. If it only partially addresses it, describe that limitation instead of using `Fixes`.
4. Under `## Testing`, use only a bullet list of the tests that were added or adapted, with a short description for each. Include the actual verification command, environment, and before/after result in one final bullet.
5. Keep the QA line only when it applies, and state the relevant testing recommendation clearly.
6. Under `## Release Notes`, use `N/A` when the change has no public-facing behavior or API. For a public-facing change, follow `tools/changelog/PR_FORMAT.md` and provide the required section/subsection entry.
7. Remove the entire `## Google CLA` section from the completed PR body.

Do not include credentials, large logs, personal data, or unexplained generated output.

For a private-facing fix, create a concise temporary body file with this shape:

```markdown
<Describe the reported behavior, the confirmed root cause, and how the focused fix resolves it.>

Fixes [CMP-1234](https://youtrack.example.com/issue/CMP-1234)

## Testing
- `TestClass/testMethod` — <short description of the behavior verified>.
- `TestClass/testMethod2` — <short description of the behavior verified>.
- Verified with `<actual command>` on `<actual environment>`: failed before the fix and passed afterward.

This does not require additional QA testing.

## Release Notes
N/A
```

Create the PR with non-interactive arguments and the required label:

```bash
gh pr create \
  --repo JetBrains/compose-multiplatform-core \
  --base jb-main \
  --head CMP-1234 \
  --title "Short fix summary" \
  --body-file /tmp/CMP-1234-pr-description.md \
  --label ios-bugathon \
  --reviewer svastven \
  --reviewer ASalavei \
  --reviewer mazunin-v-jb
```

If a reviewer name is rejected, report the exact GitHub CLI message and ask the user for a valid username; do not silently omit any reviewer.

## Verify the submitted PR

After creation, verify the important PR fields and return the URL to the user:

```bash
gh pr view --repo JetBrains/compose-multiplatform-core \
  --json number,url,title,baseRefName,headRefName,labels,reviewRequests
```

Confirm that:

- the base branch is `jb-main`;
- the head branch is the issue ID;
- the title is concise and does not contain the YouTrack issue ID;
- the `ios-bugathon` label is present;
- all requested reviewers are present.

If a field is wrong and can be corrected non-destructively, use the corresponding `gh pr edit` command and tell the user what was corrected. If it cannot be corrected safely, report the PR URL and the mismatch.

## Address reviewer comments

When the user asks to address new or existing reviewer comments on a PR:

1. Read the PR’s general comments and review comments directly with the GitHub CLI. Do not use plugins.
2. For PR-level comments, use:

   ```bash
   gh pr view 1234 --repo JetBrains/compose-multiplatform-core --comments
   ```

3. For inline review comments, use:

   ```bash
   gh api repos/JetBrains/compose-multiplatform-core/pulls/1234/comments
   ```

4. Read the referenced code and nearby implementation. Make only the changes requested by the reviewer or required by the review evidence.
5. Rerun the focused regression test and the narrowest related suites that cover the changed code.
6. Remove any unnecessary comments and temporary diagnostics.
7. Commit with a clear subject that does not contain the YouTrack issue ID.
8. Push the issue branch.
9. If useful, add a short PR comment summarizing what was addressed. Do not claim a comment was resolved unless the change actually resolves it.

Follow all other rules in this skill: no plugins, human author/committer identity, no major public API changes, no changes to the temporary iOS instrumented test configuration file, and no unrelated cleanup.

## Invocation

```text
Use $submit-github-pr.
Issue: https://youtrack.example.com/issue/CMP-1234

# Optional; omit to use the default reviewers:
Reviewers: svastven, ASalavei, mazunin-v-jb
```
