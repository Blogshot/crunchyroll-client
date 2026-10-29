# Design – Crunchyroll TV

Stand 2026-10-29 · Entwurf. Ausführliche Fassung mit Begründungen:
[Architekturvorschlag](https://claude.ai/artifact/BPFJLnQ4RmyuJKPtkzoUzA).

## Session-Regeln

1. Genau ein `SessionManager` besitzt die Token (`core/.../auth/SessionManager.kt`).
2. Alle Erneuerungen laufen über eine Mutex. Wer wartet, übernimmt das Ergebnis, statt selbst zu erneuern.
   Crunchyroll rotiert das Refresh-Token bei jeder Erneuerung; zwei parallele Refreshes würden die Session zerstören.
3. Neues Refresh-Token wird gespeichert, bevor es benutzt wird.
4. Abmelden nur bei `invalid_grant`. Netzwerkfehler, 5xx und 429 lassen die Session bestehen.
5. Proaktive Erneuerung beim Aufwachen der App (`ProcessLifecycleOwner.onStart`).
6. Bei 401 erneuert der `TokenAuthenticator` einmal und wiederholt den Request; ein zweiter 401 geht an die UI.

## Browsing-Regeln (ab M2)

- Jeder Bildschirm: `Loading / Content / Error` mit „Erneut versuchen“, nie eine stille leere Seite.
- Cache zuerst anzeigen, im Hintergrund aktualisieren (Weiterschauen nach jeder Wiedergabe gezielt).
- Zurück stellt Scrollposition und Fokus wieder her; Rails laden und scheitern unabhängig voneinander.

## Meilensteine

| | Inhalt |
|---|---|
| M0 | API-Probe mit eigenem Konto: Gerätecode, Refresh, Home-Feed, Playback; Antworten als Fixtures |
| M1 | Gerüst, CI-APK, Gerätecode-Login, `SessionManager` mit Tests |
| M2 | Home, Weiterschauen, Watchlist, Serie/Staffeln/Folgen, Suche |
| M3 | ExoPlayer + Widevine, Fortschritt, Sprachen, nächste Folge |
| M4 | Profile, Intro überspringen, Einstellungen |
