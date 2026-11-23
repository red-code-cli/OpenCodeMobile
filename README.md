# OpenCode Mobile (Jetpack Compose) — UI-only prototype

Package: `com.opencode.mobile` · minSdk 26 · Kotlin 2.0.20 · Compose BOM 2024.09.03

## Open / run
1. Android Studio (Koala+) → *Open* → this folder → Gradle sync (wrapper jar auto-generate ho jayega).
2. Run `app` on device/emulator.

## Structure
- `data/AppViewModel.kt` – saari UI state + fake data. **Yahi ek jagah hai jahan server wiring aayegi.**
- `data/Models.kt` – Session / Msg / Part / ModelOption + sample data.
- `ui/theme` – OpenCode dark/light tokens (`Oc.c.*`).
- `ui/Components.kt` – TopBar (pulsing dot), spinning ring, DottedWave, DotSpinner, OcSwitch.
- `ui/Composer.kt` – message box: `/` and `@` popup, agent dropdown, model picker, mic -> recording bar, send/stop.
- `ui/screens` – Home, Chat (tools expand, copy, scroll-to-bottom), ModelSheet, Drawer, Settings (Appearance, Language, Sessions, Voice, Experimental, What's New).

## Next (server integration)
`AppViewModel.send()` ko repository se replace karo (OpenCode REST + SSE event stream),
`sessions/messages/projects/agents/models` ko server se load karo.
Voice ke liye `RECORD_AUDIO` permission + AudioRecord level ko `DottedWave(active=..)` me feed karna.
