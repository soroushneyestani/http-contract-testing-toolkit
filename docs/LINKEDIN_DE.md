# LinkedIn-Beitrag

Ein API-Endpunkt kann weiterhin `200 OK` liefern und trotzdem einen Client brechen. Ein Beispiel ist eine Produktantwort, die eine numerische ID liefert und den erwarteten Preis weglässt.

Für Version `0.1.0` des Contract Testing Toolkit habe ich zwei Prüfpfade in Java umgesetzt. Der erste vergleicht die Anforderungen eines Consumers mit den deklarierten Zusagen eines Providers. Der zweite sendet eine echte HTTP-Anfrage an einen synthetischen lokalen Provider und prüft Status und JSON-Felder der Antwort.

Die Demo prüft denselben Vertrag an der Ausgangsversion, einer inkompatiblen Änderung, einer kompatiblen Erweiterung und der wiederhergestellten Ausgangsversion. Ein geänderter Feldtyp und ein fehlendes Pflichtfeld führen zu einem negativen Ergebnis. Zusätzliche Felder dürfen hinzukommen. Optionale Felder werden geprüft, wenn sie vorhanden sind.

Definierte CLI-Exitcodes unterscheiden Vertragsverletzungen von Einrichtungs- und Transportfehlern. JUnit erzeugt XML-Berichte für die automatisierte Auswertung.

#Java #ContractTesting #SoftwareTesting #BackendEngineering
