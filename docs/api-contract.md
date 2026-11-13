# Kontrakt API frontendu MVP

Żądania i odpowiedzi sukcesu dla rejestracji i logowania odpowiadają kodowi backendu z commita `47f51ad367d9847508a0b079a03aa52bd8fc813a`. Trasy i przykłady w sekcjach roboczych pochodzą z projektu i służą do pracy nad mockami. [Commit backendu](https://github.com/Matt1479/medication-organizer/commit/47f51ad367d9847508a0b079a03aa52bd8fc813a) · [Projekt kontraktu](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/docs/design.md#4-api-contract) · [FE-02](https://github.com/az2kxx/fe-medication-organizer/issues/2)

## Tryby pracy

Oba tryby używają tych samych DTO rejestracji i logowania. Tryb, scenariusz demo i adres API ustawia się przy uruchomieniu aplikacji. [Uruchomienie projektu](../README.md#tryby-pracy)

| Tryb docelowy | Skąd pochodzą dane |
| --- | --- |
| `MOCK` | Lokalny transport obsługuje rejestrację i logowanie z fikcyjnymi danymi, profil i preferencje bieżącego konta, kasetki oraz robocze operacje planu, leków, przygotowania i historii. |
| `LIVE` | Repozytorium wysyła rejestrację i logowanie do skonfigurowanego backendu. Dla kolejnych tras najpierw potwierdzamy kontrakt w kodzie i zachowanie odpowiedzi. |

## Potwierdzony kontrakt rejestracji i logowania

Bazowa ścieżka to `/api`. Backend zezwala na żądania do `/api/auth/**` bez sesji. Pozostałe żądania wymagają uwierzytelnienia przez `Authorization: Bearer <token>`. [Konfiguracja zabezpieczeń](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/config/SecurityConfig.java#L40-L51)

W `MOCK` publiczne są logowanie, rejestracja i odczyt katalogu modeli kasetek. Robocze trasy przypisań kasetek, profilu, preferencji, leków, planu, przygotowania i deklaracji przyjęcia wymagają ważnej sesji demo. Klient używa odpowiedzi `401` z chronionej trasy jako sygnału do zakończenia sesji. [Obsługa sesji](architecture.md#obsługa-sesji)

### Rejestracja `POST /api/auth/register`

Żądanie

```json
{
  "email": "demo@example.com",
  "password": "demo-password",
  "displayName": "Ola"
}
```

Odpowiedź `200 OK`

```json
{
  "id": 1,
  "email": "demo@example.com",
  "displayName": "Ola",
  "enabled": true,
  "createdAt": "2026-10-30T08:00:00Z",
  "updatedAt": "2026-10-30T08:00:00Z",
  "roles": ["USER"]
}
```

`email` i `password` są wymagane. `displayName` może być pominięte lub przekazane jako `null`. Odpowiedź użytkownika bez nazwy ma `"displayName": null`. Kotlin dekoduje `id` jako `Long`, oba znaczniki czasu jako `Instant` i wartości tablicy `roles` jako enum `USER` lub `ADMIN`. Po rejestracji klient przechodzi do logowania, aby otrzymać token. [Kontroler rejestracji](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/auth/AuthController.java#L48-L54) · [RegisterRequest](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/auth/dto/RegisterRequest.java#L7-L19) · [UserResponse](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/users/dto/UserResponse.java#L8-L15) · [Role](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/users/Role.java#L3-L6)

### Logowanie `POST /api/auth/login`

Żądanie

```json
{
  "email": "demo@example.com",
  "password": "demo-password"
}
```

Odpowiedź `200 OK`

```json
{
  "accessToken": "<jwt>",
  "expiresInSeconds": 3600
}
```

`expiresInSeconds` to liczba sekund typu `Long`. Wartość domyślna wynosi 3600, a backend może zmienić ją przez `JWT_TTL_MINUTES`. Odpowiedź zawiera wyłącznie token i jego czas ważności. [Kontroler logowania](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/auth/AuthController.java#L38-L46) · [LoginRequest](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/auth/dto/LoginRequest.java#L7-L16) · [TokenResponse](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/auth/dto/TokenResponse.java#L3) · [Czas ważności](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/auth/TokenService.java#L51-L53) · [Konfiguracja TTL](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/resources/application.yaml#L19-L21)

Fixture testowe korzystają z fikcyjnych danych. Ścieżki prowadzą do JSON używanych w testach serializacji DTO.

| Scenariusz | Żądanie | Odpowiedź |
| --- | --- | --- |
| Logowanie | [login-request.json](../composeApp/src/androidHostTest/resources/fixtures/auth/login-request.json) | [login-response.json](../composeApp/src/androidHostTest/resources/fixtures/auth/login-response.json) |
| Rejestracja z nazwą | [register-request.json](../composeApp/src/androidHostTest/resources/fixtures/auth/register-request.json) | [register-response.json](../composeApp/src/androidHostTest/resources/fixtures/auth/register-response.json) |
| Rejestracja bez nazwy | [register-request-no-display-name.json](../composeApp/src/androidHostTest/resources/fixtures/auth/register-request-no-display-name.json) | [register-response-null-display-name.json](../composeApp/src/androidHostTest/resources/fixtures/auth/register-response-null-display-name.json) |

### Błędy

Przy próbie rejestracji zajętego adresu e-mail serwis zgłasza `409 Conflict`. Odpowiedź na błędne logowanie i szczegóły walidacji wymagają weryfikacji. [Sprawdzenie adresu e-mail](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/users/UserService.java#L26-L33)

Robocze fixture błędów używają pól `timestamp`, `status`, `error`, `message` i `path` z `ErrorResponse` na gałęzi backendu `feat/exceptions`. Format pozostaje propozycją do czasu scalenia i weryfikacji odpowiedzi. [ErrorResponse](https://github.com/Matt1479/medication-organizer/blob/feat/exceptions/src/main/java/com/matt1479/medicationorganizer/exceptions/dto/ErrorResponse.java)

| Status | Roboczy przykład odpowiedzi |
| --- | --- |
| `400` | [Nieprawidłowe żądanie](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/error-400-response.json) |
| `401` | [Brak ważnej sesji](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/error-401-response.json) |
| `403` | [Brak uprawnień](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/error-403-response.json) |
| `404` | [Brak zasobu](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/error-404-response.json) |
| `409` | [Konflikt stanu](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/error-409-response.json) |
| `500` | [Błąd serwera](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/error-500-response.json) |

Warstwa transportu mapuje status HTTP na kategorię i lokalny komunikat `ApiException`. Działa również przy pustym body lub innym formacie odpowiedzi. `error.retryable` wskazuje, że przyczyna błędu może ustąpić. Transport wysyła każde żądanie jednokrotnie. Kod funkcji decyduje o ewentualnym ponowieniu.

## Robocze trasy do mocków

Projekt zakłada publiczny odczyt katalogu oraz uwierzytelnienie i kontrolę dostępu do pozostałych zasobów. Obecny backend udostępnia m.in. `GET /api/users/{id}` i `PATCH /api/users/change-display-name`. Odczyt przez ID jest dostępny dla uwierzytelnionych użytkowników niezależnie od właściciela profilu. Zmiana nazwy wymaga bieżącego hasła. Docelowe trasy profilu i preferencji poniżej pozostają do uzgodnienia przed integracją `LIVE`. [Kontroler użytkowników](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/users/UserController.java#L51-L81) · [Odczyt przez ID](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/users/UserService.java#L44-L50)

`services.profile` udostępnia profil i preferencje. `services.organizers` udostępnia katalog modeli kasetek, a `services.assignments` ich przypisania. `services.demo` udostępnia odczyt modelu kasetki po ID, listę i dodawanie leków, zastąpienie harmonogramu, plan dnia i tygodnia, przygotowanie kasetki oraz zapis i odczyt deklaracji przyjęcia. Tabela obejmuje także trasy planowane dla kolejnych zadań. [Repozytorium profilu](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/features/profile/data/ProfileRepository.kt) · [Repozytorium katalogu](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/features/organizers/data/OrganizerCatalogRepository.kt) · [Repozytorium demo](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/core/demo/DemoRepository.kt)

| Metoda | Trasa | Zastosowanie |
| --- | --- | --- |
| `GET`, `PATCH` | `/api/users/me` | Odczyt i edycja profilu |
| `GET`, `PUT` | `/api/users/me/preferences` | Odczyt i zastąpienie preferencji |
| `GET` | `/api/organizer-models` | Katalog i wyszukiwanie przez `qrCode` lub `productCode` |
| `GET` | `/api/organizer-models/{id}` | Model i definicje przegródek |
| `GET`, `POST` | `/api/users/me/organizers` | Lista i przypisanie kasetek |
| `GET`, `PATCH`, `DELETE` | `/api/users/me/organizers/{id}` | Odczyt, zmiana i usunięcie przypisania z zachowaniem historii |
| `GET`, `POST` | `/api/medications` | Lista i dodanie leku |
| `GET`, `PATCH` | `/api/medications/{id}` | Szczegóły i edycja leku |
| `PATCH` | `/api/medications/{id}/status` | Zmiana statusu leku |
| `PUT` | `/api/medications/{id}/schedule` | Zastąpienie harmonogramu |
| `GET` | `/api/plan/daily?date=YYYY-MM-DD&organizerId={id}` | Plan dnia |
| `GET` | `/api/plan/weekly?weekStart=YYYY-MM-DD&organizerId={id}` | Plan tygodnia |
| `GET` | `/api/dose-occurrences/{id}` | Szczegóły wystąpienia dawki |
| `POST` | `/api/dose-occurrences/{id}/intake` | Zapis deklaracji przyjęcia lub pominięcia |
| `GET` | `/api/intake?from=YYYY-MM-DD&to=YYYY-MM-DD` | Historia deklaracji |
| `POST` | `/api/users/me/organizers/{id}/preparations` | Rozpoczęcie przygotowania |
| `GET` | `/api/users/me/organizers/{id}/preparations/current` | Bieżąca sesja przygotowania |
| `GET` | `/api/preparations/{id}` | Szczegóły sesji |
| `PATCH` | `/api/preparations/{id}/compartments/{compartmentId}` | Zmiana statusu przegródki |
| `POST` | `/api/preparations/{id}/complete` | Zakończenie po sprawdzeniu wymaganych przegródek |

`organizerId` w planie i `{id}` w trasach `/api/users/me/organizers/{id}/…` oznaczają identyfikator przypisania `UserOrganizer`. `compartmentId` w trasie aktualizacji oznacza `CompartmentDefinition.id`. Układ cyfrowej kasetki wynika z definicji przegródek i ich `positionIndex`.

Projekt zakłada JSON. Daty i godziny harmonogramu to wartości lokalne, np. `2026-10-30` i `08:00`. Czas zdarzenia to znacznik ISO 8601, np. `2026-10-30T08:05:00Z`. Ilość dawki i moc leku to liczby dziesiętne. DTO musi zachować ich precyzję. Strefę czasową i ustawienia powiadomień obsługuje klient. Powiadomienia i ich odkładanie działają lokalnie w MVP.

W demo `days` używa numeracji 1–7 od poniedziałku, a `times` zawiera obiekty z polami `time` i opcjonalnym `timeOfDay`. Ilość i moc przechodzą przez liczbowe `JsonPrimitive`, dzięki czemu zachowują precyzję zapisu dziesiętnego.

Projekt przewiduje `201` dla tworzenia, `200` dla odczytu i aktualizacji oraz `204` dla odpowiedzi bez treści. Konkretne statusy i payloady potwierdzimy dla każdej wdrożonej trasy.

### Roboczy profil i preferencje w MOCK

Obie trasy wymagają `Authorization: Bearer <token>` i operują na bieżącym koncie. W `LIVE` repozytorium profilu pozostaje niedostępne do czasu potwierdzenia kontraktu.

`GET /api/users/me` zwraca `200` z polami `id` (`Long`), `email` (`String`) i `displayName` (`String?`). `PATCH /api/users/me` przyjmuje `{"displayName":"Alex"}` i zwraca ten sam format. Nazwa po usunięciu białych znaków na końcach ma od 1 do 32 znaków. E-mail pozostaje polem odczytu.

`GET /api/users/me/preferences` zwraca `200` i bieżący zestaw preferencji. `PUT` przyjmuje i zwraca pełny zestaw. Domyślne wartości w `MOCK` to `{"simplifiedMode":false,"textScale":100,"highContrast":false}`. `textScale` to procent wyrażony liczbą całkowitą od 100 do 200. Język, strefa czasowa, dźwięk i wibracja pozostają ustawieniami lokalnymi lub systemowymi.

W `MOCK` nieprawidłowa nazwa lub zestaw preferencji daje `400`, brak ważnej sesji daje `401`, a niedostępny magazyn preferencji daje `503`. [Implementacja tras demo](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/core/network/DemoSession.kt)

### Roboczy katalog modeli kasetek w MOCK

Backend nie ma jeszcze tras katalogu. Projekt backendu określa trasy, pola modelu i publiczny odczyt. Kształt odpowiedzi poniżej to założenie frontendu do potwierdzenia przed integracją `LIVE`. Do tego czasu repozytorium katalogu jest dostępne tylko w `MOCK`. [Projekt modelu kasetki](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/docs/design.md#organizermodel) · [FE-07](https://github.com/az2kxx/fe-medication-organizer/issues/7)

Wszystkie trzy odczyty są publiczne. Klient nie wysyła `Authorization` nawet przy aktywnej sesji.

| Trasa | Założona odpowiedź `200` |
| --- | --- |
| `GET /api/organizer-models` | Tablica modeli bez przegródek. Zawiera też modele nieaktywne. |
| `GET /api/organizer-models?productCode=...` | Ta sama tablica zawężona do kodu. Nieznany kod daje pustą tablicę, a nie `404`. |
| `GET /api/organizer-models/{id}` | Jeden model z tablicą `compartments`. Nieznane ID daje `404`. |

Model ma pola `id` (`Long`), `productCode`, `name`, `active` oraz opcjonalne `description`, `daysCovered` i `compartmentsPerDay`. Przegródka ma pola `id` (`Long`), `positionIndex`, `label` oraz opcjonalne `dayOfWeek` (1–7 od poniedziałku) i `timeOfDay` (`MORNING`, `NOON`, `EVENING`, `NIGHT`, `OTHER`). Klient pomija pozostałe pola, np. `qrCodeValue` i `createdAt`. [Fixture listy](../composeApp/src/androidHostTest/resources/fixtures/organizers/organizer-models-response.json) · [Fixture modelu](../composeApp/src/androidHostTest/resources/fixtures/organizers/organizer-model-daily-response.json)

Układ kasetki wynika z przegródek posortowanych według `positionIndex`. Rząd ma `compartmentsPerDay` przegródek. Bez tej wartości rząd tworzą kolejne przegródki z tym samym `dayOfWeek`. Interfejs pokazuje model nieaktywny, ale nie pozwala go wybrać do nowego przypisania. [Układ przegródek](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/features/organizers/domain/OrganizerLayout.kt)

Klient usuwa białe znaki z końców kodu i koduje go w całości w zapytaniu. `MOCK` porównuje kod bez rozróżniania wielkości liter. Katalog demo ma model 7×2 używany przez plan i przygotowanie, model 7×3 (`ELC-7X3`), model dzienny 1×4 bez dni tygodnia i nieaktywny model 7×1. Scenariusz `EMPTY` zachowuje katalog. [Katalog demo](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/core/demo/DemoCatalog.kt)

Katalog nie ma instrukcji produktu ani zdjęć modeli. Projekt backendu nie przewiduje dla nich pól ani tras.

### Robocze przypisania kasetek w MOCK

Trasy `/api/users/me/organizers` działają wyłącznie w `MOCK` i wymagają `Authorization: Bearer <token>`. Każda operacja dotyczy bieżącego konta. Kształt odpowiedzi i reguły zapisu wymagają potwierdzenia przed integracją `LIVE`. [FE-09](https://github.com/az2kxx/fe-medication-organizer/issues/9)

| Metoda i ścieżka względem `/api/users/me/organizers` | Żądanie | Odpowiedź |
| --- | --- | --- |
| `GET` | — | `200` z tablicą `UserOrganizer` |
| `GET /{id}` | — | `200` z jednym `UserOrganizer` |
| `POST` | `organizerModelId` i opcjonalne `displayName` | `201` z nowym `UserOrganizer` |
| `PATCH /{id}` | Pełny zestaw `displayName`, `active` i `isDefault` | `200` z zaktualizowanym `UserOrganizer` |
| `DELETE /{id}` | — | `204` bez treści |

`UserOrganizer` zawiera `id` i `organizerModelId` (`Long`), `displayName` (`String?`), `active` i `isDefault` (`Boolean`) oraz `assignedAt` (`Instant` w formacie ISO 8601). `id` identyfikuje przypisanie i trafia do tras planu oraz przygotowania. `organizerModelId` służy do odczytu układu przegródek z katalogu i pozostaje stałe po utworzeniu przypisania. Każde nowe przypisanie jest aktywne i ma `isDefault=false`. Ten sam model można przypisać więcej niż raz.

`POST` przyjmuje np. `{"organizerModelId":2,"displayName":"Kasetka podróżna"}`. `PATCH` wymaga wszystkich trzech pól, np. `{"displayName":null,"active":true,"isDefault":false}`. `displayName` jest jawne w odpowiedzi i w `PATCH`. `null` lub pusta nazwa po usunięciu skrajnych białych znaków oznacza wyświetlanie nazwy modelu. Nazwa dłuższa niż 32 znaki lub zawierająca znaki sterujące daje `400`. [Fixture PATCH](../composeApp/src/androidHostTest/resources/fixtures/organizers/assignment-patch-request.json) · [Fixture odpowiedzi](../composeApp/src/androidHostTest/resources/fixtures/organizers/assignment-response.json)

`active` określa, czy przypisanie jest dostępne do użycia, a `isDefault` wskazuje domyślne przypisanie bieżącego konta. Ustawienie aktywnego przypisania jako domyślnego atomowo zdejmuje tę flagę z poprzedniego. `active=false` i `isDefault=true` zwraca `409`. Bieżący wybór w UI jest osobny. Wybranie innej kasetki zmienia kontekst układu, planu i przygotowania bez zapisywania flag `active` lub `isDefault`. Gdy bieżący wybór przestaje być dostępny, klient wybiera aktywną domyślną kasetkę, a gdy takiej brak, pierwszą aktywną.

Dezaktywacja przez `PATCH` zachowuje przypisanie na liście i pozwala je ponownie aktywować. `DELETE` ukrywa przypisanie w odczytach i zachowuje historię przyjęć. Istniejące wystąpienie dawki nadal można zgłosić po dezaktywacji lub usunięciu przypisania. Nieznane lub usunięte przypisanie oraz nieznany model dają `404`. Próba przypisania wycofanego modelu albo odczytu planu lub pracy z przygotowaniem dla nieaktywnego przypisania daje `409`.

W `NORMAL` przypisanie `1` wskazuje model `1` (7×2), jest aktywne i domyślne. Zawiera początkową dawkę demo. Przypisanie `101` wskazuje model `2` (7×3), jest aktywne i zaczyna z pustym planem. W `EMPTY` zostaje przypisanie `1` z pustym planem. Przy zapisie harmonogramu leku bez przypisania demo wybiera domyślne przypisanie albo pierwsze aktywne. Jeśli żadne nie jest aktywne, harmonogram pozostaje bez przypisania. Późniejsza zmiana domyślnej kasetki albo wyboru w UI nie przenosi i nie powiela przypisanych dawek. [Repozytorium przypisań](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/features/organizers/data/OrganizerAssignmentsRepository.kt) · [Trasy demo](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/core/demo/DemoRoutes.kt)

## Robocze przykłady dla mocków

Przykłady pochodzą z projektu kontraktu i zawierają fikcyjne dane. Skrócone odpowiedzi pokazują tylko część projektowanych pól. [Projekt kontraktu](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/docs/design.md#4-api-contract)

### Dodanie leku `POST /api/medications`

Lista i dodawanie działają w `MOCK` i wymagają ważnej sesji. `GET /api/medications` zwraca `200` z tablicą skrótów zawierających `id`, `name` i `status`. Po zapisie klient odświeża listę. Pełne szczegóły i edycja mają osobne trasy w projekcie kontraktu.

W formularzu wymagane są nazwa, dodatnia ilość dawki, jej jednostka i data początku. Data końca jest opcjonalna i może być równa dacie początku. Moc leku jest opcjonalna i wymaga razem wartości oraz jednostki. `form` jest opcjonalne w `MOCK`. Dostępne wartości to `TABLET`, `CAPSULE`, `LIQUID` i `OTHER`. Klient przyjmuje przecinek lub kropkę w liczbie dziesiętnej, np. `0,5`, i wysyła ją jako liczbę JSON `0.5`. Nieprawidłowy zapis żądania daje `400`. [Fixture żądania](../composeApp/src/androidHostTest/resources/fixtures/medications/fe11-create-medication-request.json) · [Fixture odpowiedzi](../composeApp/src/androidHostTest/resources/fixtures/medications/fe11-create-medication-response.json)

Żądanie

```json
{
  "name": "Lek demonstracyjny",
  "form": "TABLET",
  "strengthValue": 500,
  "strengthUnit": "mg",
  "doseAmount": 0.5,
  "doseUnit": "TABLET",
  "startDate": "2026-10-30",
  "endDate": null
}
```

Skrócona odpowiedź `201 Created`

```json
{
  "id": 12,
  "name": "Lek demonstracyjny",
  "status": "ACTIVE"
}
```

Robocze DTO przyjmuje opcjonalne `form` o wartościach `TABLET`, `CAPSULE`, `LIQUID` i `OTHER`. `status` w odpowiedzi ma typ `MedicationStatus` o wartościach `ACTIVE`, `SUSPENDED` i `INACTIVE`. [Fixture dodawania leku z postacią](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/medication-create-request.json)

### Edycja leku i zmiana statusu w MOCK

W `services.demo` metody `updateMedication` i `updateMedicationStatus` wywołują obie trasy PATCH. `PATCH /api/medications/{id}` przyjmuje pełny formularz. `name`, `doseAmount`, `doseUnit` i `startDate` są wymagane. Klient wysyła jawne `null`, aby wyczyścić `endDate`, `strengthValue`, `strengthUnit` lub `form`. Mock również traktuje pominięcie pola opcjonalnego jako wyczyszczenie. `PATCH /api/medications/{id}/status` przyjmuje obiekt z polem `status`. Obie trasy zwracają `200` oraz skrót leku z polami `id`, `name` i `status`. Przed integracją trzeba potwierdzić pola żądań i odpowiedzi, znaczenie `null` i pominiętych pól oraz dozwolone przejścia statusów. [Fixture edycji](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/medication-update-request.json) · [Fixture czyszczenia pól](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/medication-update-clear-fields-request.json) · [Fixture zmiany statusu](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/medication-status-request.json)

Edycja przelicza dawki `PENDING` od daty demo. Zawieszenie lub dezaktywacja usuwa takie dawki z planu, a ponowna aktywacja pozwala je wygenerować. Potwierdzone dawki zachowują zapisane dane, a historia pozostaje dostępna.

### Zastąpienie harmonogramu `PUT /api/medications/12/schedule`

Wydzielony edytor FE-12 korzysta z istniejącego `ReplaceSchedule`. `days` zawiera co najmniej jeden dzień 1–7, poniedziałek=1. `times` zawiera co najmniej jedną godzinę, z opcjonalnym `timeOfDay`. Formularz przyjmuje `8:00` i `08:00`, a wysyła `08:00`; minuty wymagają dwóch cyfr. Powtórzenie tej samej znormalizowanej godziny jest błędem wszystkich kolidujących wierszy, także przy różnych porach dnia. Pory dnia nie są wyliczane z godzin.

`validFrom` jest wymaganą istniejącą datą `RRRR-MM-DD`. `validUntil` jest opcjonalne, może być równe początkowi i nie może go poprzedzać. Pusty koniec mapuje się na `null`; istniejący serializer pomija domyślne pola `null`. PUT zastępuje cały zestaw reguł, a formularz zachowuje pozostałe dni i godziny podczas edycji. Niepuste `validUntil` i wszystkie reguły są sprawdzane w [fixture żądania](../composeApp/src/androidHostTest/resources/fixtures/schedules/fe12-replace-schedule-request.json) i [odpowiedzi](../composeApp/src/androidHostTest/resources/fixtures/schedules/fe12-replace-schedule-response.json).

To nadal roboczy kontrakt `MOCK`, bez potwierdzonej implementacji harmonogramu w backendzie. Sam edytor oddaje payload callbackowi hosta. Podłączenie do leków oraz odświeżenie planu czekają na FE-11. Test mocka potwierdza zastąpienie przyszłych dawek i zachowanie wcześniejszych potwierdzeń; produkcyjne generowanie dawek pozostaje po stronie backendu.

Żądanie

```json
{
  "validFrom": "2026-10-30",
  "validUntil": null,
  "days": [1, 3, 5],
  "times": [
    { "time": "08:00", "timeOfDay": "MORNING" },
    { "time": "20:00", "timeOfDay": "EVENING" }
  ]
}
```

Odpowiedź `200 OK`

```json
{
  "id": 25,
  "medicationId": 12,
  "validFrom": "2026-10-30",
  "validUntil": null,
  "days": [1, 3, 5],
  "times": [
    { "time": "08:00", "timeOfDay": "MORNING" },
    { "time": "20:00", "timeOfDay": "EVENING" }
  ]
}
```

### Zapis deklaracji `POST /api/dose-occurrences/50/intake`

Żądanie

```json
{
  "status": "TAKEN",
  "occurredAt": "2026-10-30T08:05:00Z",
  "note": null
}
```

Odpowiedź `201 Created`

```json
{
  "id": 90,
  "doseOccurrenceId": 50,
  "status": "TAKEN",
  "occurredAt": "2026-10-30T08:05:00Z",
  "recordedAt": "2026-10-30T08:06:00Z"
}
```

### Aktualizacja przegródki `PATCH /api/preparations/30/compartments/8`

Żądanie

```json
{
  "status": "PREPARED",
  "note": null
}
```

Odpowiedź `200 OK`

```json
{
  "id": 100,
  "compartmentDefinitionId": 8,
  "status": "PREPARED",
  "checkedAt": "2026-10-30T08:00:00Z"
}
```

`8` identyfikuje definicję przegródki, a `100` zapis postępu tej przegródki w sesji. `PREPARED` opisuje przygotowanie, a `TAKEN` deklarację dotyczącą konkretnej dawki.

### Puste listy i dodatkowe stany

Roboczy plan dnia zwraca tablicę. Dzień bez dawek zwraca `[]`. DTO odczytują też `MISSED` w planie i `CANCELLED` w sesji przygotowania. Przykłady sprawdzają dekodowanie. Reguła nadawania `MISSED` i operacja anulowania przygotowania wymagają uzgodnienia. [Pusty plan](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/daily-plan-empty-response.json) · [Plan ze statusem MISSED](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/daily-plan-missed-response.json) · [Sesja CANCELLED](../composeApp/src/androidHostTest/resources/fixtures/demo-contract/preparation-cancelled-response.json)

## Do uzgodnienia z backendem

- Kształt odpowiedzi katalogu z sekcji o katalogu: lista bez przegródek, pusta tablica dla nieznanego kodu, obecność modeli nieaktywnych na liście i porównanie kodu bez rozróżniania wielkości liter.
- Pełne odpowiedzi profilu, preferencji, przypisania kasetki, planu dnia i tygodnia, historii oraz sesji przygotowania. Dla przypisań trzeba też potwierdzić pełny zestaw pól `PATCH`, zasady ustawiania domyślnej kasetki i usunięcie z zachowaniem historii. Potrzebujemy także formatu pustych list, wyniku wyszukiwania QR i odpowiedzi przy braku bieżącej sesji.
- Wymagane i opcjonalne pola mutacji, w tym `form` przy dodawaniu leku, pozostają do potwierdzenia z backendem. Trzeba też potwierdzić dopuszczalne wartości `timeOfDay` oraz numerację dni tygodnia.
- Reguły przypisania dawek do kasetki i przegródki oraz kształt odpowiedzi dla dawki bez przypisanej przegródki. Plan powinien przekazywać dane potrzebne kreatorowi i widokowi kasetki.
- Reguła nadawania `MISSED`, zachowanie lokalnego czasu w razie zmiany strefy lub przejścia na czas letni i zimowy oraz obsługa ponownego zgłoszenia lub korekty intake.
- Lista wymaganych przegródek do zakończenia przygotowania, sposób wskazania braków przy `409`, format błędów, kody domenowe i błędy poszczególnych pól walidacji.
