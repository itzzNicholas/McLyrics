# Third-party material used by LyricsWorld

LyricsWorld itself is licensed under the **GNU Affero General Public License v3.0 or later**
(see `LICENSE`). Everything below is separate material with its own terms.

## Emoji graphics - Twemoji

* **What:** `assets/lyricsworld/textures/emoji_atlas.png` is a sprite sheet of Twemoji images.
* **Source:** Twemoji, https://github.com/jdecked/twemoji (maintained fork of Twitter's Twemoji),
  obtained through the `emoji-datasource-twitter` npm package.
* **Copyright:** Copyright 2019 Twitter, Inc and other contributors (graphics).
* **License:** the graphics are licensed under **CC-BY 4.0**
  (https://creativecommons.org/licenses/by/4.0/). The attribution required by that license is this
  notice, shipped inside every LyricsWorld jar (`THIRD_PARTY_LyricsWorld.md`) and in the README.
* The atlas is a modified (re-arranged/combined) version of the original images; the artwork itself is unchanged.
* Rebuild it with `tools/build_emoji_atlas.py`.

CC-BY 4.0 material may be combined with AGPL-3.0 code as long as it stays clearly attributed and
separable (it is, under `assets/`); the AGPL does not relicense the artwork.

## Lyrics font - Rock Salt

* **What:** `assets/lyricsworld/textures/font_atlas.png` + `assets/lyricsworld/font/glyphs.json` are glyph images generated from **Rock Salt**.
* **Source:** Google Fonts / the `@fontsource/rock-salt` npm package. Copyright (c) 2010 by Font Diner, Inc DBA Sideshow.
* **License:** **Apache License 2.0** (full text: `docs/fonts/LICENSE-RockSalt-Apache-2.0.txt`). The glyphs are rasterised, slightly thickened and outlined; no other changes.
* Rebuild with `tools/build_font_atlas.py`.

## Lyrics - LRCLIB

* **Where:** https://lrclib.net - a free, key-less, community-maintained database of synced lyrics
  (API documented at https://lrclib.net/docs).
* **How it is used:** LyricsWorld queries it **at runtime, on the player's own machine**, for the song that
  player is listening to, and keeps a private cache on disk. **No lyrics are stored in this repository or
  inside the mod jar.**
* **Terms:** lyrics remain the property of their respective rightsholders. LRCLIB provides them as
  user-contributed data for personal use; please read the service's own terms before relying on it for
  anything beyond personal display. LyricsWorld identifies itself with a `User-Agent` header as LRCLIB requests.
* LRCLIB provides **line-level** timestamps (a few entries use the Enhanced-LRC word format). When only
  line timestamps exist, LyricsWorld **estimates** word timing and says so in the settings window.
* You can bypass online lookups entirely: put your own `Artist - Title.lrc` files into
  `config/lyricsworld/lyrics/` (or turn the online lookup off in the settings window).

## Music players

* **Spotify Web API** (https://developer.spotify.com/documentation/web-api): used with OAuth PKCE, with a
  Client ID that **you** register. Subject to Spotify's Developer Terms. Only the "currently playing" and
  "playback state" read scopes are requested. LyricsWorld is not affiliated with or endorsed by Spotify.
* **Apple Music / system players:** read through the operating system's own media-session interfaces
  (Windows SMTC, macOS AppleScript for Music.app, Linux MPRIS through `playerctl`). No Apple API keys or
  private Apple services are used. Not affiliated with or endorsed by Apple.

## Libraries

* **Gson** (Apache-2.0) - provided by Minecraft at runtime, not bundled.
* Build tooling only (not shipped): Gradle, Fabric Loom (MIT), ForgeGradle (LGPL-2.1), Architectury/Essential Loom (MIT).
* Minecraft is a trademark of Mojang AB / Microsoft; this project is not affiliated with Mojang or Microsoft.
