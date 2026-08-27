# DonutSMP RTP Mapper

A production-oriented, client-side Fabric mod for Minecraft Java Edition **1.21.11** that experimentally maps confirmed `/rtp` destinations on DonutSMP. It cycles through the selected public regions, sends one explicit normal `rtp <region>` command, waits for a real large teleport, waits for the player position to settle, records one region-tagged sample, and only then starts the cooldown for the next request.

The RTP mapper does not automate movement/combat/inventories, spoof coordinates, alter packets, bypass cooldowns, or attempt to evade anti-cheat. Version 1.3.0 also exposes a separate, optional Baritone mining controller for single-player and explicitly permitted private servers. That controller rejects `donutsmp.net` and its subdomains. Follow every server's current rules and only enable mining where you have permission.

## Requirements

- Minecraft Java Edition 1.21.11
- Java 21
- Fabric Loader 0.19.3 or a compatible newer loader
- Fabric API 0.141.6+1.21.11
- Optional: the hardened Baritone API Fabric 1.17.0 integration build for Minecraft 1.21.11

This project uses Gradle 9.2.1, Fabric Loom Remap 1.14.10, Mojang's official 1.21.11 mappings, and no Mixins.

## Build

Clone or extract the project, make Java 21 available, then run:

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The remapped production JAR is generated in `build/libs/` as:

```text
donutsmp-rtp-mapper-1.3.0.jar
```

Unit tests can be run separately with `./gradlew test`. They cover the normal/delayed/failed RTP timelines, stabilization corrections, timeouts, disconnects, connection changes, exact cooldown behavior, duplicate protection, configuration, storage recovery, CSV escaping, statistics, and chart-coordinate transforms.

## Install

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put Fabric API for 1.21.11 in the instance's `mods` folder.
3. Put `donutsmp-rtp-mapper-1.3.0.jar` in the same `mods` folder.
4. Start Minecraft with the Fabric profile.

The JAR is client-only. It is not needed or intended on the server.

## Optional Baritone mining

Baritone is an optional, separately installed mining engine; it is not bundled with this mod. For Minecraft 1.21.11, use this repository's [hardened API artifact](artifacts/baritone-api-fabric-1.17.0-donutsmp.1.jar), built from the [pinned upstream commit `bc3dcde`](https://github.com/cabaletta/baritone/tree/bc3dcde2fdb7568ec3a1aade475f1a8ebe574e09). Its checksum and build audit are in [the Baritone build report](artifacts/BARITONE-BUILD.md). Place the API JAR beside this mod in the separate instance's `mods` folder.

Mining is limited to single-player and explicitly allowlisted private servers. `donutsmp.net` and every DonutSMP subdomain are always blocked, regardless of the allowlist. Each run is bounded by its configured timeout and positive inventory target. Baritone defines that quantity as the absolute matching-drop target already present in inventory, not a count of additional blocks to mine. Press **End** at any time for the emergency stop, which cancels both active mining and RTP automation.

Open **Settings → Mining** to configure targets and permissions. Multiplayer mining defaults to denied because its independent allowlist starts empty. This hardened API build removes Baritone's separate chat command controller, so mining remains reachable through the mapper's guarded programmatic integration rather than Baritone chat commands. A stock Baritone JAR does not provide that guarantee. Mining is never automatically resumed after a disconnect.

RTP mapping and mapper-managed mining use one exclusive automation lease. The mapper also checks Baritone's mine process before each RTP tick and stops mapping if another integration starts mining. The global emergency action intentionally cancels all Baritone path control.

Use a separate Minecraft instance/profile for the Baritone dependency and do not use that profile to join DonutSMP. The runtime gate only sees the connection address Minecraft exposes: it rejects the DonutSMP root hostname and subdomains, but cannot prove where an arbitrary direct IP, DNS alias, or local proxy ultimately leads. It also does not hide an installed mod or make Baritone permitted. DonutSMP's [current terms](https://store.donutsmp.net/terms) prohibit movement modifications, macros/scripts, and unfair-advantage modifications, and its published privacy notice says it performs real-time client-integrity checks.

## Start and stop mapping

1. Join `donutsmp.net` or an allowed DonutSMP subdomain.
2. Press **Right Shift** to open the mapper.
3. Open **Settings → RTP Regions** and choose the regions to sample. All six are enabled by default.
4. Select **Start Mapping**.
5. Keep the client connected. Use **Stop Mapping** at any time.

