# Medication Organizer

Mobilny frontend kasetki EL COMP. Android i iOS korzystają ze wspólnego powitania, ekranu Dzisiaj i dolnych zakładek napisanych w Kotlin i Compose Multiplatform.

## Środowisko

- JDK 21 i Gradle Wrapper dołączony do repozytorium.
- Android Studio oraz Android SDK Platform 36 i Build Tools 35.0.0. Aplikacja obsługuje Androida od API 24.
- macOS na Apple Silicon oraz Xcode 26.2 do pracy nad iOS. Aplikacja obsługuje iOS od wersji 16 na urządzeniach i symulatorach arm64.

## Android

Otwórz katalog repozytorium w Android Studio, ustaw JDK 21 jako Gradle JDK i wskaż lokalny Android SDK. Uruchom konfigurację `androidApp` na emulatorze lub urządzeniu. W terminalu ustaw `JAVA_HOME` na JDK 21 i `ANDROID_HOME` na katalog Android SDK.

```sh
./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest :composeApp:testAndroidHostTest
```

APK pojawi się w `androidApp/build/outputs/apk/debug/`. Testy `androidApp` obejmują rejestrację i logowanie w demo, błędne hasło, konflikt adresu, wylogowanie i powrót do konta. Sprawdzają też nawigację w PL i EN, walidację profilu i dostęp do zakładek przy skali tekstu 200%. Testy hosta w `composeApp` sprawdzają kontrakt auth, sesję, transport oraz przebieg demo.

## iOS

