---
name: ai-bugathon
description: Event-specific overlay for the JetBrains Compose Multiplatform AI Bugathon. Layers the event rules on top of $solve-youtrack-issue, $evidence-first-bug-debugger, and $submit-github-pr, adding the production-code cap, the required PR label, and event process. Use when the user says the work is part of the AI Bugathon.
argument-hint: "No direct arguments; issue URL and overrides are owned by the coordinated skills"
---

# AI Bugathon

Apply these rules on top of `$solve-youtrack-issue`, `$evidence-first-bug-debugger`, and `$submit-github-pr` only when the user says the work is part of the AI Bugathon. These rules tighten the generic skills; they never replace their evidence, authorship, or safety requirements. This is an event overlay: after the Bugathon, remove this skill together with its Event pointers in the generic skills.

## Event rules

- **Production-code cap:** change no more than 500 production lines. Test files and test source sets do not count toward the cap. Enforce it while shaping the fix in `$evidence-first-bug-debugger`, and again during the `$submit-github-pr` pre-flight by counting additions and deletions:

  ```bash
  git diff --numstat origin/jb-main...HEAD
  ```

  If the smallest supported fix would exceed the cap, stop and report the evidence instead of delivering or submitting an oversized fix.

- **PR label:** every Bugathon pull request must carry the `ios-bugathon` label. When `$submit-github-pr` creates the PR, include `--label ios-bugathon` and verify the label in the PR fields afterwards. If the label does not exist in the repository, tell the user and ask whether to create it; never create or change repository labels without explicit approval. Do not submit a Bugathon PR without the label.

- **One issue, one PR:** solve one YouTrack issue per pull request; do not bundle several issues into one PR.

- **Issue list:** pick issues from the tagged Bugathon list at <https://youtrack.jetbrains.com/issues?q=tag:%20ios-bugathon>.

## Invocation

```text
Use $solve-youtrack-issue.
Issue: https://youtrack.jetbrains.com/issue/CMP-1234
Event: AI Bugathon
```