Selected regions are sampled in balanced round-robin order. Every attempted command advances the rotation, including a timeout or send failure, so a temporarily unavailable region cannot monopolize collection. The current presets issue `/rtp east`, `/rtp west`, `/rtp eu central`, `/rtp eu west`, `/rtp asia`, and `/rtp oceania`; plain `/rtp` is never used because its behavior can be interactive or server-state-dependent.

The default server allowlist is `donutsmp.net` and `*.donutsmp.net`. Single-player, LAN servers, lookalike domains, and other multiplayer servers are rejected. The allowlist is editable in Settings.

The following commands execute entirely on the client and are never sent to the server:

```text
/rtpmapper start
/rtpmapper stop
/rtpmapper status
/rtpmapper mine start
/rtpmapper mine stop
/rtpmapper mine status
/rtpmapper emergency
/rtpmapper clear
/rtpmapper export
```

Both GUI and command clearing require an explicit confirmation before data is deleted.

## Chart controls

- Mouse wheel: zoom around the coordinate under the cursor
- Left-drag: pan
- Reset View: frame the origin and every point in the selected dataset
- Session / All Time: switch the plotted dataset and statistics
- Hover: show sample number, X/Y/Z, radial distance, requested region, dimension, category, and recorded time
- Mouse wheel over Statistics: scroll all 25,000-block radial buckets

The graph uses Minecraft **X** horizontally and **Z** vertically. Minecraft elevation Y is never used as a graph axis. Points use the six region-map colors (with legacy/unknown samples in gray), and the Statistics panel shows requested-region coverage. Grid steps and concentric distance rings are derived from the current zoom. Projection, pixel binning, and spatial hover buckets are cached; at very high density the renderer caps submitted glyphs while preserving every raw sample and one stable hover representative for each occupied screen pixel.

## Teleport detector

The automation is a single-threaded state machine driven once at the end of each client tick:

```text
IDLE
  -> WAITING_TO_SEND
  -> WAITING_FOR_TELEPORT
  -> WAITING_FOR_STABILIZATION
  -> RECORDING
  -> COOLDOWN
  -> WAITING_TO_SEND
```

Immediately before sending `rtp <region>`, the controller stores the current position, dimension, requested region, request number, timeout, and a snapshot of all attempt-related settings. It changes to `WAITING_FOR_TELEPORT` before invoking Minecraft's normal command-sending method, which prevents a send failure or re-entrant callback from sending twice. Editing the selection while an attempt is in flight cannot relabel that attempt.

A teleport candidate is detected when either the dimension changes or horizontal displacement from the stored baseline reaches the configured threshold (512 blocks by default). The candidate must remain within a small three-dimensional tolerance for several ticks and satisfy the configured minimum stabilization time (0.75 seconds by default). If it returns near the baseline, it is rejected. A separate stabilization deadline prevents movement from hanging the mapper forever.

Only the final observed client position is recorded. Every pending request has a one-way recorded guard, so one command can produce at most one sample. Minor corrections during cooldown are ignored. If no teleport arrives within 20 seconds, or stabilization never completes, the attempt is counted as failed and enters the normal cooldown before retrying.

Optional coordinate guards can stop an active run after a settled Overworld RTP lands near the origin or near the DonutSMP world border. The triggering landing is recorded and queued for saving first. The center check uses radial distance from `(0, 0)`. The border check uses the actual square border at `X/Z = +/-225,000`, so it measures the nearest square edge rather than drawing an incorrect 225,000-block circle. Nether samples do not activate either guard.

The five-second default interval begins when a sample is recorded—or when a failed attempt is safely closed—not when the command is sent. Server lag therefore cannot cause overlapping `/rtp` commands.

Coordinate polling can correlate the next qualifying settled teleport with the one pending command, but a client-only mod cannot cryptographically prove why a server moved the player. An unrelated large server teleport while an RTP is pending can satisfy the detector; stop mapping before using other teleport commands.

## Disconnect behavior

Any null player/world/connection, server rejection, disconnect, or connection-object change immediately clears the pending request and returns the controller to `IDLE`. With the default **Auto Resume: OFF**, reconnecting never sends a command until the user starts mapping again.

If Auto Resume is enabled, reconnecting to an allowed server still creates a fresh controller request and waits the configured interval before it may send. Old pending timers are never carried across the connection.

## Settings

