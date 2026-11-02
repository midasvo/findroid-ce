# ADR 0003: One way to restore focus on every screen

**Status:** Proposed

## Context

Focus is lost on every level after Back ([1-walkthrough.md](../1-walkthrough.md), F4):

- **Cause:** every screen calls `requestFocus()` in a `LaunchedEffect(true)`, and Navigation 3 removes screens below the top from composition. So that effect runs again on return and puts focus on the first element.
- **Rows and grids:** they have no `focusRestorer`, so entering one lands on the closest item, not the last one.
- **Reloads:** Home reloads its data on every return, so items can move.

Wholphin solves this with a saved position per screen ([2-wholphin.md](../2-wholphin.md) §1).

## Decision

A small CE helper set in `ce/ui/focus/`, used by every CE screen:

1. **A saved position per screen.** `rememberFocusPosition()` keeps a `(row, column)` (or a single index for one list) in `rememberSaveable`. Navigation 3's `SaveableStateHolder` keeps it per back-stack entry, so it survives leaving the screen and coming back.
2. **Write on focus, read on return.** Each card reports its position in `onFocusChanged`. On the first composition after a return, the screen puts focus back on the saved position. On the very first visit it focuses the default element instead (the first card, or the Play button on a detail page).
3. **Rows and grids remember their own column.** Every `LazyRow`/`LazyVerticalGrid` gets `focusRestorer()` plus a `FocusRequester` for the saved item, so moving between rows also comes back to the last column.
4. **Scroll first, then focus.** If the saved item is off-screen, scroll it into view (`scrollToItem`) before requesting focus, so the request doesn't fail on an item that isn't laid out.
5. **Stable keys.** Positions are saved as item IDs where the list can change (Home after a reload), and as indexes where it can't (a season's episodes). If the saved ID is gone, fall back to the same index, clamped to the list.
6. **No reload that reshuffles.** On return, a screen refreshes in the background and keeps its current list until the new one is in. G13 handles Home separately.

**Rules for code review:**

- No `LaunchedEffect(true) { focusRequester.requestFocus() }` in a CE screen. Use the helper.
- Every focusable element shows focus visibly (rule 3): scale, border or glow, not only a colour change.
- Dialogs return focus to the element that opened them.

## Consequences

- G1 is mainly this helper plus moving each screen onto it (and per ADR 0001, that happens as each screen is rebuilt).
- A Compose UI test per screen can check "open item → Back → same item focused". Add such tests as the screens move over. The module has none today.
- Positions are only kept for the back stack, not across app restarts. That's on purpose.
