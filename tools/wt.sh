#!/usr/bin/env bash
# Worktree git for subagents, whose cwd resets between shell calls (so `cd` then bare git
# cannot work) while a blanket `git -C` allow rule would let the irreversible tail slip past
# prefix-matched deny rules. This script is the allowlisted shape: it refuses the
# irreversible verbs, then execs git -C.
# Usage: bash tools/wt.sh <worktree-path> <git args...>
set -euo pipefail
worktree="$1"
shift
case " $* " in
  *" push "*|*" push"|*"reset --hard"*|*" clean "*|*" clean"|*"checkout --"*|*"--force"*|*" -f "*)
    echo "wt.sh: refusing irreversible git verb: git $*" >&2
    exit 1
    ;;
esac
exec git -C "$worktree" "$@"
