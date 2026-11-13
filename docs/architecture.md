# Architektura frontendu

Frontend EL COMP to aplikacja mobilna Kotlin Multiplatform z interfejsem Compose Multiplatform dla Androida i iOS. Obie platformy korzystają ze wspólnego powitania, ekranu Dzisiaj i dolnej nawigacji. [FE-04](https://github.com/az2kxx/fe-medication-organizer/issues/4)

## Moduły i źródła

| Katalog | Odpowiedzialność |
| --- | --- |
| `composeApp/src/commonMain/` | Wspólny ekran, stan UI, logika i interfejsy repozytoriów. Kod Kotlin używa pakietu bazowego `com.elcomp.medicationorganizer`. |
| `composeApp/src/androidMain/` | Implementacje wspólnych interfejsów zależne od Androida. |
| `composeApp/src/iosMain/` | Implementacje wspólnych interfejsów zależne od iOS i punkt wejścia dla hosta SwiftUI. |
| `androidApp/` | Host Androida, manifest i aktywność startowa, która wyświetla wspólne UI. |
| `iosApp/` | Host SwiftUI i projekt Xcode, który osadza framework Kotlin. |

Punkt wejścia do wspólnego UI znajduje się w pakiecie `app`. Przy kolejnych zadaniach dodawajcie pakiety, gdy pojawi się kod, który do nich należy. `app` scala zależności i zawiera nawigację. `core/ui` gromadzi współdzielone komponenty i motyw. `core/network` zawiera wspólną konfigurację transportu, autoryzacji i obsługi błędów. `core/demo` przechowuje fikcyjne dane i stan demonstracyjny.

Powitanie w `MOCK` pozwala zalogować fikcyjne konto skrótem **Wypróbuj demo** lub przejść do formularzy konta. W `LIVE` **Zacznij** otwiera logowanie. Po zalogowaniu dolne zakładki prowadzą do Dzisiaj, Kasetki, Leków i Historii. Przygotowanie, zarządzanie przypisaniami i wybór modelu są podstronami Kasetki. Wybór prowadzi przez katalog, szczegóły modelu i formularz przypisania. Ustawienia otwiera się z Dzisiaj. W `MOCK` pokazują profil i preferencje zalogowanego konta. Wylogowanie działa w obu trybach. [FE-07](https://github.com/az2kxx/fe-medication-organizer/issues/7) · [FE-09](https://github.com/az2kxx/fe-medication-organizer/issues/9)

Wspólne komponenty w `core/ui` obsługują pola z błędami, blokadę ponownego wysłania formularza podczas zapisu, a także stany ładowania, pustej listy i błędu z akcją ponowienia. Tekst i semantyka nadają znaczenie statusom, a ikony wspierają ich odczyt. Napisy oraz etykiety dostępności są w zasobach Compose z wersjami PL i EN.

Kod każdej funkcji trzymajcie pod `features/<feature>/`. `presentation` zawiera ekrany, ViewModele i stan UI. `domain` zawiera modele używane przez aplikację i interfejsy repozytoriów. `data` mapuje DTO i implementuje repozytoria. Aparat, powiadomienia oraz bezpieczny zapis tokenu trafiają do kodu platformowego, gdy dana funkcja ich potrzebuje.

## Dane i praca równoległa

Ekran przekazuje zdarzenia do ViewModelu i renderuje stan UI. ViewModel korzysta z interfejsu repozytorium, a warstwa `data` dostarcza jego implementację. Host wybiera `ApiSettings` przy starcie i tworzy `AppServices`. Ta sama implementacja repozytorium rejestracji i logowania używa wspólnego klienta Ktor w obu trybach.

Rejestracja i logowanie używają DTO zgodnych z kodem backendu. W `MOCK` na żądania odpowiada Ktor `MockEngine`, a `LIVE` kieruje je do skonfigurowanego adresu API. Robocze operacje profilu, preferencji, leków, planu, przygotowania i historii korzystają ze wspólnego stanu demo w `MOCK`. [Kontrakt API](api-contract.md)

Uzgodnijcie interfejs repozytorium przed równoległą implementacją UI i transportu. Podłączcie kolejne ekrany do jednej instancji `AppServices`, aby ponowny odczyt planu po zmianie leku lub historii po potwierdzeniu dawki pokazywał aktualny stan demo.

Komendy uruchomienia i wymagane SDK są w README. [Uruchomienie projektu](../README.md)

## Profil i dostępność

`ProfileRepository` korzysta z roboczych tras chronionych sesją. Formularz zmienia wyłącznie nazwę wyświetlaną. Zapis preferencji przesyła razem tryb uproszczony, skalę tekstu i wysoki kontrast. Wspólny motyw stosuje kontrast i mnożnik typografii aplikacji, a system nadal przelicza jednostki tekstu zgodnie ze swoją skalą. Tryb uproszczony na ekranie Dzisiaj skupia widok na dawce z planu i głównej akcji do kasetki.

`DemoPreferencesStore` trzyma jeden rekord preferencji wbudowanego konta demo. Android używa prywatnych `SharedPreferences`, a iOS `UserDefaults` aplikacji. Zmiany nazwy i konta utworzone w demo są dostępne w pamięci tej samej instancji `AppServices`. Ustawienia języka, strefy czasowej, dźwięku i wibracji pozostają lokalne lub systemowe. [FE-06](https://github.com/az2kxx/fe-medication-organizer/issues/6)

## Obsługa sesji

Rejestracja tworzy konto i kieruje do logowania z uzupełnionym adresem e-mail. Dopiero logowanie otwiera sesję. Klient wylicza jej wygaśnięcie z `expiresInSeconds` i dodaje `Authorization: Bearer` do żądań wymagających sesji. W trybie `MOCK` fikcyjny token trafia wyłącznie do lokalnego transportu.

`MOCK` przechowuje sesję w pamięci. `LIVE` zapisuje token, czas wygaśnięcia i adres API. Android szyfruje rekord kluczem z Android Keystore i trzyma go w katalogu pomijanym przez kopie zapasowe. iOS zapisuje go w Keychain jako dane dostępne na odblokowanym urządzeniu. Aplikacja przywraca ważną sesję dla tego samego adresu API.

Wylogowanie, wygaśnięcie i odpowiedź `401` z chronionej trasy kończą sesję. Interfejs wraca do logowania i usuwa dane poprzedniej sesji z widoku. Błędne hasło na ekranie logowania pokazuje komunikat w formularzu. Kolejna sesja zaczyna się od ponownego logowania i odczytu danych bieżącego konta. [Kontrakt API](api-contract.md)

Planowanie lokalnych powiadomień należy do FE-18. Integracja powinna anulować powiadomienia bieżącego użytkownika po zakończeniu jego sesji. [FE-18](https://github.com/az2kxx/fe-medication-organizer/issues/18)
