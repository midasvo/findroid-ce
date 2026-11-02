# ADR 0002: Navigation with a side drawer

**Status:** Accepted (2026-11-02, in #121)

## Context

TV now uses a top `TabRow` (Search, Home, Libraries) with a profile button that opens Settings ([1-walkthrough.md](../1-walkthrough.md), F10 and F11):

- tabs switch only on OK;
- the Libraries screen takes focus away from the tab row when it loads;
- libraries are one level deeper than needed (tab → grid of libraries → library);
- Back on Home exits the app right away.

Wholphin uses a collapsed icon rail on the left that expands into a drawer, with each library in it by name, and Back on Home opens the drawer ([2-wholphin.md](../2-wholphin.md) §4). Google TV and most streaming apps use the same pattern.

## Options

1. **Keep the top tabs and fix them** (switch on focus, add the libraries as tabs). Few changes, but the row becomes crowded with more than about 4 libraries, and Search, Favorites and Settings still have no natural place.
2. **Side drawer** (`androidx.tv.material3.ModalNavigationDrawer`, available in our `tv-material` 1.1.0).
3. **A hybrid:** a drawer for the sections, top tabs inside a library (Recommended / Library / Genres), like Wholphin.

## Decision

Option 3, built in two steps.

**Step 1 (G9):** a drawer, collapsed to an icon rail by default and expanding when it has focus:

- profile (user + server; OK opens the user switch), Search, Home, Favorites, then each library by name, then Settings at the bottom;
- the libraries come from `MediaViewModel`, in server order;
- the content area changes on OK, not on focus. That keeps us from loading a library just because the user moved past it.

**Back behaviour:**

| Where | Back does |
|---|---|
| Any screen other than a drawer destination | One level up (as now) |
| A drawer destination other than Home | Go to Home |
| Home, drawer closed | Open the drawer with focus on Home |
| Drawer open | Exit the app |

**Step 2 (with G15):** inside a library, tabs for *Library* (grid) and, if wanted, *Genres*. A "Recommended" tab is optional and can wait.

## Consequences

- Replaces `MainScreen` and `MediaScreen` with CE screens (ADR 0001). The "Libraries" grid as its own page disappears.
- Search (G4) and Favorites (G17) get their place in the drawer, so they don't need a tab.
- An app exit always needs two presses of Back from Home, which matches what Android TV users expect.
- With many libraries the drawer has to scroll, and focus restore (ADR 0003) also applies inside the drawer.
