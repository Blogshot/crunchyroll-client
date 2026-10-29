# Crunchyroll TV

Inoffizieller Crunchyroll-Client für Android TV / Google TV, gebaut für zwei Dinge, die die offizielle App nicht
zuverlässig kann: **Sessions, die halten**, und **Browsing, das sich selbst aktualisiert**.

> **Status:** M1 – Grundgerüst und Session-Handling. Startseite (M2) und Player (M3) folgen.
> Architektur: [docs/DESIGN.md](docs/DESIGN.md).

**Kein Umgehen von Schutzmechanismen.** Wiedergabe läuft über Widevine und den Lizenzserver von Crunchyroll mit dem
eigenen Abo. Die App entschlüsselt nichts selbst und ignoriert keine Stream- oder Regionslimits.
Gedacht für die private Nutzung mit dem eigenen Konto.

## Aufbau

| Modul | Inhalt |
|-------|--------|
| `core` | Reines Kotlin/JVM: `SessionManager`, Token-Endpunkte, OkHttp-Interceptor/Authenticator. Ohne Android SDK testbar. |
| `app` | Android-App: Compose for TV, verschlüsselter Token-Speicher, Gerätecode-Login. |

## Entwicklung

Voraussetzung: JDK 17+, für `app` ein Android SDK (compileSdk 36).

```bash
./gradlew -PcoreOnly :core:test   # Session-Logik testen, ohne Android SDK
./gradlew :app:assembleDebug      # APK nach app/build/outputs/apk/debug/
```

### Client-Kennung

Die API erwartet die Client-Kennung einer Crunchyroll-App. Sie steht **nicht** im Repo. Lokal in `local.properties`:

```properties
CR_CLIENT_ID=...
CR_CLIENT_SECRET=...
CR_USER_AGENT=...
```

In GitHub Actions als Repository-Secrets mit denselben Namen. Ohne Werte baut die App, der Login schlägt aber fehl.

## Installation auf dem TV

Jeder Push auf `main` ersetzt das Vorab-Release **android-latest** (`crtv-latest.apk`, debug-signiert mit festem
Schlüssel, Updates lassen sich drüberinstallieren). Installation per `adb install -r crtv-latest.apk` oder mit der
App „Downloader“.
