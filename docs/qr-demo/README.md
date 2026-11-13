# Kody QR do demo

Kody pozwalają sprawdzić skaner FE-08 w trybie `MOCK` na urządzeniu z aparatem. Wyświetl obraz na drugim ekranie albo go wydrukuj, a potem w aplikacji wybierz **Kasetka → Dodaj kasetkę**. [FE-08](https://github.com/az2kxx/fe-medication-organizer/issues/8)

| Plik | Wartość QR | Oczekiwany wynik |
| --- | --- | --- |
| [elc-7x2-demo.png](elc-7x2-demo.png) | `ELCOMP:ELC-7X2-DEMO` | Szczegóły kasetki demonstracyjnej 7×2 |
| [elc-7x3.png](elc-7x3.png) | `ELCOMP:ELC-7X3` | Szczegóły kasetki tygodniowej 7×3 |
| [elc-1x4.png](elc-1x4.png) | `ELCOMP:ELC-1X4` | Szczegóły kasetki dziennej 1×4 |
| [elc-7x1-old.png](elc-7x1-old.png) | `ELCOMP:ELC-7X1-OLD` | Szczegóły modelu wycofanego bez możliwości wyboru |
| [unknown-model.png](unknown-model.png) | `ELCOMP:UNKNOWN-MODEL` | Komunikat „Nie rozpoznano kodu” i ponowne skanowanie |

Format `ELCOMP:<kod produktu>` jest założeniem fixture. Backend przechowuje dowolną wartość `qrCodeValue` dla modelu, a klient przesyła odczytany tekst bez interpretacji. Wartości demo są zdefiniowane w [DemoCatalog.kt](../../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/core/demo/DemoCatalog.kt).

Obrazy wygenerowano przez CoreImage (`CIQRCodeGenerator`, poziom korekcji M) i zweryfikowano ponownym odczytem przez `CIDetector`.
