# Wie wir unsere Kamera installieren und verteilen

Stand unserer Recherche: 9. Oktober 2026. Wir liefern RC2 als signierte Produktions-APK und Play-App-Bundle mit Paket `de.jce.seafrogs` aus. Zusätzlich bauen wir ein kompatibles Update für `de.jce.seafrogs.test` mit dem dauerhaft gesicherten Test-Signierschlüssel. Wir verwenden einen eigenen gesicherten Produktionsschlüssel und die Release-Signierkonfiguration in `app/build.gradle.kts`. Unseren Build und die verbleibende Play-Einrichtung dokumentieren wir in [RELEASE.md](RELEASE.md).

## Welche Meldung wir unterscheiden müssen

Ohne den genauen Wortlaut können wir die konkrete Installationsmeldung nicht eindeutig zuordnen. Wir unterscheiden drei Fälle:

| Meldung | Unsere Einordnung | Unser nächster Schritt |
|---|---|---|
| Installation aus dieser Quelle nicht erlaubt | Android verlangt bei direkter APK-Installation eine Freigabe für den installierenden Browser oder Dateimanager. | Für den regulären Bezug verteilen wir über Google Play. Eine andere Signatur hebt diese Quellenfreigabe nicht auf. |
| Play Protect kennt die App noch nicht / Scan empfohlen | Google hat diese APK noch nicht ausreichend geprüft; die Meldung ist keine automatische Feststellung von Schadsoftware. | Wir lassen die angebotene Prüfung durchführen und bevorzugen einen Play-Testkanal für wiederholte Updates. |
| App als schädlich blockiert | Google hat eine konkrete Schutzentscheidung getroffen. | Wir prüfen Berechtigungen, Verhalten und eingebundene Komponenten; bei einer Fehlklassifikation nutzen wir Googles Einspruchsverfahren. |

Wir können eine vollständig warnungsfreie Installation für jede Android-Version und jede Gerätekonfiguration nicht garantieren. Wir stellen die Schutzmechanismen nicht ab, um eine solche Zusage scheinbar zu erfüllen.

## Welchen Verteilungsweg wir empfehlen

Für unseren kleinen Testkreis empfehlen wir einen **internen Test in Google Play**. Wir verteilen dabei über den Play Store an bis zu 100 eingeladene Tester, ohne die App bereits öffentlich anbieten zu müssen. Damit umgehen wir die übliche Freigabe für unbekannte APK-Quellen. Eine Testkennzeichnung oder normale Kameraberechtigungsabfrage ist weiterhin möglich; daraus folgt keine Sicherheitswarnung.

Für die Play-Verteilung gehen wir so vor:

1. Wir richten ein Play-Console-Entwicklerkonto ein beziehungsweise verwenden ein vorhandenes verifiziertes Konto.
2. Wir legen die dauerhafte Paketidentität und den Übergang von **SeaFrogs Test** fest.
3. Wir erzeugen und sichern einen eigenen Release-/Upload-Schlüssel und konfigurieren Play App Signing. Wir halten private Schlüssel und Passwörter außerhalb des Repositorys.
4. Wir bauen ein signiertes Release-App-Bundle, prüfen dessen Kamera-, HID-, Speicher- und JNI-Verhalten und legen einen internen Testrelease an.
5. Wir tragen die vorgesehenen Tester ein. Sie treten über den Testlink bei und installieren beziehungsweise aktualisieren über Google Play.

