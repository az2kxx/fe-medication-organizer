# Medication Organizer

Mobilny frontend kasetki EL COMP. Android i iOS korzystają ze wspólnej konfiguracji lokalnej, ekranu Dzisiaj i menu napisanych w Kotlin i Compose Multiplatform.

## Środowisko

- JDK 21 i Gradle Wrapper dołączony do repozytorium.
- Android Studio oraz Android SDK Platform 36 i Build Tools 35.0.0. Aplikacja obsługuje Androida od API 24.
- macOS na Apple Silicon oraz Xcode 26.2 do pracy nad iOS. Aplikacja obsługuje iOS od wersji 16 na urządzeniach i symulatorach arm64.

## Android

Otwórz katalog repozytorium w Android Studio, ustaw JDK 21 jako Gradle JDK i wskaż lokalny Android SDK. Uruchom konfigurację `androidApp` na emulatorze lub urządzeniu. W terminalu ustaw `JAVA_HOME` na JDK 21 i `ANDROID_HOME` na katalog Android SDK.

```sh
./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest :composeApp:testAndroidHostTest
```

APK pojawi się w `androidApp/build/outputs/apk/debug/`. Testy `androidApp` sprawdzają start w PL i EN, walidację, nawigację, dostęp do przycisku przy skali tekstu 200% i blokadę ponownego wysłania podczas zapisu. Testy hosta w `composeApp` sprawdzają serializację auth, transport oraz przebieg demo.

## iOS

Otwórz `iosApp/iosApp.xcodeproj`, wybierz schemat `iosApp` i uruchom go na symulatorze iPhone’a. Faza budowania w Xcode kompiluje framework Kotlin. Dla kompilacji z terminala ustaw `JAVA_HOME` i użyj Xcode 26.2.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/xcode CODE_SIGNING_ALLOWED=NO build
```

Polecenie sprawdza kompilację aplikacji i frameworka dla symulatora. Testy transportu i demo uruchomisz poleceniem `:composeApp:testAndroidHostTest` z sekcji Android. Jeśli uruchamiasz aplikację na urządzeniu, wybierz swój zespół w ustawieniach podpisywania Xcode.

### Konfiguracja i dostępność na iOS

1. Uruchom schemat `iosApp` w konfiguracji Debug na symulatorze. Zostaw domyślny tryb `MOCK`.
2. Wyślij pustą nazwę w konfiguracji lokalnej. Sprawdź komunikat przy polu. Wpisz fikcyjną nazwę i przejdź do Dzisiaj. Otwórz menu, przejdź do Historii i wróć do Dzisiaj.
3. Sprawdź ten przebieg po zmianie języka symulatora na polski i angielski. Przy większym tekście w ustawieniach dostępności przewiń formularz i menu. Sprawdź, czy etykiety są czytelne i czy da się dosięgnąć głównych przycisków.
4. Na iPhonie włącz VoiceOver w `Ustawienia → Dostępność → VoiceOver`. Przejdź przez formularz i menu gestami VoiceOver. Sprawdź kolejność fokusu, odczyt nazwy pola i błędu, nazwy akcji otwarcia i zamknięcia menu oraz powrót do Dzisiaj. W symulatorze możesz sprawdzić etykiety i role przez Accessibility Inspector. [Testowanie dostępności w aplikacji](https://developer.apple.com/documentation/accessibility/performing-accessibility-testing-for-your-app)

## Tryby pracy

Aplikacja domyślnie uruchamia się w `MOCK` ze scenariuszem `NORMAL`. Interfejs pokazuje aktywny tryb oraz nazwę scenariusza, jeśli wybrano inny niż domyślny. Konfiguracja nazwy i przejście do Dzisiaj korzystają z lokalnego stanu aplikacji. Warstwa transportu w `LIVE` udostępnia rejestrację i logowanie dla kolejnych funkcji. Leki, plan, przygotowanie i historia są dostępne w roboczym repozytorium `MOCK`. [Kontrakt API](docs/api-contract.md)

Parametry startowe poniżej działają w konfiguracji `Debug`. W `LIVE` podaj sam origin, np. `https://api.example.com`. Repozytorium dodaje ścieżkę `/api`. Z lokalnym backendem połączysz się przez HTTP pod adresami z przykładów. Dla zdalnego backendu użyj HTTPS.