| Setting | Default | Valid range / behavior |
| --- | ---: | --- |
| RTP interval | 5.0 seconds | 1-60 seconds |
| Teleport threshold | 512 blocks | 32-60,000,000 blocks |
| Teleport timeout | 20 seconds | 5-300 seconds |
| Stabilization time | 0.75 seconds | 0.25-5 seconds |
| Show HUD | On | Minimal running status overlay |
| Auto Resume | Off | Delayed, allowlisted reconnect only |
| Store Y coordinate | On | Off stores a missing value, never a fabricated Y |
| Show Grid | On | Chart grid and coordinate labels |
| Show Distance Rings | On | Dynamic origin-centered rings |
| Point size | 2.5 pixels | 1-8 pixels |
| RTP regions | All six public regions | Nonempty multi-selection, balanced round-robin |
| Allowed servers | DonutSMP root + subdomains | Comma-separated exact/wildcard hosts |
| Stop near center | Off; 50,000-block radius | Optional Overworld stop at or inside a 0-318,198-block radial threshold |
| Stop near world border | Off; 10,000-block margin | Optional Overworld stop within 0-225,000 blocks of the square +/-225,000 border |
| Allow single-player mining | On | Applies only to integrated single-player worlds |
| Mining server allowlist | Empty | Separate exact/wildcard private-server list; DonutSMP cannot be enabled |
| Mining targets | Diamond ore variants | 1-32 namespaced block IDs |
| Mining inventory target | 64 | Absolute matching-drop inventory target, 1-2,304 |
| Mining timeout | 10 minutes | 1-120 minutes |

Changes apply to the next RTP request; an in-flight request keeps the settings captured when its command was sent. Both coordinate-stop toggles default to Off, including when upgrading an existing installation.

## Data storage

Persistent files are under the Minecraft instance's Fabric config directory:

```text
.minecraft/config/rtpmapper/config.json
.minecraft/config/rtpmapper/rtp_samples.json
.minecraft/config/rtpmapper/rtp_samples.csv
```

User-requested exports are written to:

```text
.minecraft/rtpmapper/exports/rtp_data_YYYY-MM-DD_HH-mm-ss.csv
```

Each sample stores its sequential sample number, full-precision X/Y/Z, dimension identifier, epoch-millisecond timestamp, requested RTP region, and extensible category. “Requested” is intentional: a client-side mapper knows which command target it issued but cannot independently verify the server's physical backend. JSON is authoritative and CSV is maintained as a convenient mirror. CSV exports use:

```csv
sample,x,y,z,distance_from_origin,dimension,timestamp,requested_region
```

The sample JSON schema is version 2. Existing schema-1 samples load safely as `unknown`; the mapper never guesses historical regions from coordinates because server region boundaries can change.

The session list is memory-only and resets on game startup or a fresh manual mapping start. All-time samples remain until confirmed clearing.

## Save and recovery behavior

Recording copies immutable dataset snapshots to dedicated background persistence and export workers. Repeated save requests are coalesced so render ticks never perform file IO and workers cannot observe mutable Minecraft state. A failed CSV-mirror update is repaired without rewriting authoritative JSON, with exponential retry capped at five minutes. Mapping stop queues a save. Confirmed clearing waits for an ordered durable empty save before reporting success, and shutdown waits up to ten seconds on an exact final-save barrier.

Files are written to a sibling temporary file, flushed, and replaced. A one-generation recovery journal is retained across the vulnerable replacement window. Canonical JSON failures stop mapping and retry safely; a locked derived CSV mirror does not discard a successful JSON generation and is retried separately. If an existing dataset cannot be loaded, mapping fails closed rather than overwriting it with an empty dataset. Config corruption is moved to a timestamped `.corrupt-*` backup before validated defaults are created.

## Statistics

Statistics are cached by dataset revision and include total samples, average X/Z, X/Z bounds, average/minimum/maximum radius, requested-region/category/dimension counts, 25,000-block radial buckets, and Minecraft-oriented quadrants (north is negative Z): NE, NW, SE, and SW.

## Project layout

```text
automation/  deterministic RTP state machine and teleport detector
mining/      optional Baritone adapter, server policy, and bounded lifecycle
config/      validated settings, atomic config manager, server matcher
data/        samples, session/all-time dataset, atomic storage, CSV export
gui/         main/settings screens, cached chart renderer, statistics panel
hud/         modern layered Fabric HUD element
command/     client-only Brigadier command tree
region/      fixed DonutSMP region metadata and fair round-robin cycle
util/        coordinate transform and cached statistics calculator
```

All Minecraft player/world/network operations run on the client thread. Only immutable data snapshots cross to the persistence and export workers.
