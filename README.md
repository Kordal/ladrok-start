# Ladrok Launcher

The home screen of [Ladrok TV](https://github.com/Kordal/ladrok-tv). Two columns, a clock, and the name of whatever has focus. Nothing else.

- **Left: Watch Next and Channels**. Watch Next (what you started and can continue) is the first row when it isn't empty. Below it are the Channels you switched on. Channels are hidden until you turn them on.
- **Right: the Keypad**. One Tile per TV app, in pages of nine, three by three like a phone keypad. Digits 1–9 on the remote open the Tile with that number. Arrow past the right edge for the next page. The Settings Tile is always last.
- **Long-press OK on a Tile**:
  - *Move*: use the arrows, then press OK. This is the only way the order changes.
  - *Channels*: switch that app's Channels on or off.
  - *App info*
- **Back** hops between the rows and the Keypad.
- **Home** on the home screen jumps back to the start.

The repo is called `ladrok-start`. The app, package (`tv.ladrok.launcher`) and APK are *Ladrok Launcher*.

Terms are defined in [CONTEXT.md](CONTEXT.md), decisions in [docs/adr](docs/adr).

## Build and run

You need the Android SDK and JDK 21 or newer (the one bundled with Android Studio works).

```bash
./gradlew testDebugUnitTest          # Tile order and Keypad paging rules
./gradlew installDebug               # onto a connected Android TV device or emulator
./gradlew connectedDebugAndroidTest  # D-pad UI tests, on a device or emulator
```

On a stock Android TV device, press Home and pick *Ladrok Launcher*.

## Releases

Push a tag such as `v1.0.0`. CI builds `LadrokLauncher.apk`, signs it with the release key (repo secrets `LADROK_KEYSTORE_B64` and `LADROK_KEYSTORE_PASSWORD`) and attaches it with its SHA-256 to a GitHub Release. ladrok-tv downloads a pinned version and builds it into the system image as a prebuilt, so the Launcher updates with the system's over-the-air updates. To ship a new release, bump `LAUNCHER_URL` and `LAUNCHER_SHA256` together in ladrok-tv's `scripts/apply-patches.sh`.

On Ladrok TV, `READ_TV_LISTINGS` is granted at first boot. On other devices the Launcher asks for it once.

## License

Apache License 2.0, see [LICENSE](LICENSE).
