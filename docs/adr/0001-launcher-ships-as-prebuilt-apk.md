# The Launcher ships to ladrok-tv as a prebuilt APK

ladrok-tv's own apps (LadrokSetup, LadrokUpdater) live in-tree in the AOSP device patch and are built by Soong. The Launcher is different: it lives in its own repo, is built with Gradle, and ladrok-tv pulls the APK in with `android_app_import`. A launcher needs no platform APIs or platform signing, and building outside AOSP means a UI change can be tried on an emulator or any Android TV device in minutes, instead of running a full 400 GB AOSP build each time.