Otwórz `iosApp/iosApp.xcodeproj`, wybierz schemat `iosApp` i uruchom go na symulatorze iPhone’a. Faza budowania w Xcode kompiluje framework Kotlin. Dla kompilacji z terminala ustaw `JAVA_HOME` i użyj Xcode 26.2.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/xcode CODE_SIGNING_ALLOWED=NO build
```

Polecenie sprawdza kompilację aplikacji i frameworka dla symulatora. Do testu sesji w `LIVE` uruchom aplikację poleceniem **Run** w Xcode. Przy budowaniu z terminala dla symulatora użyj `CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-`, aby aplikacja miała dostęp do Keychain. Testy transportu i demo uruchomisz poleceniem `:composeApp:testAndroidHostTest` z sekcji Android. Jeśli uruchamiasz aplikację na urządzeniu, wybierz swój zespół w ustawieniach podpisywania Xcode.

### Nawigacja i dostępność na iOS

1. Uruchom schemat `iosApp` w konfiguracji Debug na symulatorze. Zostaw domyślny tryb `MOCK`.
2. Wybierz **Wypróbuj demo**, aby zalogować się na fikcyjne konto i otworzyć Dzisiaj. Przejdź do kasetki główną akcją, otwórz Przygotowanie i wróć do Kasetki. Otwórz Historię dolną zakładką i wróć zakładką Dzisiaj.
3. Z Dzisiaj otwórz Ustawienia i edycję profilu lokalnego. Spróbuj zapisać pustą nazwę i sprawdź komunikat przy polu. Wpisz fikcyjną nazwę, sprawdź wynik w Ustawieniach i wyloguj się. Zaloguj się ponownie danymi demo.
4. Powtórz przebieg w polskiej i angielskiej wersji językowej symulatora. Zwiększ tekst w ustawieniach dostępności i sprawdź, czy formularz, główne przyciski i cztery zakładki pozostają dostępne.
5. Na iPhonie włącz VoiceOver w `Ustawienia → Dostępność → VoiceOver`. Sprawdź kolejność fokusu i etykiety pól logowania, nazwy zakładek i odczyt wybranej zakładki oraz odczyt błędu przy polu. W symulatorze sprawdzisz etykiety i role przez Accessibility Inspector. [Testowanie dostępności w aplikacji](https://developer.apple.com/documentation/accessibility/performing-accessibility-testing-for-your-app)

## Tryby pracy

Aplikacja domyślnie uruchamia się w `MOCK` ze scenariuszem `NORMAL`. Interfejs pokazuje aktywny tryb oraz nazwę scenariusza, jeśli wybrano inny niż domyślny. W `MOCK` możesz wypróbować fikcyjne konto lub użyć formularzy rejestracji i logowania. W `LIVE` **Zacznij** otwiera logowanie do wskazanego backendu. Leki, plan, przygotowanie i historia są dostępne w roboczym repozytorium `MOCK`. [Kontrakt API](docs/api-contract.md)

Sesja `MOCK` istnieje w pamięci procesu. `LIVE` przechowuje token i czas wygaśnięcia w bezpiecznym magazynie platformy. Po ponownym uruchomieniu aplikacja może przywrócić ważną sesję dla tego samego adresu API. Wylogowanie, wygaśnięcie sesji lub odpowiedź `401` z chronionej trasy kieruje do logowania. [Obsługa sesji](docs/architecture.md#obsługa-sesji)

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

Wybierz **Wypróbuj demo**, aby od razu zalogować się na fikcyjne konto. Jeśli otworzysz formularz **Zaloguj się**, użyj `alex@example.com` i hasła `example-password`. Aby sprawdzić rejestrację, załóż konto z nowym adresem w domenie `example.com`, `example.org` lub `example.net`. Po rejestracji aplikacja otworzy logowanie z uzupełnionym adresem e-mail. W `LIVE` użyj konta z danego środowiska backendu.

Testy oraz kod funkcji mogą tworzyć `AppServices.create(ApiSettings.Mock())` lub podać scenariusz w `ApiSettings.Mock(scenario)`.

| Scenariusz | Zachowanie transportu demo |
| --- | --- |
| `NORMAL` | Początkowe dane demonstracyjne i działające mutacje. |
| `EMPTY` | Puste listy leków, dawek i historii. Kasetka pozostaje dostępna do dodania planu. |
| `SLOW` | Każda odpowiedź jest opóźniona o 750 ms. |
| `OFFLINE` | Każde żądanie daje bezpieczny błąd sieci `NETWORK`. |
| `API_ERROR` | Każde żądanie zwraca HTTP `503`, które klient mapuje na `SERVER`. |

W `NORMAL` zaloguj fikcyjne konto przez `services.signIn(LoginRequest(...))` przed odczytem planu z `services.demo`. Dla `DEMO_DATE` (`2026-11-02`) i `DEMO_ORGANIZER_ID` (`1`) dodaj lek, przypisz mu dawkę o 20:00 i odczytaj plan ponownie. Przygotuj przegródki zgodnie z planem, oznacz przyjęcie jednej dawki i pominięcie drugiej. Kolejny odczyt historii pokaże obie deklaracje.

Demo zachowuje zmiany w pamięci danej instancji `AppServices`. Wylogowanie przywraca robocze dane do stanu początkowego i pozwala ponownie zalogować się na konto utworzone w bieżącej instancji. `resetDemo()` przywraca także początkową listę kont. Ponowne uruchomienie aplikacji tworzy świeżą instancję, a stała data demo pozwala odtworzyć ten sam plan. Instancję utworzoną bezpośrednio w teście zwolnij po sprawdzeniu przez `close()`.

## Praca nad zadaniami

Utwórz osobną gałąź z aktualnego `main` dla każdego issue. W PR opisz efekt, wykonane sprawdzenia i zależności. GitHub Actions buduje Android APK, uruchamia test startowy i testy hosta oraz kompiluje aplikację na symulator iOS.

Wspólna nawigacja zaczyna się w `App()` w `composeApp/src/commonMain`. Elementy dostępnego UI z `core/ui` można wykorzystać w kolejnych ekranach. [Architektura frontendu](docs/architecture.md) · [Zasady pracy dla zespołu, Codexa i Claude’a](docs/working-agreement.md)
