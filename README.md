# LyricsWorld

**Karaoke lyrics floating in your Minecraft world — with emoji that react to what is being sung.**
Client-side only. Works on vanilla and third-party servers (PVP included): the server never sees, needs or is sent anything.

> Licensed under the **GNU AGPL v3** (see [LICENSE](LICENSE)). Third-party notices: [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md).

## What it does

* Reads what you are playing on **Spotify** (Web API, OAuth PKCE) or **Apple Music / any player** (OS media session) and keeps in sync with pause / seek / track change.
* Downloads time-synced lyrics at runtime and shows them as billboard text **behind your shoulders, above your head or in front of you**; the current word is highlighted, sung words fade, upcoming words are dimmed.
* Spawns **emoji** from the meaning of the lyrics ("you broke my heart" → 💔, "non piango più" does *not* give 😭). ~560 rules per language (English + Italian), 26 categories, rules are plain JSON you can extend.
* A **separate settings window** (Windows / macOS / Linux) opens with the game: connect players, live preview of lyrics, every option.

## Supported versions

| Minecraft | Fabric | Forge | Java | Notes |
|-----------|:------:|:-----:|:----:|-------|
| 1.20.1    | ✅ (Fabric Loader + Fabric API) | ✅ (47.x) | 17 | Fabric & Forge share one renderer (Mojang mappings) |
| 1.12.2    | ❌ not built | ✅ (14.23.5.2847) | 8 | see "Legacy Fabric" below |
| 1.8.9     | ❌ not built | ✅ (11.15.1.2318) | 8 | Forge only |

Jars are named `lyricsworld-<loader>-<mc>-<version>.jar` and are attached to each GitHub Release.

**Legacy Fabric (1.12.2) and Fabric on 1.8.9 are not provided.** Those ports use different mappings (Yarn for the old versions)
and a separate toolchain; I could not validate them, and I'd rather not ship something that pretends to work. The loader-independent
`core` contains ~95 % of the logic, so a port is a thin module (≈4 small classes: `Game…`, `Renderer…`, hooks, metadata) — see
`targets/forge-1.12.2` as a template. PRs welcome.

## Install

1. Install Fabric (+ Fabric API) or Forge for your Minecraft version.
2. Drop the matching jar into `mods/`.
3. Start the game. The settings window opens by itself (key **J** toggles the lyrics, a second unbound key re-opens the window).
4. In the window pick your music source (see below).

## Connecting your music

### Spotify (any platform, any Spotify client — even your phone)
1. Go to <https://developer.spotify.com/dashboard> and create an app. Add the redirect URI shown in the *Connections* tab
   (default `http://127.0.0.1:8898/callback`).
2. Paste its **Client ID** into the window and press **Connect Spotify**. No client secret is involved (PKCE).
3. The refresh token is stored only on your machine, AES-256-GCM encrypted (`config/lyricsworld/spotify_token.bin`).
   This stops casual copying of the file; it does not protect against malware running as your user.

Spotify's developer rules (e.g. whether a Premium account is required for development-mode apps) are Spotify's to change — check them.

### Apple Music and other players (OS media sessions)

| OS | How | Limits |
|----|-----|--------|
| Windows | System Media Transport Controls via a hidden PowerShell 5.1 helper | Works for Apple Music for Windows, Spotify desktop, browsers… Position precision depends on how often the app updates its timeline |
| macOS | AppleScript to `Music.app` (Apple Music) and `Spotify.app` | First use asks for *Automation* permission. Apple's private MediaRemote is **not** used (blocked for third parties since macOS 15.4), so players other than Music/Spotify are not detected — use the Spotify Web API instead |
| Linux | MPRIS2 through the `playerctl` command (install it from your distro) | No native Apple Music client exists; web/unofficial clients that expose MPRIS (Cider, Chromium…) work through *System* mode |

`Auto` mode uses the Spotify Web API when connected and falls back to the OS session.
The **sync offset** slider compensates latency; small drift is smoothed, big jumps are treated as seeks.

## Lyrics sources (read this)

