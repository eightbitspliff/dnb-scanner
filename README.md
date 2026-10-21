# DnB / Jungle Scanner (Android)

Android-App, die neue **Drum & Bass**- und **Jungle**-Releases der letzten **Woche**, des letzten **Monats**
oder der letzten **3 Monate** sucht und jedes Release mit **YouTube Music** verknüpft.

## Funktionen

- Zeitraum per Umschalter: Woche / Monat / 3 Monate
- Quellen:
  - **Bandcamp** (Discover-Feed „neu“ für die Tags `drum-and-bass`, `drum-bass`, `jungle` – in den Einstellungen änderbar)
  - **MusicBrainz** (Release-Groups mit DnB-/Jungle-Tags im gewählten Zeitraum)
- **Strenge Genre-Prüfung**: Jedes Release wird anhand seiner eigenen Tags geprüft (bei Bandcamp von der
  Release-Seite). Es erscheint nur, wenn DnB-/Jungle-Tags vorhanden sind und nicht von anderen Genres
  (House, Techno, Trance, Dubstep, Lo-Fi, …) überwogen werden. Die Tags stehen auf jeder Karte.
- Doppelte Einträge aus mehreren Quellen werden zusammengeführt (Artist + Titel, ohne Katalognummern/„EP“)
- **YouTube-Music-Abgleich**: Für jedes Release wird in YouTube Music nach Album bzw. Song gesucht.
  - ▶ **YouTube Music** = direkter Link zum gefundenen Album/Song (öffnet die YT-Music-App)
  - 🔍 **In YT Music suchen** = kein sicherer Treffer, öffnet die Suche mit Artist + Titel
  - Treffer werden auf dem Gerät gespeichert; Fehlschläge werden nach 3 Tagen erneut versucht
- **Spotify-Link** neben YouTube Music:
  - mit eigener Spotify Client-ID/Secret (developer.spotify.com, in den Einstellungen) direkt über die Spotify-API
  - ohne Zugangsdaten über song.link: der gefundene YouTube-Music-Link wird in den Spotify-Link umgewandelt
    (ca. 10 Releases pro Minute, läuft im Hintergrund weiter)
  - sonst Spotify-Suche mit Artist + Titel
- Dunkles Neon-Design im Rave-Look mit Equalizer im 174-BPM-Takt, Pull-to-Refresh
- Filter nach Genre, „Nur auf YT Music“, „Nur auf Spotify“, Textsuche (Artist/Titel/Label)
- Teilen-Button, Links zur Quelle (Bandcamp / MusicBrainz)
- Ergebnisse werden pro Zeitraum gespeichert und nach 6 Stunden automatisch aktualisiert

## APK installieren

**Direkter Download (immer der neueste Build):**
https://github.com/eightbitspliff/dnb-scanner/releases/latest/download/dnb-scanner.apk

Alternativ liegt jede APK auch als Build-Artefakt bei GitHub Actions:

1. Im Repo auf **Actions → Android Build** → letzten Lauf öffnen
2. Unter **Artifacts** `dnb-scanner-apk` herunterladen und entpacken
3. `dnb-scanner.apk` auf dem Handy öffnen (Installation aus unbekannten Quellen erlauben)

Wird ein Tag `v*` (z.B. `v0.1.0`) gepusht, landet die APK zusätzlich direkt als Download unter **Releases**.

## Selbst bauen

Android Studio (oder JDK 17 + Android SDK 35):

```bash
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

## Hinweise

- Die Bandcamp-Discover-API und die YouTube-Music-Suche sind inoffizielle Web-Schnittstellen und können sich ändern.
  Fällt eine Quelle aus, zeigt die App eine Warnung und arbeitet mit den übrigen Quellen weiter; YouTube Music fällt
  dann auf Such-Links zurück.
- MusicBrainz ist community-gepflegt: Dort sind nur Releases mit gesetzten Genre-Tags zu finden.
- Für „3 Monate“ werden pro Bandcamp-Tag bis zu 18 Seiten à 60 Releases geladen (Einstellung „Seiten pro Tag“ × 6).
