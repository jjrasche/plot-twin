# CHARTER — earthworks: design the moving of ground

## Where it fits
Today the twin can only READ ground: measured terrain arrives as capture-signed rows. But the
whole point is designing a plot — and designing a plot means moving dirt. Jim asked to dig
deeper here and fill his mental model before any code locks a shape. This ends in a PROPOSAL
he ratifies, not a built feature.

## The questions to dig
- **Regrade as an op.** What are the op slots for "reshape this ground"? A polygon + a target
  surface? A cut depth? A slope constraint? Which of those are authored and which are solved?
- **Cut and fill are solver outputs.** Volumes fall out of proposed-ground minus measured-ground.
  What is the violation type — is "this needs 40 cubic yards trucked in" a violation, a cost, or
  both? The architecture says solvers return violations with location + magnitude + rule; decide
  honestly whether cost fits that shape or needs its own return.
- **Spoil is conserved mass.** Dig a pond, the dirt exists. It becomes a berm somewhere, and the
  optimizer must PLACE it — scored by the same viewshed and watershed rules any entity faces.
  Work out what that placement problem actually is, and what breaks if spoil is allowed to
  vanish.
- **Two grounds.** Proposed-ground vs measured-ground row types: one type with a writer role,
  or two types? Which does a solver read by default? What happens when new capture arrives and
  contradicts a proposal that was already built?
- **Does purpose change the op, or only the rules?** A pond, a terrace, a swale, and a building
  pad are all "move dirt." Argue it either way, then pick.

Ground every claim. Where earthmoving practice has real conventions (cut/fill balance, shrink
and swell factors, side slopes, topsoil stripping), cite a source — do not invent numbers.

## Deliverable
- `research/questions/Q-005-earthworks-design/ANSWER.md` in the repo's existing answer shape:
  the questions, what you found with citations, and a RECOMMENDATION per question with the
  argument for it and the strongest case against.
- A short proposed row/op sketch at the end — types and fields, not code.
- CODE ONLY the uncontroversial part, and only if it is genuinely uncontroversial: row types
  that the recommendation and its alternative both require. If nothing qualifies, ship zero code
  and say so — that is a fine outcome.

## Verify
- Every number and convention carries a primary-source citation in the same line.
- Each question ends in a recommendation, not a menu.
- If you touched code: fresh full gate,
  `bash gradlew clean build --no-daemon --no-build-cache --rerun-tasks`, verbatim output.

## Bands
PASS: all five questions answered with grounded recommendations + the row/op sketch.
WEAK: answered but one or more recommendations rest on unsourced assertion — flag which.
FAIL: a menu of options with no recommendation, or code landed on a contested design.

## Rails
- Isolated worktree, branch `design/earthworks`. Docs-first lane — the schema module is being
  touched by another lane later, so do not edit `:worldstate` without telling the lead first.
- No pushes.
- **Command shape (permission rails — a prompt sleeps the whole run):** no `cd X && …`, no `&&`
  chains, no `git -C`. The cwd persists — `cd` is its own call, then plain single commands.
  Write-verb commands are single-line and non-compound; commit bodies go in a file with
  `git commit -F <file>`. Never hand-curl a POST.
- Report: `git diff --stat`, verdict against the bands, contradictions, open questions.
- The repo's constitution wins over this charter: docs are extremely concise, nothing lives
  outside the documentation taxonomy, and no decision IDs get cited at Jim.