Wir haben den Produktionsschlüssel und den signierten Release-Build vorbereitet. Wir behalten in RC2 Ziel-API 35 bei; vor einer neuen Play-Einreichung migrieren und prüfen wir Ziel-API 36 gemäß [Googles aktueller Vorgabe](https://support.google.com/googleplay/android-developer/answer/11926878). Wir haben noch kein Play-Konto eingerichtet und keinen Play-Release veröffentlicht.

## Wie wir den Übergang von der Test-App behandeln

Android prüft bei Updates die Paketidentität und Signaturkompatibilität. Ein einfaches Neusignieren unserer bisherigen Test-App mit einem unabhängigen neuen Schlüssel ergibt deshalb kein kompatibles Update.

Als übersichtlichen Übergang empfehlen wir unsere Produktionsidentität `de.jce.seafrogs` mit eigener Release-Signierung. Sie kann neben `de.jce.seafrogs.test` installiert werden. Wir übernehmen die gewünschten Einstellungen bewusst; die neue Paketidentität übernimmt App-Daten und Berechtigungen nicht automatisch. Unsere über MediaStore gespeicherten Bilder und Videos liegen weiterhin in Pictures/SeaFrogs und Movies/SeaFrogs; ihre Zugänglichkeit und etwaige appgebundene Referenzen prüfen wir beim Übergang.

Falls auf unserem Gerät ein alter Prototyp mit Paket `de.jce.seafrogs` und einer anderen Signatur installiert ist, sichern wir benötigte App-Daten und entfernen diesen Prototyp vor der Produktionsinstallation. Unsere aktuelle `.test`-App kann parallel bestehen bleiben.

Falls wir stattdessen die Test-Paketidentität dauerhaft behalten möchten, planen wir eine ausdrücklich geprüfte Signaturmigration oder eine Neuinstallation. Wir betrachten den vorhandenen Debug-Schlüssel nicht als dauerhafte Produktionslösung. Für einheitliche Play- und direkte APK-Updates müssten wir auch die App-Signing-Key-Strategie aufeinander abstimmen.

## Was eine direkte Release-APK verbessert

Wir können auch außerhalb von Google Play eine sauber signierte Release-APK verteilen. Damit erhalten wir eine dauerhaft kontrollierte Herausgeberidentität, können den Debug-Modus abschalten und Builds reproduzierbar veröffentlichen. Eine Release-Signatur allein beseitigt weder Androids Freigabe unbekannter Quellen noch jede Play-Protect-Prüfung. Wir versprechen deshalb auch mit einer Release-APK keine warnungsfreie Installation.

Unser Manifest verlangt Kamera und nur bis Android 9 die alte Schreibberechtigung. Wir verlangen keine Mikrofon-, Internet-, SMS- oder Bedienungshilfenberechtigung. Wir ergänzen für den Verteilungswechsel keine unnötigen Berechtigungen.

## Welche Verifizierung wir zusätzlich berücksichtigen

Nach Googles aktuellem Zeitplan gelten seit 30. September 2026 erste Schutzanforderungen für teilnehmende Stores in Brasilien, Indonesien, Singapur und Thailand auf zertifizierten Geräten ab Android 7. Die globale Erweiterung ist für 2027 und danach vorgesehen. Wir setzen diese Entwicklerverifizierung nicht mit einer Play-Protect-Unbedenklichkeitsprüfung gleich. Sie hebt auch die Quellenfreigabe einer direkten APK nicht automatisch auf.

Für eine spätere breitere Verteilung halten wir Entwickleridentität und Paketregistrierung aktuell. Für kleine private Projekte beschreibt Google außerdem Konten für begrenzte Verteilung an bis zu 20 Geräte; wir behandeln diese Möglichkeit nicht als Garantie für eine warnungsfreie APK-Installation.

## Auf welche Primärquellen wir uns stützen

- [Android: alternative Verteilung und unbekannte Quellen](https://developer.android.com/distribute/marketing-tools/alternative-distribution)
- [Google: Play-Protect-Warnungen für Entwickler](https://developers.google.com/android/play-protect/warning-dev-guidance)
- [Google: internen, geschlossenen oder offenen Test einrichten](https://support.google.com/googleplay/android-developer/answer/9845334)
- [Android: App-Signierung und kompatible Updates](https://developer.android.com/studio/publish/app-signing)
- [Android: Entwicklerverifizierung und Zeitplan](https://developer.android.com/developer-verification)
