# Architektura frontendu

Frontend EL COMP to aplikacja mobilna Kotlin Multiplatform z interfejsem Compose Multiplatform dla Androida i iOS. Obie platformy uruchamiają tę samą konfigurację lokalną, ekran Dzisiaj i menu głównych sekcji. [FE-04](https://github.com/az2kxx/fe-medication-organizer/issues/4)

## Moduły i źródła

| Katalog | Odpowiedzialność |
| --- | --- |
| `composeApp/src/commonMain/` | Wspólny ekran, stan UI, logika i interfejsy repozytoriów. Kod Kotlin używa pakietu bazowego `com.elcomp.medicationorganizer`. |
| `composeApp/src/androidMain/` | Implementacje wspólnych interfejsów zależne od Androida. |
| `composeApp/src/iosMain/` | Implementacje wspólnych interfejsów zależne od iOS i punkt wejścia dla hosta SwiftUI. |
| `androidApp/` | Host Androida, manifest i aktywność startowa, która wyświetla wspólne UI. |
| `iosApp/` | Host SwiftUI i projekt Xcode, który osadza framework Kotlin. |

Punkt wejścia do wspólnego UI znajduje się w pakiecie `app`. Przy kolejnych zadaniach dodawajcie pakiety, gdy pojawi się kod, który do nich należy. `app` scala zależności i zawiera nawigację. `core/ui` gromadzi współdzielone komponenty i motyw. `core/network` zawiera wspólną konfigurację transportu, autoryzacji i obsługi błędów. `core/demo` przechowuje fikcyjne dane i stan demonstracyjny.

Konfiguracja zapisuje nazwę wyświetlaną w lokalnym stanie interfejsu i otwiera Dzisiaj. Menu prowadzi do Dzisiaj, Kasetki, Leków, Przygotowania, Historii i Ustawień. Ustawienia prowadzą do edycji lokalnej nazwy. Nawigacja i konfiguracja działają niezależnie od trybu transportu.

Wspólne komponenty w `core/ui` obsługują pola z błędami, blokadę ponownego wysłania formularza podczas zapisu, a także stany ładowania, pustej listy i błędu z akcją ponowienia. Tekst i semantyka nadają znaczenie statusom, a ikony wspierają ich odczyt. Napisy oraz etykiety dostępności są w zasobach Compose z wersjami PL i EN.

Kod każdej funkcji trzymajcie pod `features/<feature>/`. `presentation` zawiera ekrany, ViewModele i stan UI. `domain` zawiera modele używane przez aplikację i interfejsy repozytoriów. `data` mapuje DTO i implementuje repozytoria. Aparat, powiadomienia oraz bezpieczny zapis tokenu trafiają do kodu platformowego, gdy dana funkcja ich potrzebuje.

## Dane i praca równoległa

Ekran przekazuje zdarzenia do ViewModelu i renderuje stan UI. ViewModel korzysta z interfejsu repozytorium, a warstwa `data` dostarcza jego implementację. Host wybiera `ApiSettings` przy starcie i tworzy `AppServices`. Ta sama implementacja repozytorium rejestracji i logowania używa wspólnego klienta Ktor w obu trybach.

Rejestracja i logowanie używają DTO zgodnych z kodem backendu. W `MOCK` na żądania odpowiada Ktor `MockEngine`, a `LIVE` kieruje je do skonfigurowanego adresu API. Robocze operacje leków, planu, przygotowania i historii korzystają ze wspólnego stanu demo w `MOCK`. [Kontrakt API](api-contract.md)

Uzgodnijcie interfejs repozytorium przed równoległą implementacją UI i transportu. Podłączcie kolejne ekrany do jednej instancji `AppServices`, aby ponowny odczyt planu po zmianie leku lub historii po potwierdzeniu dawki pokazywał aktualny stan demo.

Komendy uruchomienia i wymagane SDK są w README. [Uruchomienie projektu](../README.md)