* **[LRCLIB](https://lrclib.net)** — free, key-less, community-maintained synced lyrics, queried **at runtime from your computer**, cached under `config/lyricsworld/cache/`. Nothing is shipped in this repository or the jars.
* LRCLIB is **line-synced**. Word-level timing is used when a file contains Enhanced-LRC `<mm:ss.xx>` tags; otherwise words are **interpolated** and the window says *"Estimated word sync"*. (Musixmatch/Apple word-level data is not freely accessible and is not used.)
* Your own files win: put `Artist - Title.lrc` (plain or Enhanced LRC) in `config/lyricsworld/lyrics/`.
* Lyrics belong to their rightsholders; they are only displayed to you, locally.

## Emoji

* Rules: `core/src/main/resources/assets/lyricsworld/emoji/rules_en.json`, `rules_it.json`. Extend without rebuilding by dropping extra `*.json` files (same format) into `config/lyricsworld/emoji/`. Add a new language with `"language": "xx"` plus `"stopwords"` (used to detect each line's language) and `"negations"`.
* Matching: single words, prefix wildcards (`piang*`), phrases (`"broke* heart"`), accent-insensitive, negation within 3 words ("non", "never", "don't"…), phrases beat single words.
* Options: intensity 0–100 % (frequency, count, size), main-only, variety, cooldown, per-category on/off.
* Pictures: **[Twemoji](https://github.com/jdecked/twemoji)** graphics, © Twitter, Inc. and contributors, **CC-BY 4.0**, packed into `emoji_atlas.png` by `tools/build_emoji_atlas.py` (only emoji that rules use; custom emoji need to exist in the atlas).

## Client-side only — how that is enforced

* No custom packets, registries, entities or mixins. Lyrics and emoji are just text/quads drawn by the client; they have no hitbox and never touch gameplay.
* Fabric: `"environment": "client"`. Forge 1.20.1: `displayTest="IGNORE_ALL_VERSION"` (client-only, ignored by the server mod-list check). Forge 1.12.2 / 1.8.9: `clientSideOnly = true`, `acceptableRemoteVersions = "*"`.
* No telemetry. The only network calls are LRCLIB and Spotify (if you connect it), from your machine.

## Privacy

Tokens stay on disk locally; the control API between game and window listens on `127.0.0.1` only, with a random per-launch token and Host-header checks; nothing is sent to any server owned by this project.

## The settings window & macOS

Swing inside Minecraft's own process fights LWJGL/GLFW for the main thread on macOS, so the window runs as a **separate Java process** that talks to the game over loopback and exits when the game exits. Headless machines simply skip it (settings stay editable in `config/lyricsworld/settings.json`). `Open cache folder` / `Open log folder` buttons are in the footer; logs are in `config/lyricsworld/logs/`.

## Build

```bash
./build-all.sh            # core tests + every loader/version → dist/*.jar   (JDK 17)
./build-all.sh 1.20.1     # only matching targets
```

Layout: `core/` (Java 8, all logic, unit-tested) · `targets/common-1.20.1` (shared 1.20.1 renderer) · `targets/<loader>-<mc>` (thin glue, each an independent Gradle build because the toolchains need different Gradle versions).
Run core tests alone: `./gradlew :core:test`. Rebuild the emoji atlas after changing rules: `python3 tools/build_emoji_atlas.py <path to emoji-datasource-twitter>`.

### CI / releases

* `ci.yml` builds and tests on every push/PR **only if** `core/`, `targets/`, Gradle files or the workflows changed. README, docs, licences, images, `.gitignore`, templates never trigger it.
* `release.yml`: a push to `main` that changes mod files publishes `vX.Y.(Z+1)` (add `#minor`/`#major` to a commit message to bump higher) with all jars; pushing a `vX.Y.Z` tag by hand works too (skipped if only non-mod files differ from the previous tag); or run it manually with a version.

## Manual test checklist

1. Join a **vanilla** server (and a Forge one without the mod): no kick, no mod-mismatch screen.
2. Play a song in Spotify/Apple Music; the window shows the track within ~2 s and the lyrics preview.
3. Lyrics appear behind you; pause → they freeze; seek → they jump; skip → they change.
4. Sing-along words highlight; a line with "heart", "rain", "fire"… spawns emoji; "I don't cry" does not spawn 😭.
5. Move/attack: lyrics fade; stop: they return.
6. Close the window: game continues; press the *window* key: it comes back. Quit the game: the window disappears.
7. Disconnect the network: game stays silent, window says why.

## Status

See the release notes / CI badge for what is currently verified. Honest limits: the Windows SMTC helper, macOS AppleScript and Linux `playerctl` paths are covered by parsing unit tests but could only be exercised on real machines by you; in-game rendering of every loader target is verified to *compile* in CI but must be eyeballed in a real client.
