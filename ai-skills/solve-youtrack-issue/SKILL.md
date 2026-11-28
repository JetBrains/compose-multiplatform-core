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
- **YouTrack CLI:** Required before starting. Use it to assign the issue to the current user when unassigned, move the issue to In Progress, download any reproducer attachment, and later move the issue to Code Review.
- **Local reproducer project path:** Optional. Pass it to the debugging skill when supplied.
- **Reviewer overrides:** Optional. `$submit-github-pr` has the team's default reviewers; use an override only when the user explicitly supplies one.
- **Scope:** Determine whether the user asked only to investigate or asked to solve the issue and create the PR. If the scope is unclear and PR creation would be an external action, ask before submitting.

## Workflow

1. Validate that a YouTrack issue URL or issue ID is available. If it is missing, ask for it. Do not invent a branch or issue name.
2. Use the configured YouTrack CLI to:
   - determine the current user;
   - assign the issue to that user if it is unassigned;
   - move the issue to In Progress;
   - download any reproducer attachment to a temporary directory outside the repository, such as `/tmp/CMP-1234-reproducer`;
   - unzip or open the reproducer and inspect it for useful reproduction details before invoking the debugging skill.
   If the YouTrack CLI is unavailable or not authenticated, tell the user exactly what to install or configure and stop. Do not use plugins or browser automation as a substitute.
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
   - focused, well-formed commits and push;
   - filling `.github/PULL_REQUEST_TEMPLATE.md` concisely;
   - adding the `ios-bugathon` label;
   - requesting the default reviewers;
   - creating and verifying the pull request.
8. After the PR is created and verified, use the YouTrack CLI to move the issue to Code Review.
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
