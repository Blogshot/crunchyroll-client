# Crunchyroll TV

Inoffizieller Crunchyroll-Client für Android TV / Google TV, gebaut für zwei Dinge, die die offizielle App nicht
zuverlässig kann: **Sessions, die halten**, und **Browsing, das sich selbst aktualisiert**.

> **Status:** Login, Profilauswahl und Browsing stehen; der Player (M3) folgt.
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

### Login und Client-Kennung

Im APK stehen keine Zugangsdaten. Die App fragt beim ersten Start nach E-Mail und Passwort (das Passwort wird nur
für die Anmeldung gesendet, nicht gespeichert) und danach nach dem Profil („Wer schaut?“).

Zusätzlich verlangt Crunchyroll eine Client-Kennung der App. Die liest die App zur Laufzeit von der Startseite
von crunchyroll.com (öffentliche Kennung der Web-App) und speichert sie verschlüsselt. Klappt das nicht, fragt der
Login-Bildschirm einmal danach.

### API-Probe

Der Workflow **API-Probe** (manuell starten oder auf `probe/<name>` pushen) meldet sich mit den Repository-Secrets
`CR_USERNAME`/`CR_PASSWORD` an und prüft die Endpunkte. Er gibt nur Statuscodes, Anzahlen und Feldnamen aus.

## Installation auf dem TV

Jeder Push auf `main` ersetzt das Vorab-Release **android-latest** (`crtv-latest.apk`, debug-signiert mit festem
Schlüssel, Updates lassen sich drüberinstallieren). Installation per `adb install -r crtv-latest.apk` oder mit der
App „Downloader“.
