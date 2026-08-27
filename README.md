# DonutSMP RTP Mapper

This mod maps coordinates in all of DonutSMP's regions. You can configure a guard from the border and spawn, to adjust when you want the bot to stop teleporting. 

The RTP mapper does not automate movement/combat/inventories, spoof coordinates, alter packets, bypass cooldowns, or attempt to evade anti-cheat. Version 1.3.0 also exposes a separate, optional Baritone mining controller for single-player and explicitly permitted private servers. 

Follow every server's current rules and only enable mining where you have permission.

## Requirements

- Minecraft Java Edition 1.21.11
- Java 21
- Fabric Loader 0.19.3 or a compatible newer loader
- Fabric API 0.141.6+1.21.11
- Optional: the hardened Baritone API Fabric 1.17.0 integration build for Minecraft 1.21.11

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

## Install

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put Fabric API for 1.21.11 in the instance's `mods` folder.
3. Put `donutsmp-rtp-mapper-1.3.0.jar` in the same `mods` folder.
4. Start Minecraft with the Fabric profile.

The JAR is client-only. It is not needed or intended on the server.

## Optional Baritone mining

Open **Settings → Mining** to configure targets and permissions This hardened API build removes Baritone's separate chat command controller, so mining remains reachable through the mapper's guarded programmatic integration rather than Baritone chat commands. A stock Baritone JAR does not provide that guarantee. Mining is never automatically resumed after a disconnect.

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

*This project was completely made with Ai, specifically GPT SOL 5.6 I do not claim credit for any of the intellectual property in this build. Please try to improve this if you can.
