#!/usr/bin/env bash
#
# Cherry-pick a merged commit (or PR) onto a release branch and open a PR.
#
# Usage:
#   cherry-pick-to-release.sh <PR-number | PR-URL | commit-SHA> <release-version | release-branch>
#
# Examples:
#   cherry-pick-to-release.sh 3176 1.10
#   cherry-pick-to-release.sh https://github.com/JetBrains/compose-multiplatform-core/pull/3176 release/1.10
#   cherry-pick-to-release.sh 12f315236f7 1.10
#
# The script is deterministic: it derives everything from the given commit/PR and the
# release branch. The QA reviewer team is always assigned automatically.

set -euo pipefail

# --- Config ------------------------------------------------------------------
REMOTE="origin"
MAIN_BRANCH="jb-main"                          # branch searched to resolve a PR number -> commit
QA_TEAM="JetBrains/compose-multiplatform-qa"   # GitHub team assigned as reviewer (org/team-slug)

# --- Helpers -----------------------------------------------------------------
die() { echo "ERROR: $*" >&2; exit 1; }

usage() {
  cat >&2 <<'EOF'
Usage: cherry-pick-to-release.sh <PR-number | PR-URL | commit-SHA> <release-version | release-branch>

Arguments:
  1) PR number, PR URL, or commit SHA to cherry-pick
  2) Release version (e.g. 1.10) or full release branch (e.g. release/1.10)
EOF
  exit 2
}

# --- Validate arguments & environment ---------------------------------------
[ "$#" -eq 2 ] || usage
COMMITTISH="$1"
RELEASE_ARG="$2"

command -v git >/dev/null 2>&1 || die "'git' is not installed."
command -v gh  >/dev/null 2>&1 || die "'gh' (GitHub CLI) is not installed."
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Not inside a git repository."

# --- Resolve the release branch ----------------------------------------------
case "$RELEASE_ARG" in
  release/*) RELEASE_BRANCH="$RELEASE_ARG" ;;
  *)         RELEASE_BRANCH="release/$RELEASE_ARG" ;;
esac

echo "Fetching '$REMOTE'..."
git fetch --quiet "$REMOTE"

git rev-parse --verify --quiet "refs/remotes/$REMOTE/$RELEASE_BRANCH" >/dev/null \
  || die "Release branch '$REMOTE/$RELEASE_BRANCH' not found. Check the release version/branch."

# --- Resolve the commit to cherry-pick, its PR number and subject ------------
# Reduce a PR URL (.../pull/<N>) to its number.
case "$COMMITTISH" in
  *://*/pull/*) COMMITTISH="${COMMITTISH##*/pull/}"; COMMITTISH="${COMMITTISH%%[!0-9]*}" ;;
esac

PR_NUMBER=""
if [[ "$COMMITTISH" =~ ^[0-9]+$ ]]; then
  # PR number -> squash commit on the main branch (subject ends with "(#<N>)").
  PR_NUMBER="$COMMITTISH"
  COMMIT="$(git log "$REMOTE/$MAIN_BRANCH" --grep="(#$PR_NUMBER)\$" --format=%H -1 || true)"
  [ -n "$COMMIT" ] || die "Could not find a commit for PR #$PR_NUMBER on $REMOTE/$MAIN_BRANCH. Pass the commit SHA explicitly."
else
  # Commit SHA.
  COMMIT="$(git rev-parse --verify --quiet "${COMMITTISH}^{commit}" || true)"
  [ -n "$COMMIT" ] || die "Commit '$COMMITTISH' not found."
  # Extract the PR number from the trailing "(#N)" of the subject, if present.
  SUBJECT_TMP="$(git log -1 --format=%s "$COMMIT")"
  if [[ "$SUBJECT_TMP" =~ \(#([0-9]+)\)$ ]]; then
    PR_NUMBER="${BASH_REMATCH[1]}"
  fi
fi

SUBJECT="$(git log -1 --format=%s "$COMMIT")"

# --- Compute the new branch name --------------------------------------------
EMAIL="$(git config user.email || true)"
[ -n "$EMAIL" ] || die "git user.email is not configured."
EMAIL_LOCAL="${EMAIL%%@*}"

if [ -n "$PR_NUMBER" ]; then
  BRANCH_ID="$PR_NUMBER"
else
  BRANCH_ID="$(git rev-parse --short "$COMMIT")"
fi
BRANCH="$EMAIL_LOCAL/cherry-pick-$BRANCH_ID"

git rev-parse --verify --quiet "refs/heads/$BRANCH" >/dev/null \
  && die "Local branch '$BRANCH' already exists. Delete it or resolve the previous cherry-pick first."

# --- Create the branch and cherry-pick --------------------------------------
echo "Creating branch '$BRANCH' from '$REMOTE/$RELEASE_BRANCH'..."
git checkout -b "$BRANCH" "$REMOTE/$RELEASE_BRANCH"

echo "Cherry-picking $COMMIT ..."
if ! git cherry-pick "$COMMIT"; then
  cat >&2 <<EOF

ERROR: Cherry-pick of $COMMIT hit merge conflicts.
The cherry-pick is left in progress on branch '$BRANCH'.
Resolve the conflicts, then run:

  git cherry-pick --continue
  git push -u $REMOTE $BRANCH
  gh pr create --base "$RELEASE_BRANCH" --head "$BRANCH" \\
    --title "[Cherry-pick] $SUBJECT" --reviewer "$QA_TEAM"

Or abort with: git cherry-pick --abort
EOF
  exit 1
fi

# --- Push and open the PR ----------------------------------------------------
echo "Pushing '$BRANCH' to '$REMOTE'..."
git push -u "$REMOTE" "$BRANCH"

TITLE="[Cherry-pick] $SUBJECT"
if [ -n "$PR_NUMBER" ]; then
  BODY="Cherry-pick of #$PR_NUMBER onto \`$RELEASE_BRANCH\`."$'\n\n'"$(git log -1 --format=%b "$COMMIT")"
else
  BODY="Cherry-pick of commit \`$COMMIT\` onto \`$RELEASE_BRANCH\`."$'\n\n'"$(git log -1 --format=%b "$COMMIT")"
fi

echo "Creating pull request..."
if ! PR_URL="$(gh pr create --base "$RELEASE_BRANCH" --head "$BRANCH" \
      --title "$TITLE" --body "$BODY" --reviewer "$QA_TEAM")"; then
  echo "WARNING: PR creation with reviewer failed; retrying without reviewer..." >&2
  PR_URL="$(gh pr create --base "$RELEASE_BRANCH" --head "$BRANCH" --title "$TITLE" --body "$BODY")"
  gh pr edit "$PR_URL" --add-reviewer "$QA_TEAM" \
    || echo "WARNING: could not assign reviewer '$QA_TEAM'. Assign it manually." >&2
fi

echo
echo "Done. Pull request created:"
echo "$PR_URL"
