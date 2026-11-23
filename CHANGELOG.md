# Changelog

## 1.0.4 — 2026-11-23

### Fixes

- The "Starred tracks" and "Top songs of favorite artists" rows on Home no longer disappear after visiting Library and returning.
- The "Recently played" Home section is now titled "Недавно играло" in Russian.

### Features

- New Settings → UI toggle "Radio stations on Home" (off by default) that shows or hides the radio stations section for your favorite artists.

## 1.0.3 — 2026-11-22

### Fixes

- The USER_TAG cloud works again in release builds. R8 was renaming and stripping the Gson models for Navidrome's native API, which broke the login request and `/api/song` parsing in minified APKs.

## 1.0.2 — 2026-11-22

### Fixes

- Update checks now run again shortly after launch instead of waiting a full day, and a failed GitHub request no longer postpones the next check.
- Settings → Updates has a new "Check for updates now" action with progress, success and error feedback.
- The USER_TAG cloud is refreshed at most once per day and checks its freshness when the Library screen returns to the foreground.
- The About section now shows the current version and describes SMuzy as a beautiful and lightweight open-source Subsonic client. The unDraw credit was removed.

## 1.0.1 — 2026-11-21

### Fixes

- The spectrum visualizer is now on by default and starts with the Pedestal (mirrored bars) style; installs upgrading from 1.0.0 get the new defaults once.
- Enabling the visualizer no longer leaves the vinyl cover animation stuck inside the sleeve.
- The Tags section no longer disappears after repeated refreshes: Navidrome's login rate limit (HTTP 429) and transient server errors are treated as retryable errors, and the native API token is refreshed from Navidrome's rotation header and reused between scans instead of logging in on every page.

## 1.0.0 — 2026-11-21

First public SMuzy release, based on the earlier open-source Subsonic client this project forked from.

### Branding and identity

- New SMuzy name, launcher icon, brand-orange Material 3 palette and vinyl artwork placeholders.
- New application ID (`io.github.coollogin.smuzy`), so SMuzy installs alongside other clients.
- Build flavor renamed to `smuzy`; Java namespace and source packages moved to `io.github.coollogin.smuzy`.
- About screen now shows the SMuzy author and support contact.

### Features

- Subsonic integration for Navidrome, Gonic, LMS, Nextcloud Music, Ampache, Airsonic Advanced and other compatible servers.
- Browsing and search for artists, albums, genres, playlists, decades and folders.
- Streaming, offline downloads, playlist management, gapless playback and ReplayGain.
- Optional Last.fm and ListenBrainz scrobbling.
- Internet radio search and saved stations.
- Server-side transcoding profiles, multiple music libraries, equalizer and home-screen widget.
- Android Auto and Chromecast in the GitHub build; degoogled and play variants build without Google services.
- USER_TAG cloud for browsing Navidrome user tags in the Library section.
- Optional cover animation and spectrum visualizer in the full-screen player.
- Local network playback control between two phones running SMuzy.
- UPnP/DLNA playback, sleep timer, playback speed control and settings export/import.

### Updates

- Optional daily check for new SMuzy releases on GitHub, available under Settings → Updates.
- Update checks read the public `coollogin/SMuzy` repository API without an account or token.

### License

- SMuzy is released under GPL-3.0. Upstream copyright notices and attribution are preserved in [LICENSE](LICENSE).
