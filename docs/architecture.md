# Architektura frontendu

Frontend EL COMP to aplikacja mobilna Kotlin Multiplatform z interfejsem Compose Multiplatform dla Androida i iOS. FE-01 dostarcza wspólny ekran startowy i konfigurację CI. Nawigacja oraz wspólne komponenty należą do FE-04. [FE-01](https://github.com/az2kxx/fe-medication-organizer/issues/1) · [FE-04](https://github.com/az2kxx/fe-medication-organizer/issues/4)

## Moduły i źródła

| Katalog | Odpowiedzialność |
| --- | --- |
| `composeApp/src/commonMain/` | Wspólny ekran, stan UI, logika i interfejsy repozytoriów. Kod Kotlin używa pakietu bazowego `com.elcomp.medicationorganizer`. |
| `composeApp/src/androidMain/` | Implementacje wspólnych interfejsów zależne od Androida. |
| `composeApp/src/iosMain/` | Implementacje wspólnych interfejsów zależne od iOS i punkt wejścia dla hosta SwiftUI. |
| `androidApp/` | Host Androida, manifest i aktywność startowa, która wyświetla wspólne UI. |
| `iosApp/` | Host SwiftUI i projekt Xcode, który osadza framework Kotlin. |

Punkt wejścia do wspólnego UI znajduje się w pakiecie `app`. Przy kolejnych zadaniach dodawajcie pakiety, gdy pojawi się kod, który do nich należy. `app` scala zależności i zawiera nawigację. `core/ui` gromadzi współdzielone komponenty i motyw. `core/network` zawiera wspólną konfigurację transportu, autoryzacji i obsługi błędów. `core/demo` przechowuje fikcyjne dane i stan demonstracyjny.

Kod każdej funkcji trzymajcie pod `features/<feature>/`. `presentation` zawiera ekrany, ViewModele i stan UI. `domain` zawiera modele używane przez aplikację i interfejsy repozytoriów. `data` mapuje DTO i implementuje repozytoria. Aparat, powiadomienia oraz bezpieczny zapis tokenu trafiają do kodu platformowego, gdy dana funkcja ich potrzebuje.

## Dane i praca równoległa

Ekran przekazuje zdarzenia do ViewModelu i renderuje stan UI. ViewModel korzysta z interfejsu repozytorium, a warstwa `data` dostarcza jego implementację. Wybór transportu odbywa się przy składaniu zależności w `app`. Oba tryby korzystają z tych samych ekranów i modeli domenowych.

FE-02 ustala DTO i przygotowuje przykładowe żądania oraz odpowiedzi. FE-03 dodaje klienta HTTP, stanowe mocki i przełącznik `MOCK`/`LIVE`. Do tego czasu ekran FE-01 uruchamia się lokalnie bez backendu. Szczegóły payloadów i pytania do backendu są w kontrakcie. [Kontrakt API](api-contract.md) · [FE-02](https://github.com/az2kxx/fe-medication-organizer/issues/2) · [FE-03](https://github.com/az2kxx/fe-medication-organizer/issues/3)

Uzgodnijcie interfejs repozytorium przed równoległą implementacją UI i transportu. Po zmianie leku mock powinien odświeżyć plan, a po potwierdzeniu przyjęcia dawki uaktualnić historię. Dzięki temu obie osoby mogą sprawdzić ten sam scenariusz na danych demonstracyjnych.

Komendy uruchomienia i wymagane SDK są w README. [Uruchomienie projektu](../README.md)
