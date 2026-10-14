# Ladrok Launcher

The home screen of [Ladrok TV](https://github.com/Kordal/ladrok-tv): a grid of your TV apps, a clock and Settings. Nothing else.

- **Tiles**: one per TV app, showing its banner. The focused app's name appears below the grid.
- **Move**: long-press OK on a Tile, choose *Move*, use the arrows, then press OK. That is the only setting.
- **Home**: pressing Home on the home screen brings you back to the first Tile.

Terms are defined in [CONTEXT.md](CONTEXT.md), decisions in [docs/adr](docs/adr).

## Build and run

You need the Android SDK and JDK 21 or newer (the one bundled with Android Studio works).

```bash
./gradlew testDebugUnitTest          # the Tile order rules
./gradlew installDebug               # onto a connected Android TV device or emulator
./gradlew connectedDebugAndroidTest  # D-pad UI tests, on a device or emulator
```

On a stock Android TV device, press Home and pick *Ladrok Launcher*.

## Releases

Push a tag such as `v1.0.0`. CI builds `LadrokLauncher.apk`, signs it with the release key (repo secrets `LADROK_KEYSTORE_B64` and `LADROK_KEYSTORE_PASSWORD`) and attaches it with its SHA-256 to a GitHub Release. ladrok-tv downloads a pinned version and builds it into the system image as a prebuilt, so the Launcher updates with the system's over-the-air updates.

## License

Apache License 2.0, see [LICENSE](LICENSE).
