# Chain Healer

Heals one-sided chain conveyor connections in [Create](https://modrinth.com/mod/create) instead of amputating them.

## Why

Create stores a chain conveyor connection on **both** conveyors. When a save/load cycle or an unload-timing race leaves one side missing, vanilla's validation deletes the surviving half:

- Frogports bound to that conveyor then fail their `connections.contains(...)` check and **silently refuse to send packages** - the conveyor appears "unrecognised" even though the port is bound correctly.
- Routing entries time out, in-flight packages loop forever, and the capacity of the whole chain network degrades.
- Re-connecting the chains could never repair it: the connection packet treats "already connected on this side" as a hard failure, so the only workaround was breaking the conveyor block entirely and re-placing it.

## What it does

- **Heal instead of amputate** - when a conveyor finds its neighbour exists but lacks the reverse record, the missing half is added back on the neighbour instead of deleting the surviving half.
- **Periodic repair** - a repair pass runs on every lazy tick (~2x per second), so corruption from chunk-load races (or a neighbour whose chunk loads later) self-heals within a second.
- **Re-connect always converges** - connecting two conveyors that already hold a one-sided record now ends in a symmetric state, so simply re-connecting the chains repairs everything.
- Legitimate removals (the target block is gone) still work, including proper cleanup of stats and in-flight packages.

## Compatibility

- Minecraft 1.21.1 / NeoForge / Create 6.0.x
- Only touches `ChainConveyorBlockEntity` connection validation and the connection packet handler; kinetic propagation, routing and frogport logic are untouched.

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
