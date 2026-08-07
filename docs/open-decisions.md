# Open decisions

Design questions raised by the 2026-07-23 codebase audit that were deliberately **not** fixed,
because each one changes visible behaviour and the intended behaviour is a product call rather
than a bug. Everything else the audit found shipped in `v1.1.0-ce.1`.

## data-02 — `OnConflictStrategy.IGNORE` on the download inserts

`insertMovie` / `insertShow` / `insertSeason` / `insertEpisode` all use `OnConflictStrategy.IGNORE`.
Metadata for an already-downloaded item is therefore never refreshed when it is downloaded again.

That may well be intentional — never silently overwrite local rows while a download exists.
Switching to `REPLACE` visibly changes re-download behaviour.

**Decision needed:** should re-downloading refresh metadata (`REPLACE`, or an explicit update path),
or is freeze-on-first-download the intent?

## setup-02 — the active user can delete themselves

The guard preventing this is commented out, with an explicit note: *"Let the user delete the current
active user for now."* A deliberate product choice, but it leaves a dangling `Server.currentUserId`
and an in-memory token that stays valid until process death.

**Decision needed:** restore the guard, or keep allowing it and clean up `currentUserId` plus the
`JellyfinApi` credentials on delete?

## data-04 — `File(path).length()` on every list render

Called per source per render, and silently returns 0 when the file has gone missing.

The real fix is persisting the size at download completion, which needs a schema column plus a write
in `DownloaderImpl` — touching the same tables as data-02. **Proposal:** fold this into the next
schema bump together with the outcome of data-02, rather than fixing it in isolation.
