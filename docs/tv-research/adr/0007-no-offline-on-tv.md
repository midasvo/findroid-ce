# ADR 0007: No downloads or offline mode on TV for now

**Status:** Proposed

## Context

Phone CE has downloads, offline mode, Smart Downloads (#116) and a Downloads screen (#81). TV has none of it. TV boxes usually have little storage (8–32 GB, much of it taken by the system and streaming apps), are always on the home network, and are rarely taken offline. Wholphin doesn't offer downloads either.

## Decision

Don't bring downloads or offline mode to TV for now:

- The TV app hides every download and storage setting (ADR 0008).
- The shared code needs no change: `LocalOfflineMode` stays off on TV, and the download buttons don't appear in the CE screens.
- We reconsider if users ask for it, for example for a TV in a holiday home or a camper. That would be its own issue (size L).

## Consequences

- There's less to build and test on TV.
- A user who switches a box from the phone APK to the TV APK (ADR 0005) keeps their downloads on disk, but the TV app doesn't show them. Mention this in the release notes of the first TV release that people are pointed to.
