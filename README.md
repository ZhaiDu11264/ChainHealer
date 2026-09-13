# Chain Healer

Heals one-sided chain conveyor connections in [Create](https://modrinth.com/mod/create) instead of amputating them - and keeps chain-conveyor package logistics self-healing, self-reporting and ghost-free.

## Why

Create stores a chain conveyor connection on **both** conveyors. When a save/load cycle or an unload-timing race leaves one side missing, vanilla's validation deletes the surviving half:

- Frogports bound to that conveyor then fail their `connections.contains(...)` check and **silently refuse to send packages** - the conveyor appears "unrecognised" even though the port is bound correctly.
- Routing entries time out, in-flight packages loop forever, and the capacity of the whole chain network degrades.
- Re-connecting the chains could never repair it: the connection packet treats "already connected on this side" as a hard failure, so the only workaround was breaking the conveyor block entirely and re-placing it.

Additionally, vanilla leaks **ghost capacity** in two ways:

- `removeInvalidConnections()` removes a dead connection but leaves the packages travelling on it inside `travellingPackages` - they can never tick, never arrive, render at a stale position (or `Vec3.ZERO`), yet permanently occupy `canAcceptMorePackages()`.
- When a travelling package arrives, the now-empty lane entry is never removed. `canAcceptMorePackages()` counts lane entries (not packages), so busy junctions silently lose capacity over time and end up "at capacity" while looking completely empty.

## What it does

- **Heal instead of amputate** - when a conveyor finds its neighbour exists but lacks the reverse record, the missing half is added back on the neighbour instead of deleting the surviving half.
- **Periodic repair** - a repair pass runs on every lazy tick (~2x per second), so corruption from chunk-load races (or a neighbour whose chunk loads later) self-heals within a second.
- **Re-connect always converges** - connecting two conveyors that already hold a one-sided record now ends in a symmetric state, so simply re-connecting the chains repairs everything.
- **Orphaned lane purge** - travelling packages stranded on dead connections are dropped as item entities (nothing is destroyed) and the stale lane is removed; empty lanes are silently reclaimed, so junction nodes keep their full capacity.
- **Dead-letter for looping packages** - packages that circle a conveyor undelivered for more than 3 minutes are dropped as item entities instead of looping forever.
- **Export diagnostics** - when a frogport's export fails, the exact blocker (missing BE / missing connection / speed 0 / far-end full / capacity) is logged with a capacity breakdown (`looping=N, packagesOnLanes=N, lanes=N [empty=N, orphaned=N]`).
- **In-game chat notices** - blocked ports and rescued packages are announced in chat (localized EN / 简体中文) with a sound cue at the affected conveyor, rate-limited per position.
- Legitimate removals (the target block is gone) still work, including proper cleanup of stats and in-flight packages.

## Compatibility

- Minecraft 1.21.1 / NeoForge / Create 6.0.x
- Only touches `ChainConveyorBlockEntity` connection validation / package storage and the connection packet handler; kinetic propagation, routing and frogport logic are untouched.

## Installation

1. Install [Create](https://modrinth.com/mod/create) 6.0.x for NeoForge 1.21.1.
2. Drop the jar from [Releases](https://github.com/ZhaiDu11264/ChainHealer/releases) into your `mods` folder.

## Building

The Create jar (plus its jar-in-jar embedded ponder/flywheel) is expected in `libs/`:

```
libs/create-1.21.1-6.0.10.jar
libs/ponder-neoforge-1.0.82+mc1.21.1.jar
libs/flywheel-neoforge-1.21.1-1.0.6.jar
```

Then build with:

```
./gradlew build
```

## License

MIT
