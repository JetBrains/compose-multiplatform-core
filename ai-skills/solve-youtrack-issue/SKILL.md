---
name: solve-youtrack-issue
description: End-to-end AI Bugathon workflow for JetBrains/compose-multiplatform-core. Coordinates the evidence-first debugger and GitHub PR submission skills, from issue-named jb-main branch setup through verification, human-authored commits, PR labeling, and PR creation. Use when the user explicitly asks to solve a YouTrack issue and create the PR.
argument-hint: "YouTrack issue URL; optional local reproducer path and reviewer overrides"
---

# Solve YouTrack Issue

Run a selected YouTrack issue through the repository's evidence-first debugging workflow and, when the user asks for a complete solution, submit the verified result as a GitHub pull request. Use the configured YouTrack CLI for issue state, assignee, and reproducer downloads. Do not use plugins.

This is a coordination skill. Do not duplicate or weaken the specialized instructions in `$evidence-first-bug-debugger` or `$submit-github-pr`; invoke and follow them in order.

## Inputs

- **YouTrack issue URL or issue ID:** Required. Use the exact issue ID, such as `CMP-1234`.
- **YouTrack CLI:** Required before starting. Verify it in the Prerequisites section, then use it to assign the issue to the current user when unassigned, move the issue to `In progress`, download any reproducer attachment, and later move the issue to `Code review` and post the final comment.
- **Local reproducer project path:** Optional. Pass it to the debugging skill when supplied.
- **Reviewer overrides:** Optional. `$submit-github-pr` has the team's default reviewers; use an override only when the user explicitly supplies one.
- **Scope:** Determine whether the user asked only to investigate or asked to solve the issue and create the PR. If the scope is unclear and PR creation would be an external action, ask before submitting.

## Prerequisites

Check the required CLIs before starting any work. Do not install tools, start browser login flows, or handle credentials on the user's behalf.

### YouTrack CLI

Always required: this skill uses it to read the issue, assign it, change its state, download the reproducer, and post the final comment. Check installation and authentication:

```bash
command -v youtrack
youtrack auth status
```

- **Not installed:** Ask the user to install it from the installation section at <https://github.com/JetBrains/youtrack-cli#install> (on macOS, for example `brew install jetbrains/utils/youtrack`), and then sign in with:

  ```bash
  youtrack auth login
  ```

- **Installed but not authenticated, expired, or revoked:** Ask the user to run `youtrack auth login`, complete the sign-in, and confirm when done.

### GitHub CLI

Required only when the requested scope includes PR creation. Check installation and authentication:

```bash
command -v gh
gh auth status --hostname github.com
```

- **Not installed:** Ask the user to install it (on macOS, for example `brew install gh`) and then sign in with `gh auth login`.
- **Installed but not logged in:** Ask the user to run `gh auth login`, complete the browser sign-in, and confirm when done.

Treat this as an early gate only. `$submit-github-pr` still owns the full GitHub pre-flight, including scopes, repository access, and every later check; do not duplicate or weaken those checks here.

### When a prerequisite is missing

1. Tell the user exactly which check failed and give the exact fix from the sections above.
2. Stop the workflow and ask them to come back once the missing tool is installed and authenticated. Do not proceed with issue changes, reproducer downloads, or PR work.
3. Only if the user explicitly declines to install or configure the missing tool, continue with the work that does not require it:
   - without the YouTrack CLI, ask the user to paste the issue content and provide the reproducer manually; do not assign the issue, change its state, or post comments;
   - without the GitHub CLI, do not create the PR;
   - at the end, list the manual steps the user must do themselves: assign the issue to themselves and move it to `In progress` before the work, and after the fix create the PR, move the issue to `Code review`, and post a comment with the fix summary and PR URL.

## Workflow

1. Validate that a YouTrack issue URL or issue ID is available. If it is missing, ask for it. Do not invent a branch or issue name.
2. Use the configured YouTrack CLI to:
   - determine the current user;
   - assign the issue to that user if it is unassigned. If assigning by login fails, apply the YouTrack command `Assignee me` instead;
   - move the issue to the exact `In progress` state value used by the issue's project, for example:

     ```bash
     youtrack issue command CMP-1234 "State In progress"
     ```

   - download any reproducer attachment to a temporary directory outside the repository, such as `/tmp/CMP-1234-reproducer`;
   - unzip or open the reproducer and inspect it for useful reproduction details before invoking the debugging skill.

   If the YouTrack CLI is unavailable or not authenticated, follow the Prerequisites section: give the exact fix and stop, or continue without external actions only when the user explicitly declines the prerequisite. Do not use plugins or browser automation as a substitute.
3. Invoke `$evidence-first-bug-debugger` with the issue URL, optional reproducer path, and any user-specified reproduction environment. Let that skill own:
   - opening and interpreting the issue;
   - creating the `CMP-1234` branch from current `origin/jb-main`;
   - proving a focused regression test fails;
   - collecting causal diagnostic evidence;
   - making the smallest supported fix with no major public API changes;
   - rerunning and cleaning up temporary diagnostics and unnecessary comments.
4. Stop if the debugging skill reports an access blocker, unavailable reproduction environment, non-reproducing test, or unresolved root cause. Do not proceed to PR submission without verified evidence.
5. Review the debugger’s final report. Require:
   - the environment actually used;
   - the focused regression test;
   - the initial failure;
   - the causal evidence;
   - the implementation change;
   - whether a public API changed, while omitting API wording from the PR when none did;
   - the successful verification after the fix.
6. If the user asked only to investigate, deliver the final report and stop.
7. If the user asked to solve the issue and create the PR, invoke `$submit-github-pr` with the issue URL and any explicit reviewer override. Let that skill own:
   - GitHub CLI, login, scope, and repository-access checks;
   - branch, target, and clean committed-state validation;
   - human author and committer verification, without requiring CLA confirmation;
   - focused, well-formed commits and push, without the YouTrack issue ID in the commit message;
   - filling `.github/PULL_REQUEST_TEMPLATE.md` concisely, without the YouTrack issue ID in the PR title;
   - adding the `ios-bugathon` label;
   - requesting the default reviewers;
   - creating and verifying the pull request.
8. After the PR is created and verified, use the YouTrack CLI to move the issue to the exact `Code review` state value used by the issue's project and post a comment. For the multi-line summary, write it to a temporary file first:

   ```bash
   youtrack issue command CMP-1234 "State Code review"
   youtrack issue comment create CMP-1234 --text-file /tmp/CMP-1234-youtrack-comment.md
   ```

   The comment must include:
   - the original reported behavior;
   - the confirmed root cause;
   - the implementation change and why it is the right boundary;
   - the focused regression test and the related suites that were run;
   - the actual verification result and any meaningful environment difference;
   - the pull request URL.
9. Return a compact end-to-end summary containing the issue, confirmed root cause, focused test and result, implementation summary, verification result, and PR URL. Clearly distinguish confirmed findings from hypotheses or environment-limited results.

Do not skip evidence because a likely fix is obvious. Do not submit a PR for an unverified fix or uncommitted work. Do not submit a major public API change. Do not ask the user to repeatedly run tests or paste logs that the skills can obtain directly.

## Invocation

```text
Use $solve-youtrack-issue.
Issue: https://youtrack.example.com/issue/CMP-1234
Reproducer: /Users/your-name/Downloads/customer-reproducer

# Optional; omit to use the default reviewers:
Reviewers: svastven, ASalavei, mazunin-v-jb
```
