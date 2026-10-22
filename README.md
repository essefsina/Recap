# Recap

A full Android dialer with on-device AI call notes — automatic titles, summaries, and speaker-labeled transcripts, generated entirely on the phone. No accounts, no cloud, nothing leaves the device.

Recap is a complete default-phone-app replacement: Keypad, Recents, Contacts, Voicemail, and Recordings, with five color themes + dark mode, a floating call bubble for games and fullscreen apps, dual-SIM support, and a launcher icon whose background follows your theme.

## ⚠️ About call recording — read this first

Android only allows call recording from the app that holds the default-dialer role, which is why Recap is a full dialer. But even then, **whether call audio is actually delivered to the app is decided by the phone's firmware** — and on many modern phones the honest answer is that it isn't.

This was verified the hard way, on a **Motorola Edge Plus 2023 (Android 16)**:

- Recap tries **ten capture paths** (dual uplink/downlink channels, VOICE_CALL stereo/mono, VOICE_COMMUNICATION, VOICE_RECOGNITION, microphone, unprocessed input, plus a second engine via MediaRecorder), from both the call service and the foreground call screen.
- On the Motorola, **every path delivered digital silence during a call** — including the plain microphone, while the same microphone tested loud and clear outside a call, and including on speakerphone. The stock Google Phone app on the same device/carrier offers no recording option either.
- Conclusion: on devices like this, **real both-sides call recording requires root.** No Play-Store-legal app can do it. Any app that claims otherwise on such a phone is faking it or recording only your side of a speakerphone call.

On phones whose firmware *does* hand call audio to the default dialer, Recap records normally — and it can prove it: every capture path is metered while recording, and each saved recording carries a diagnostics record of exactly which path delivered the audio and at what level. If a recording comes back silent, the app says so in plain language instead of pretending.

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
