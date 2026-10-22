# Recap

A full Android dialer with on-device AI call notes — automatic titles, summaries, and speaker-labeled transcripts, generated entirely on the phone. No accounts, no cloud, nothing leaves the device.

Recap is a complete default-phone-app replacement: Keypad, Recents, Contacts, Voicemail, and Recordings, with five color themes + dark mode, a floating call bubble for games and fullscreen apps, dual-SIM support, and a launcher icon whose background follows your theme.

## ⚠️ About call recording — read this first

Android only allows call recording from the app that holds the default-dialer role, which is why Recap is a full dialer. But being the default dialer is not enough — and this is the part most recorder apps never tell you:

**Android's security model reserves real call audio for system apps.** The capture sources that carry a phone call (`VOICE_CALL`, `VOICE_UPLINK`, `VOICE_DOWNLINK`) are restricted to privileged apps — the ones preinstalled in the phone's system partition with platform-level permissions. That is the *only* reason a manufacturer's built-in recorder works where it exists: it ships as part of the phone. A normal user-installed app — installed from the Play Store or sideloaded, it makes no difference — runs unprivileged, and on most modern phones the system feeds it silence for the duration of a call.

So on a phone that behaves this way, there are exactly two routes to working call recording, and neither is available to a regular app:

1. **Root the phone**, so the recorder can run with system-level access (for example as a Magisk module or installed into the system partition), or
2. **The app is a system app** — which only the phone's manufacturer or carrier can ship. You can't install your way there.

Recap is deliberately neither. It is an ordinary app, and on such a phone it cannot record calls. Any third-party recorder claiming otherwise on a blocked phone is faking it, or quietly capturing only your side through the microphone on speakerphone.

This was verified the hard way, on a **Motorola Edge Plus 2023 (Android 16)**:

- Recap tries **ten capture paths** (dual uplink/downlink channels, VOICE_CALL stereo/mono, VOICE_COMMUNICATION, VOICE_RECOGNITION, microphone, unprocessed input, plus a second engine via MediaRecorder), started both from its call service and from the foreground call screen, on earpiece and on speakerphone.
- **Every path delivered digital silence during a call** — including the plain microphone, which tested loud and clear the moment no call was active. The stock Google Phone app on the same device and carrier offers no recording option at all.

On phones whose manufacturers *do* let the default dialer hear call audio (some models and regions), Recap records normally — and it can prove it: every capture path is metered while recording, and each saved recording carries a diagnostics record of exactly which path delivered the audio and at what level. If a recording comes back silent, the app says so in plain language instead of pretending.

**Status:** development is parked. The Motorola verdict above is why — the dialer, AI transcription, summaries, and diagnostics all work; the test phone's firmware simply refuses to share call audio with any third-party app.

## Features

- **Dialer** — keypad with frequent-calls row, smart number suggestions, dual-SIM picker and per-SIM defaults, grouped Recents with filters, contact view with merged history, call-log deletion
- **In-call UI** — mute / speaker / hold / DTMF keypad, consent announcement option, floating bubble when you leave the call screen (with an answer/decline ringing card that never hijacks a game)
- **Recording engine** — ten metered capture paths with a silence watchdog that hops paths automatically, bounded gain lift for quiet audio, per-recording diagnostics
- **On-device AI** — Vosk speech-to-text (model bundled, works offline), speaker labels from dual-channel capture where available, automatic call titles and summaries
- **Voicemail** — visual voicemail where the carrier supports it, with the same transcription pipeline
- **Themes** — Forest / Ocean / Violet / Sunset / Rose + dark mode; the launcher icon recolors with the theme

## Building

No Gradle — Recap builds with a manual pipeline (`build.sh`), the same approach as [DropIt](https://github.com/essefsina/DropIt):

- JDK 17 and the Android SDK (platform 34, build-tools 34.0.0)
- `aapt2` → `kotlinc` → `d8` → `zipalign` → `apksigner`
- Dependencies are vendored in `libs/` (Vosk, JNA, Kotlin stdlib); the speech model is bundled in `app/src/main/assets/`
- The signing keystore is **not** in this repo — generate your own and point `build.sh` at it

```bash
VER_CODE=8 VER_NAME=0.8.0 ./build.sh
```

`make_icons.py` regenerates the themed launcher icons from the logo master.

## License

MIT — see [LICENSE](LICENSE).
