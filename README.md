# Medication Organizer

Mobilny frontend kasetki EL COMP. Android i iOS uruchamiają ten sam ekran startowy napisany w Kotlin i Compose Multiplatform.

## Środowisko

- JDK 21 i Gradle Wrapper dołączony do repozytorium.
- Android Studio oraz Android SDK Platform 36 i Build Tools 35.0.0. Aplikacja obsługuje Androida od API 24.
- macOS na Apple Silicon oraz Xcode 26.2 do pracy nad iOS. Aplikacja obsługuje iOS od wersji 16 na urządzeniach i symulatorach arm64.

## Android

Otwórz katalog repozytorium w Android Studio, ustaw JDK 21 jako Gradle JDK i wskaż lokalny Android SDK. Uruchom konfigurację `androidApp` na emulatorze lub urządzeniu. W terminalu ustaw `JAVA_HOME` na JDK 21 i `ANDROID_HOME` na katalog Android SDK.

```sh
./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest
```

APK pojawi się w `androidApp/build/outputs/apk/debug/`. Test uruchamia aktywność Androida i sprawdza, czy wyświetliła wspólny ekran Compose.

## iOS

Otwórz `iosApp/iosApp.xcodeproj`, wybierz schemat `iosApp` i uruchom go na symulatorze iPhone’a. Faza budowania w Xcode kompiluje framework Kotlin. Dla kompilacji z terminala ustaw `JAVA_HOME` i użyj Xcode 26.2.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/xcode CODE_SIGNING_ALLOWED=NO build
```

Jeśli uruchamiasz aplikację na urządzeniu, wybierz swój zespół w ustawieniach podpisywania Xcode.

## Tryby pracy

Ekran FE-01 uruchamia się lokalnie bez dodatkowej konfiguracji i bez usług backendowych. FE-02 przygotuje DTO i przykładowe payloady. FE-03 doda wybór transportu `MOCK` lub `LIVE` oraz adres API. Zasady obu trybów opisuje kontrakt. [Kontrakt API](docs/api-contract.md)

## Praca nad zadaniami

Utwórz osobną gałąź z aktualnego `main` dla każdego issue. W PR opisz efekt, wykonane sprawdzenia i zależności. GitHub Actions buduje Android APK, uruchamia test startowy i kompiluje aplikację na symulator iOS.

FE-04 zaczyna od wspólnego `App()` w `composeApp/src/commonMain`. Tam powstanie nawigacja i tam będą podłączane współdzielone komponenty. [Architektura frontendu](docs/architecture.md) · [Zasady pracy dla zespołu, Codexa i Claude’a](docs/working-agreement.md) · [FE-04](https://github.com/az2kxx/fe-medication-organizer/issues/4)
