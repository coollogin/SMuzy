<p align="center">
  <img alt="SMuzy" title="SMuzy" src="mockup/smuzi-icon.png" width="250">
</p>

<p align="center">
  <b>Access your music library on all your Android devices</b>
</p>

<div align="center">

<a href="https://github.com/coollogin/SMuzy/releases/latest">
    <img alt="Latest release" src="https://img.shields.io/github/v/release/coollogin/SMuzy?color=FE7638&style=flat">
</a>
<a href="https://www.gnu.org/licenses/gpl-3.0">
    <img src="https://img.shields.io/badge/license-GPL%20v3-2B6DBE.svg?style=flat">
</a>
</div>

**SMuzy** is an open-source, privacy-focused music client for Subsonic, designed and built natively for Android. It connects to your Subsonic-compatible server and lets you browse, stream and download your music library from your phone.

SMuzy does not rely on magic algorithms to decide what you should listen to. The interface is built around your listening history and randomness, and can optionally integrate with services such as ListenBrainz and Last.fm when your backend supports them.

[Changelog](CHANGELOG.md) · [Usage guide](USAGE.md)

## Features

- **Subsonic integration** — Navidrome, Gonic, LMS, Nextcloud Music, Ampache, Airsonic Advanced and other Subsonic-compatible servers.
- **Browse and search** — artists, albums, genres, playlists, decades and folders.
- **Streaming and offline mode** — stream from the server or keep tracks available offline.
- **Playlist management** — create, edit and manage playlists.
- **Gapless playback** and **ReplayGain** volume normalization.
- **Scrobbling** — optional Last.fm or ListenBrainz integration.
- **Internet radio** — search and save radio stations, even without backend support.
- **Transcoding support** — choose a server-side transcoding profile.
- **Multiple libraries**, **equalizer** and a **home-screen widget**.
- **Android Auto and Chromecast** — included in the GitHub build.
- **SMuzy look** — brand-orange Material 3 palette, SMuzy launcher icon and vinyl artwork placeholders.
- **Cover animation and visualizer** — optional vinyl sleeve animation and a spectrum strip under the cover in the full-screen player.
- **USER_TAG cloud** — browse albums by Navidrome user tags in the Library section.
- **Update notifications** — optional GitHub release check when the app starts (Settings → Updates).

## Installation

1. Open [Releases](https://github.com/coollogin/SMuzy/releases/latest).
2. Download `SMuzy-<release>.apk` from that release's assets.
3. Allow installation from unknown sources if Android asks, then install.

Requirements: Android 7.0 (API 24) or newer.

Because SMuzy uses its own application ID (`io.github.coollogin.smuzy`), it installs alongside other Subsonic clients.

APK signing certificate SHA-256 fingerprint:

`01:97:D9:D9:92:3C:88:A6:F7:AA:C2:AB:E3:D8:3B:EA:80:DC:43:A5:8F:1E:43:9E:F0:88:3B:E7:37:5B:9E:08`

## Building

Requirements: JDK 21 and the Android SDK (API 37, build-tools 36.1.0). The Gradle toolchain downloads JDK 21 automatically.

```bash
# Debug build
./gradlew assembleSmuzyDebug

# Release build (unsigned locally; CI signs release APKs)
./gradlew assembleSmuzyRelease

# Unit tests and lint
./gradlew testSmuzyDebugUnitTest
./gradlew lintSmuzyDebug
```

The generated APK is in `app/build/outputs/apk/smuzy/`.

## Update checks

SMuzy checks the latest GitHub release when the app starts, at most once every 15 minutes, and offers the download link when a newer version is published. The check can be disabled in **Settings → Updates**; there is also a **Check for updates now** action. It reads the public repository API without any account or token.

## Contributing

Bug reports and pull requests are welcome at [github.com/coollogin/SMuzy](https://github.com/coollogin/SMuzy). Please make sure the project builds and tests pass before opening a PR, and include before/after screenshots for UI changes.

## License and attribution

SMuzy is released under the [GNU General Public License v3.0](LICENSE).

SMuzy is a fork of [eddyizm/tempus](https://github.com/eddyizm/tempus), forked from v4.32.0. The original project/repository was created by [CappielloAntonio](https://github.com/CappielloAntonio). SMuzy modifications are Copyright © 2026 coollogin. The original authors and contributors retain the rights to their work; their copyright notices and the GPL license are preserved in [LICENSE](LICENSE).

Thanks to [Likemiens/vinyl-player](https://github.com/Likemiens/vinyl-player) for the album animation idea.

## Author

coollogin — [houseofcool@ya.ru](mailto:houseofcool@ya.ru)
