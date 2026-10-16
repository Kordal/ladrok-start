# Ladrok Launcher

The home screen of [Ladrok TV](https://github.com/Kordal/ladrok-tv). Two columns, a clock, and the name of whatever has focus. Nothing else.

- **Left: Watch Next and Channels**. The last thing you watched comes first, large, with the time left and its App: press Home, then OK, to continue. It works for any App you opened from the Launcher and stayed in for more than a few seconds. It shows what the App put in Watch Next, or else what its player was playing (title, poster, time left; Ladrok TV grants the Launcher MEDIA_CONTENT_CONTROL for this). Failing both, it shows the App's banner, or the Ladrok logo, and opens the App. The rest of Watch Next (what you started and can continue) follows, then the Channels you switched on, each titled with its App. Long-press OK on a Watch Next Program to remove it (it comes back once you watch it again) or to hide all from its App; an App's Watch Next comes back on from its Tile's menu. Channels are hidden until you turn them on. With nothing to continue, the left side shows the Ladrok logo and the time.
- **Right: the Keypad**. One Tile per TV app, in pages of nine, three by three like a phone keypad. Digits 1–9 on the remote open the Tile with that number. Arrow past the right edge for the next page. The Settings Tile is always last.
- **Long-press OK on a Tile**:
  - *Move*: use the arrows, then press OK. This is the only way the order changes.
  - *Channels*: switch that app's Channels on or off.
  - *Updates*: the App's page in [Ladrok Store](https://github.com/Kordal/ladrok-store), where its updates come from.
  - *App info*

  The menu opens beside the Tile.
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

Push a tag such as `v1.0.0`, or run the Release workflow by hand with that version (Actions → Release → Run workflow). CI builds `LadrokLauncher.apk`, signs it with the release key (repo secrets `LADROK_KEYSTORE_B64` and `LADROK_KEYSTORE_PASSWORD`, key alias `ladrok-launcher`, shared with Ladrok Store) and attaches it with its SHA-256 to a GitHub Release.

Nothing to bump in ladrok-tv: its `scripts/fetch-prebuilts.sh` builds the newest release into each system image, and between system updates [Ladrok Store](https://github.com/Kordal/ladrok-store) installs newer releases from its catalog. Both accept only APKs signed with the pinned release certificate; rotating the key means updating `CERT_SHA256` in `fetch-prebuilts.sh` and the Launcher's `signer` in ladrok-store's `catalog/apps.yaml`.

On Ladrok TV, `READ_TV_LISTINGS` is granted at first boot. On other devices the Launcher asks for it once.

## License

Apache License 2.0, see [LICENSE](LICENSE).
