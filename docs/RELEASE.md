# Wie wir RC2 bauen und ausliefern

Wir verwenden Version **1.0.0-rc2**, versionCode **31**, JDK 17, Gradle 8.11.1, AGP 8.9.2 und Kotlin 2.1.20. Wir behalten CameraX 1.6.2, Kameraabläufe, HID-Zeitsteuerung, Gehäusebelegung und die fünf stets sichtbaren Aufnahmebuttons bei. Wir aktivieren weder R8 noch Ressourcenverkleinerung in diesem Schritt.

## Welche Varianten wir verwenden

| Variante | Unser Paket | Unsere Signierung | Unser Installationsweg |
|---|---|---|---|
| Produktions-APK | `de.jce.seafrogs` | Eigener dauerhafter Release-Schlüssel, nicht debuggable | Wir installieren parallel zur aktuellen Test-App. |
| Play-App-Bundle | `de.jce.seafrogs` | Derselbe Release-/Upload-Schlüssel | Wir bereiten das AAB vor; vor der Play-Einreichung migrieren und prüfen wir Ziel-API 36. |
| Kompatibles Test-Update | `de.jce.seafrogs.test` | Bisheriger gesicherter Debug-Schlüssel | Wir aktualisieren unsere bisherige SeaFrogs-Test-Installation. |

Wir übernehmen bei einem Paketwechsel Einstellungen und Berechtigungen nicht automatisch. Falls ein alter Prototyp bereits `de.jce.seafrogs` mit einer anderen Signatur verwendet, sichern wir dessen benötigte Daten und entfernen ihn vor der Produktionsinstallation. Wir bewahren unsere aktuelle Test-App bis zur Abnahme der Produktionsvariante auf.

## Wie wir lokal signieren

Wir bewahren Keystore und Passwortdatei außerhalb des Repositorys auf. Wir setzen die vier Umgebungsvariablen `SEAFROGS_RELEASE_KEYSTORE`, `SEAFROGS_RELEASE_STORE_PASSWORD`, `SEAFROGS_RELEASE_KEY_ALIAS` und `SEAFROGS_RELEASE_KEY_PASSWORD`. Wir verwenden einen absoluten Keystore-Pfad.

```bash
./gradlew --no-daemon :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
```

Wir finden APK und AAB unter `app/build/outputs/apk/release/app-release.apk` und `app/build/outputs/bundle/release/app-release.aab`. Wir prüfen die APK mit `apksigner verify --verbose --print-certs` und `zipalign -c -P 16 4`; wir prüfen das AAB mit `jarsigner -verify`. Wir prüfen zusätzlich die ELF-Segmentausrichtung der nativen Bibliothek. Wir vergleichen das Signierzertifikat mit unserem gesicherten Schlüssel. Wir erwarten bei erneutem Build dieselbe Paketidentität und Signatur; bitidentische Artefakte behaupten wir ohne gesonderte Prüfung nicht.

Für ein kompatibles Test-Update bauen wir mit unserem bestehenden Testschlüssel:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PseafrogsParallelTest=true -PseafrogsDebugKeystore=/absoluter/privater/Debug-Schluessel.jks
```

## Wie wir GitHub Actions vorbereiten

Wir prüfen Pushes auf `main` und Pull Requests mit `.github/workflows/android-checks.yml`: Unit-Tests, Lint und Debug-Build. Wir pinnen verwendete Actions auf Commit-Hashes und sichern Prüfberichte als Artefakte.

Wir starten `.github/workflows/android-release.yml` manuell. Dieser Workflow erzeugt und prüft signierte Produktions-APK und AAB. Wir konfigurieren zuvor vier Repository-Secrets: `SEAFROGS_RELEASE_KEYSTORE_BASE64`, `SEAFROGS_RELEASE_STORE_PASSWORD`, `SEAFROGS_RELEASE_KEY_ALIAS` und `SEAFROGS_RELEASE_KEY_PASSWORD`. Base64 ersetzt keine Verschlüsselung; wir verwenden dafür die GitHub-Secrets-Verwaltung.

Wir entpacken unsere private Signiersicherung außerhalb des Repositorys und melden die GitHub CLI mit Schreibberechtigung für Actions-Secrets an. Anschließend übertragen wir die Werte ohne Ausgabe ihrer Inhalte:

```bash
python tools/configure-release-secrets.py /privater/Pfad/release-signing.json
```

Wir prüfen danach die Secret-Namen und starten den Release-Workflow. Wir haben diese Secrets mit den hier verfügbaren Schnittstellen noch nicht im GitHub-Konto hinterlegt. Der Prüfworkflow benötigt keine Signier-Secrets. Wir veröffentlichen durch den Release-Workflow noch nichts in Google Play.

## Wie wir die private Signierung sichern

Wir sichern den Release-Keystore gemeinsam mit der zugehörigen Konfiguration in einem privaten Sicherungsarchiv. Wir behandeln dieses Archiv wie einen privaten Schlüssel: Wir verteilen es nicht mit der APK und laden es weder in das Repository noch in öffentliche Releases. Die JKS-Datei ist mit einem Kennwort geschützt; das Sicherungsarchiv enthält auch dieses Kennwort und benötigt deshalb einen privaten Aufbewahrungsort.

Wir stimmen vor der ersten Play-Veröffentlichung den App-Signing-Key mit dem gewünschten direkten APK-Updateweg ab. Ein von Google neu erzeugter App-Signing-Key kann von unserem Upload-Schlüssel abweichen; wir setzen die beiden Schlüssel nicht automatisch gleich.

## Was wir an der Protokollierung ändern

Wir deaktivieren neue HID-Rohereignisse, Maus-/Tasten-Snapshots, Gerätemarkierungen, Gerätewechsel und HID-Befehle. Wir erzeugen ihre JSON-Snapshots bei deaktivierter Protokollierung nicht. Wir sammeln keine Eingaben mehr im Absturzbericht. Wir behalten Kamera-/Testereignisse, Export und die normale Absturzbehandlung bei. Wir löschen vorhandene historische Protokolle oder Absturzdateien nicht automatisch.

Wir lassen die Diagnoseoberfläche bestehen und kennzeichnen die deaktivierte HID-Aufzeichnung. Ein neuer Diagnoseexport enthält keine HID-Ereignisse. Wir formatieren Kotlin einheitlich und verwenden eindeutigere Namen für UI-Elemente, Kamerazustände und Zeitpunkte. Wir ändern dabei weder Aufnahmebedingungen noch HID-Grenzen oder Umschaltlogik.

## Was wir noch in Google Play erledigen

Wir richten das verifizierte Entwicklerkonto, die App mit Paket `de.jce.seafrogs`, Play App Signing und den internen Testkanal ein. Wir behalten in RC2 `targetSdk = 35` bei, um das bisherige Android-Laufzeitverhalten zu erhalten. Seit 31. August 2026 verlangt Google für neue Einreichungen Ziel-API 36. Wir migrieren und prüfen diese Laufzeitänderungen vor der ersten Play-Einreichung separat. Unser signiertes AAB ist deshalb ein technisch geprüftes Build-Artefakt, noch keine vollständig Play-freigegebene Auslieferung. Wir stützen uns auf [Googles Ziel-API-Anforderungen](https://support.google.com/googleplay/android-developer/answer/11926878).

Nach dieser Migration laden wir das geprüfte AAB hoch, vervollständigen die erforderlichen App-Angaben und laden unseren Testkreis ein. Wir haben noch keinen Play-Release veröffentlicht. Eine direkte APK-Signatur allein beseitigt Androids Freigabe unbekannter Installationsquellen nicht.
