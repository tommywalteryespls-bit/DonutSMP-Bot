# Hardened Baritone API build

`baritone-api-fabric-1.17.0-donutsmp.1.jar` is a Fabric API/integration build for Minecraft 1.21.11. It was built from the official Baritone `1.21.11` source at commit [`bc3dcde2fdb7568ec3a1aade475f1a8ebe574e09`](https://github.com/cabaletta/baritone/commit/bc3dcde2fdb7568ec3a1aade475f1a8ebe574e09), with local integration and build-integrity hardening.

## Artifact

- Size: 4,809,914 bytes
- SHA-256: `32e4b7300c018185e6de94d88d0fc8885f2f40afa14f19c1d1ee141f2721dd2e`
- Target: Fabric / Minecraft 1.21.11 / Java 21
- Version: `1.17.0-donutsmp.1`

## Build-integrity changes

- Pinned the official Gradle 8.14.4 distribution SHA-256.
- Removed both `mavenLocal()` repositories.
- Replaced the mutable Unimined plugin marker with timestamped module `1.4.2-20260814.024022-28` (`e7b3d2a`), and rejected changing or dynamic dependency resolution.
- Generated strict Gradle SHA-256 verification metadata for 243 components and 428 artifacts.
- Pinned and verified the ProGuard 7.4.2 ZIP before every use.
- Corrected the upstream unquoted ProGuard mapping path on Windows.
- Restricted the source build to Fabric.

## Runtime changes

- Removed registration of Baritone's independent chat command controller. Programmatic provider, mining, and pathing APIs remain available.
- Converted server-controlled dimension identifiers into bounded SHA-256-based cache directory names.
- Replaced unlimited schematic NBT accounting with a 64 MiB quota.

## Verification performed

- Built and optimized entirely offline after dependency resolution.
- Repeated the build and then performed a clean from-scratch offline build; all three API JARs were byte-for-byte identical.
- Verified the mapper-required API signatures, including provider lookup, mining, activity, cancel, and pathing cancellation methods.
- Verified Fabric metadata and the required mixin configuration.
- Confirmed `BaritoneProvider` contains no `ExampleBaritoneControl` or `registerBehavior` reference.
- Confirmed the nested `nether-pathfinder` content matches the pinned 1.6 JAR byte-for-byte, apart from one generated `fabric.mod.json` metadata entry.

## Important limitations

This is still Baritone: it installs movement/pathing mixins and includes the native/JNI `nether-pathfinder` library for Windows, Linux, and macOS. The native dependency remains a separate trust boundary. The build was compiled and structurally verified, but Minecraft was not launched in this workspace.

Use this only in a separate Fabric 1.21.11 profile for single-player or a private server that explicitly permits automation. Do not use that profile to join DonutSMP.
