#!/usr/bin/env bash
# PreToolUse (Bash): stops Claude from skipping the coverage gate in
# .githooks/pre-commit — `git commit --no-verify` / `-n`, or overriding
# core.hooksPath on the command line. Humans can still bypass in their own shell.
input="$(cat)"
# Without jq (or with unparsable input), scan the raw payload rather than failing open.
cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)" || cmd="$input"

# Split into simple commands so `git status && echo -n x` does not match.
while IFS= read -r seg; do
  # `git` as a word, also as /usr/bin/git, (git, "git", \git.
  [[ "$seg" =~ (^|[[:space:]/\(\"\'\\])git([[:space:]\"\'\)]|$) ]] || continue
  # Git config keys are case-insensitive: match the key lowercased.
  lower="${seg,,}"
  if [[ "$lower" =~ core\.hookspath ]] &&
     [[ ! "$lower" =~ config[[:space:]]+core\.hookspath[[:space:]]+\.githooks/?([[:space:]]|$) ]]; then
    reason="overriding core.hooksPath"
  elif [[ "$seg" =~ (^|[[:space:]])commit([[:space:]]|$) ]] &&
       { [[ "$seg" =~ (^|[[:space:]])--no-ver[a-z]*([[:space:]=\"\']|$) ]] ||   # git accepts --no-veri etc.
         [[ "$seg" =~ (^|[[:space:]])-[a-zA-Z]*n[a-zA-Z]*([[:space:]\"\']|$) ]]; }; then
    reason="git commit --no-verify/-n"
  else
    continue
  fi
  echo "Blocked: $reason skips the coverage gate (.githooks/pre-commit). Fix coverage (>= 80%) instead of bypassing the hook; if a bypass is really needed, ask the user to run the commit themselves." >&2
  exit 2
done < <(printf '%s\n' "$cmd" | sed -E 's/(\&\&|\|\||;|\|)/\n/g')
exit 0
