#!/usr/bin/env bash
# Merges a palantir-java-format release into a branch and opens a pull request for it.
#
# Usage: upstream-sync.sh [upstream-ref]
#   upstream-ref  an upstream tag or branch; defaults to the latest release tag (x.y.z)
#
# Environment:
#   DRY_RUN=1   do everything locally, but push nothing and create no pull request or issue
#   GH_TOKEN    needed (unless DRY_RUN=1) for the gh CLI
#
# Outcomes:
#   - already merged, or a pull request or issue already open for it: nothing happens
#   - merges cleanly: pushes branch upstream-sync/<ref>, opens a pull request into main, starts CI on it
#   - conflicts: opens an issue listing the conflicting files
set -euo pipefail

UPSTREAM_URL=${UPSTREAM_URL:-https://github.com/palantir/palantir-java-format.git}
UPSTREAM_REMOTE=${UPSTREAM_REMOTE:-upstream} # overridable for testing against another repository
BASE_BRANCH=main
DRY_RUN=${DRY_RUN:-0}

run() {
    if [ "$DRY_RUN" = 1 ]; then
        echo "[dry run] $*"
    else
        "$@"
    fi
}

git remote get-url "$UPSTREAM_REMOTE" >/dev/null 2>&1 || git remote add "$UPSTREAM_REMOTE" "$UPSTREAM_URL"
# Upstream tags go into their own namespace so they never mix with Bowerbird's own release tags.
git fetch --quiet --no-tags "$UPSTREAM_REMOTE" \
    "+refs/tags/*:refs/upstream-tags/*" "+refs/heads/*:refs/remotes/upstream/*"

ref=${1:-}
if [ -z "$ref" ]; then
    ref=$(git for-each-ref --format='%(refname:strip=2)' refs/upstream-tags \
        | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1)
fi
if git rev-parse --verify --quiet "refs/upstream-tags/$ref^{commit}" >/dev/null; then
    sha=$(git rev-parse "refs/upstream-tags/$ref^{commit}")
elif git rev-parse --verify --quiet "refs/remotes/upstream/$ref^{commit}" >/dev/null; then
    sha=$(git rev-parse "refs/remotes/upstream/$ref^{commit}")
else
    echo "::error::Unknown upstream tag or branch: $ref"
    exit 1
fi
echo "Upstream $ref is $sha"

git fetch --quiet origin "$BASE_BRANCH"
if git merge-base --is-ancestor "$sha" "origin/$BASE_BRANCH"; then
    echo "Upstream $ref is already merged into $BASE_BRANCH. Nothing to do."
    exit 0
fi

branch="upstream-sync/$ref"
title="Merge upstream palantir-java-format $ref"
conflict_title="Upstream palantir-java-format $ref has merge conflicts"

if [ "$DRY_RUN" != 1 ]; then
    if [ -n "$(gh pr list --head "$branch" --state open --json number --jq '.[].number')" ]; then
        echo "A pull request for $branch is already open. Nothing to do."
        exit 0
    fi
    if [ -n "$(gh issue list --state open --search "\"$conflict_title\" in:title" --json number --jq '.[].number')" ]; then
        echo "An issue about conflicts with $ref is already open. Nothing to do."
        exit 0
    fi
fi

git switch --quiet -C "$branch" "origin/$BASE_BRANCH"
if git merge --no-ff --no-edit -m "$title" "$sha" >/dev/null 2>&1; then
    echo "Merged $ref cleanly into $branch."
    run git push --force-with-lease origin "$branch"
    body=$(cat <<EOF
Merges upstream [palantir-java-format $ref](https://github.com/palantir/palantir-java-format/releases/tag/$ref) into \`$BASE_BRANCH\`.

- Upstream changes: https://github.com/palantir/palantir-java-format/compare/$(git merge-base "$sha" "origin/$BASE_BRANCH")...$sha
- The merge had no conflicts. CI shows whether the Bowerbird rules still work with these changes.

Opened by the upstream sync workflow.
EOF
)
    run gh pr create --base "$BASE_BRANCH" --head "$branch" --title "$title" --body "$body"
    # Pull requests opened with the workflow token do not trigger other workflows, so start CI explicitly.
    run gh workflow run ci.yml --ref "$branch"
else
    conflicts=$(git diff --name-only --diff-filter=U)
    git merge --abort
    echo "Merging $ref conflicts in:"
    echo "$conflicts"
    body=$(cat <<EOF
Merging upstream [palantir-java-format $ref](https://github.com/palantir/palantir-java-format/releases/tag/$ref) into \`$BASE_BRANCH\` has conflicts in:

$(echo "$conflicts" | sed 's/^/- `/; s/$/`/')

To resolve:

\`\`\`bash
git fetch upstream --tags
git switch -c $branch origin/$BASE_BRANCH
git merge $sha
# resolve the conflicts, then commit and push, and open a pull request
\`\`\`

Opened by the upstream sync workflow.
EOF
)
    run gh issue create --title "$conflict_title" --body "$body"
fi
