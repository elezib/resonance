# Resonance — a free, private music player for Android

Resonance is an original music player built from scratch. It has no ads, no accounts and no tracking, and it doesn't even have permission to use the internet.

## What it does

- **Library:** songs, albums, artists and folders, plus search. You can shuffle all, use "play next", or add songs to the queue.
- **Background playback:** notification, lock-screen and Bluetooth/headset controls.
- **Sound:**
  - A 10-band equalizer with a live response curve.
  - Bass, treble and preamp controls.
  - 14 presets, and you can save your own.
  - ReplayGain volume levelling (per song or per album).
  - Anti-clipping limiter.
  - Gapless playback.
- **Visuals:**
  - Live visualizer in three styles (bars, wave, radial). It doesn't need microphone permission.
  - The app's colours follow the album art.
  - Light, dark and pure-black themes, plus Material You.
- **Lyrics:** synced or plain lyrics, taken from the song file's tags or from a `.lrc` file next to it. You can tap a lyric line to jump to it.
- **Extras:** sleep timer, and a queue view where you can jump to or remove songs.

## Get the app onto your phone (no coding tools needed)

GitHub builds the app for you.

1. On github.com, click **+ → New repository**. Name it `resonance`, then create it.
2. On the new repository page, click **uploading an existing file**. Drag in **everything inside** the `resonance` folder, including the hidden `.github` folder, then click **Commit changes**.
   - If the `.github` folder didn't upload: click **Add file → Create new file**, name it `.github/workflows/build.yml`, paste in the contents of that file, and commit.
3. Open the **Actions** tab. A build named "Build Resonance APK" starts automatically and takes about 5–8 minutes. If GitHub asks, click to enable Actions first.
4. When the build shows a green tick, open it. Under **Artifacts**, download **Resonance-APK**. It's a zip file containing `app-debug.apk`.
5. Copy the APK to your phone and open it. Android will ask you to allow installing from that source. Allow it, then install.

If a build shows a red cross, open it, copy the error text, and send it to Claude to fix.

## Not included yet (possible next steps)

Playlists, home-screen widgets, crossfade, Chromecast, Android Auto browsing and a tag editor.

## Technical notes

- Kotlin, Jetpack Compose (Material 3) and AndroidX Media3 (ExoPlayer). Min Android 8.0, target Android 15.
- The DSP is a custom Media3 `AudioProcessor`, so the EQ works the same on every phone. It doesn't rely on the manufacturer's equalizer.
- Build locally: open the folder in Android Studio and press Run, or use `gradle assembleDebug` with JDK 17.
