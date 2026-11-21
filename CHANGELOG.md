# Changelog

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