### Konfiguracja Androida

Uruchom emulator i zainstaluj build debug. Każde polecenie `adb` uruchamia aplikację z własnym zestawem parametrów.

```sh
./gradlew :androidApp:installDebug

adb shell am start -S -n com.elcomp.medicationorganizer/.MainActivity \
  --es api_mode MOCK --es mock_scenario API_ERROR

adb shell am start -S -n com.elcomp.medicationorganizer/.MainActivity \
  --es api_mode LIVE --es api_base_url http://10.0.2.2:8080
```

`10.0.2.2` to adres hosta widziany przez emulator Androida. Przykład `LIVE` zakłada lokalny backend na porcie 8080.

### Konfiguracja iOS

W Xcode otwórz `Edit Scheme → Run → Arguments → Environment Variables` dla schematu `iosApp`. Ustaw zmienne dla jednego trybu i uruchom aplikację na symulatorze.

| Tryb | Aktywne zmienne |
| --- | --- |
| `MOCK` z błędem API | `ELCOMP_API_MODE=MOCK` i `ELCOMP_MOCK_SCENARIO=API_ERROR` |
| `LIVE` z lokalnym backendem | `ELCOMP_API_MODE=LIVE` i `ELCOMP_API_BASE_URL=http://127.0.0.1:8080` |

Przy zmianie trybu odznacz `ELCOMP_API_BASE_URL` dla `MOCK` lub `ELCOMP_MOCK_SCENARIO` dla `LIVE`. W razie błędnej konfiguracji aplikacja pokazuje komunikat i uruchamia lokalny `MOCK`.

### Przebieg demo

Testy oraz kod funkcji mogą tworzyć `AppServices.create(ApiSettings.Mock())` lub podać scenariusz w `ApiSettings.Mock(scenario)`. `services.auth` obsługuje fikcyjne konto `alex@example.com` z hasłem `example-password`. Rejestracja demo przyjmuje adresy z domen `example.com`, `example.org` i `example.net`. W `LIVE` użyj konta z danego środowiska backendu.

| Scenariusz | Zachowanie transportu demo |
| --- | --- |
| `NORMAL` | Początkowe dane demonstracyjne i działające mutacje. |
| `EMPTY` | Puste listy leków, dawek i historii. Kasetka pozostaje dostępna do dodania planu. |
| `SLOW` | Każda odpowiedź jest opóźniona o 750 ms. |
| `OFFLINE` | Każde żądanie daje bezpieczny błąd sieci `NETWORK`. |
| `API_ERROR` | Każde żądanie zwraca HTTP `503`, które klient mapuje na `SERVER`. |

W `NORMAL` odczytaj plan przez `services.demo` dla `DEMO_DATE` (`2026-11-02`) i `DEMO_ORGANIZER_ID` (`1`). Dodaj lek, przypisz mu dawkę o 20:00 i odczytaj plan ponownie. Przygotuj przegródki zgodnie z planem, oznacz przyjęcie jednej dawki i pominięcie drugiej. Kolejny odczyt historii pokaże obie deklaracje.

Demo zachowuje zmiany w pamięci danej instancji `AppServices`. `resetDemo()` przywraca początkowe konta i dane dla wybranego scenariusza. Ponowne uruchomienie aplikacji tworzy świeżą instancję, a stała data demo pozwala odtworzyć ten sam plan. Instancję utworzoną bezpośrednio w teście zwolnij po sprawdzeniu przez `close()`.

## Praca nad zadaniami

Utwórz osobną gałąź z aktualnego `main` dla każdego issue. W PR opisz efekt, wykonane sprawdzenia i zależności. GitHub Actions buduje Android APK, uruchamia test startowy i testy hosta oraz kompiluje aplikację na symulator iOS.

Wspólna nawigacja zaczyna się w `App()` w `composeApp/src/commonMain`. Elementy dostępnego UI z `core/ui` można wykorzystać w kolejnych ekranach. [Architektura frontendu](docs/architecture.md) · [Zasady pracy dla zespołu, Codexa i Claude’a](docs/working-agreement.md)
