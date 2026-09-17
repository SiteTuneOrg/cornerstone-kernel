#!/usr/bin/env bash
# Stop hook: before Claude ends a turn, the uncommitted code must still pass the
# 80% coverage gate (the same check .githooks/pre-commit runs at commit time).
# On failure Claude gets the report back and keeps working (exit 2).
#
# - Skips when nothing under src/, tests/, pom.xml or .mvn/ differs from HEAD.
# - Skips when that exact working tree already passed (hash kept in the git dir),
#   so a turn that touched no code costs one `git diff`, not a test run.
# - Gives up after 3 consecutive blocks in one session, so an unreachable
#   threshold can't loop forever; the pre-commit hook still blocks the commit.
set -uo pipefail
input="$(cat)"
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

paths=(src pom.xml .mvn)
[ -d tests ] && paths+=(tests)

tree_hash="$(
  { git diff HEAD -- "${paths[@]}"
    git ls-files --others --exclude-standard -- "${paths[@]}" | while IFS= read -r f; do echo "$f"; cat "$f"; done
  } | git hash-object --stdin
)"
empty_hash="$(printf '' | git hash-object --stdin)"
[ "$tree_hash" = "$empty_hash" ] && exit 0

state_dir="$(git rev-parse --git-path claude-coverage-gate)"
mkdir -p "$state_dir"
[ "$(cat "$state_dir/passed" 2>/dev/null)" = "$tree_hash" ] && exit 0

session="$(printf '%s' "$input" | jq -r '.session_id // "unknown"')"
attempts_file="$state_dir/attempts-$session"

if [ -f package.json ]; then
  output="$(npx vitest run --coverage --coverage.reporter=text-summary 2>&1)"
else
  output="$(./mvnw -q verify 2>&1)"
fi
status=$?

if [ $status -eq 0 ]; then
  echo "$tree_hash" > "$state_dir/passed"
  rm -f "$attempts_file"
  exit 0
fi

attempts=$(( $(cat "$attempts_file" 2>/dev/null || echo 0) + 1 ))
echo "$attempts" > "$attempts_file"
if [ "$attempts" -gt 3 ]; then
  rm -f "$attempts_file"
  echo "coverage gate still failing after 3 attempts; stopping anyway (pre-commit will still block the commit)" >&2
  exit 0
fi

{
  echo "Coverage gate failed (attempt $attempts/3): tests must pass and line coverage must be >= 80% before finishing."
  echo "Write the missing tests (or fix the failing ones) — do not lower the threshold. Last lines of the run:"
  printf '%s\n' "$output" | grep -vE '^\S+T[0-9:.+-]+ +(INFO|DEBUG)' | tail -40
} >&2
exit 2
