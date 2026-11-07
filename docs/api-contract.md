# Kontrakt API frontendu MVP

Żądania i odpowiedzi sukcesu dla rejestracji i logowania odpowiadają kodowi backendu z commita `47f51ad367d9847508a0b079a03aa52bd8fc813a`. Trasy i przykłady w sekcjach roboczych pochodzą z projektu i służą do pracy nad mockami. [Commit backendu](https://github.com/Matt1479/medication-organizer/commit/47f51ad367d9847508a0b079a03aa52bd8fc813a) · [Projekt kontraktu](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/docs/design.md#4-api-contract) · [FE-02](https://github.com/az2kxx/fe-medication-organizer/issues/2)

## Tryby pracy

Oba tryby używają tych samych DTO rejestracji i logowania. Tryb, scenariusz demo i adres API ustawia się przy uruchomieniu aplikacji. [Uruchomienie projektu](../README.md#tryby-pracy)

| Tryb docelowy | Skąd pochodzą dane |
| --- | --- |
| `MOCK` | Lokalny transport obsługuje rejestrację i logowanie z fikcyjnymi danymi, profil i preferencje bieżącego konta oraz robocze operacje planu, leków, przygotowania i historii. |
| `LIVE` | Repozytorium wysyła rejestrację i logowanie do skonfigurowanego backendu. Dla kolejnych tras najpierw potwierdzamy kontrakt w kodzie i zachowanie odpowiedzi. |

## Potwierdzony kontrakt rejestracji i logowania

Bazowa ścieżka to `/api`. Backend zezwala na żądania do `/api/auth/**` bez sesji. Pozostałe żądania wymagają uwierzytelnienia przez `Authorization: Bearer <token>`. [Konfiguracja zabezpieczeń](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/config/SecurityConfig.java#L40-L51)

W `MOCK` publiczne są logowanie, rejestracja i odczyt modelu kasetki. Robocze trasy profilu, preferencji, leków, planu, przygotowania i deklaracji przyjęcia wymagają ważnej sesji demo. Klient używa odpowiedzi `401` z chronionej trasy jako sygnału do zakończenia sesji. [Obsługa sesji](architecture.md#obsługa-sesji)

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

Przy próbie rejestracji zajętego adresu e-mail serwis zgłasza `409 Conflict`. Kształt JSON błędów, odpowiedzi na błędne logowanie i szczegóły walidacji wymagają weryfikacji. Przykładowe `400`, `401`, `403`, `404`, `409` i `500` w mockach są scenariuszami roboczymi. [Sprawdzenie adresu e-mail](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/users/UserService.java#L26-L33)

Warstwa transportu zgłasza `ApiException` z kategorią `error`, lokalnym komunikatem oraz `statusCode`, jeśli serwer odpowiedział. `error.retryable` wskazuje, że przyczyna błędu może ustąpić. Transport wysyła każde żądanie jednokrotnie. Kod funkcji decyduje o ewentualnym ponowieniu.

## Robocze trasy do mocków

Projekt zakłada publiczny odczyt katalogu oraz uwierzytelnienie i kontrolę dostępu do pozostałych zasobów. Obecny backend udostępnia m.in. `GET /api/users/{id}` i `PATCH /api/users/change-display-name`. Odczyt przez ID jest dostępny dla uwierzytelnionych użytkowników niezależnie od właściciela profilu. Zmiana nazwy wymaga bieżącego hasła. Docelowe trasy profilu i preferencji poniżej pozostają do uzgodnienia przed integracją `LIVE`. [Kontroler użytkowników](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/users/UserController.java#L51-L81) · [Odczyt przez ID](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/src/main/java/com/matt1479/medicationorganizer/users/UserService.java#L44-L50)

`services.profile` udostępnia profil i preferencje. `services.organizers` udostępnia katalog modeli kasetek. `services.demo` udostępnia odczyt modelu kasetki po ID, listę i dodawanie leków, zastąpienie harmonogramu, plan dnia i tygodnia, przygotowanie kasetki oraz zapis i odczyt deklaracji przyjęcia. Tabela obejmuje także trasy planowane dla kolejnych zadań. [Repozytorium profilu](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/features/profile/data/ProfileRepository.kt) · [Repozytorium katalogu](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/features/organizers/data/OrganizerCatalogRepository.kt) · [Repozytorium demo](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/core/demo/DemoRepository.kt)

| Metoda | Trasa | Zastosowanie |
| --- | --- | --- |
| `GET`, `PATCH` | `/api/users/me` | Odczyt i edycja profilu |
| `GET`, `PUT` | `/api/users/me/preferences` | Odczyt i zastąpienie preferencji |
| `GET` | `/api/organizer-models` | Katalog i wyszukiwanie przez `qrCode` lub `productCode` |
| `GET` | `/api/organizer-models/{id}` | Model i definicje przegródek |
| `GET`, `POST` | `/api/users/me/organizers` | Lista i przypisanie kasetek |
| `GET`, `PATCH`, `DELETE` | `/api/users/me/organizers/{id}` | Odczyt, zmiana i usunięcie przypisania |
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
| `GET /api/organizer-models?qrCode=...` | Ta sama tablica z modelem o danym `qrCodeValue`. Nieznany kod daje pustą tablicę. |
| `GET /api/organizer-models/{id}` | Jeden model z tablicą `compartments`. Nieznane ID daje `404`. |

Model ma pola `id` (`Long`), `productCode`, `name`, `active` oraz opcjonalne `description`, `daysCovered` i `compartmentsPerDay`. Przegródka ma pola `id` (`Long`), `positionIndex`, `label` oraz opcjonalne `dayOfWeek` (1–7 od poniedziałku) i `timeOfDay` (`MORNING`, `NOON`, `EVENING`, `NIGHT`, `OTHER`). Klient pomija pozostałe pola, np. `qrCodeValue` i `createdAt`. [Fixture listy](../composeApp/src/androidHostTest/resources/fixtures/organizers/organizer-models-response.json) · [Fixture modelu](../composeApp/src/androidHostTest/resources/fixtures/organizers/organizer-model-daily-response.json)

Układ kasetki wynika z przegródek posortowanych według `positionIndex`. Rząd ma `compartmentsPerDay` przegródek. Bez tej wartości rząd tworzą kolejne przegródki z tym samym `dayOfWeek`. Interfejs pokazuje model nieaktywny, ale nie pozwala go wybrać do nowego przypisania. [Układ przegródek](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/features/organizers/domain/OrganizerLayout.kt)

Klient usuwa białe znaki z końców kodu i koduje go w całości w zapytaniu. `MOCK` porównuje kod bez rozróżniania wielkości liter. Katalog demo ma model 7×2 używany przez plan i przygotowanie, model 7×3 (`ELC-7X3`), model dzienny 1×4 bez dni tygodnia i nieaktywny model 7×1. Scenariusz `EMPTY` zachowuje katalog. [Katalog demo](../composeApp/src/commonMain/kotlin/com/elcomp/medicationorganizer/core/demo/DemoCatalog.kt)

Klient przesyła odczytany tekst QR bez zmian, poza usunięciem białych znaków z końców. Odrzuca wartość pustą, dłuższą niż 255 znaków (długość `qr_code_value` w schemacie) albo zawierającą znaki sterujące. Nie interpretuje jej jako adresu URL ani jej nie otwiera. Wynik inny niż dokładnie jeden model traktuje jako nierozpoznany kod. `MOCK` porównuje wartość QR dokładnie, a jednoczesne `productCode` i `qrCode` daje `400`. [Kody demo](qr-demo/README.md)

Katalog nie ma instrukcji produktu ani zdjęć modeli. Projekt backendu nie przewiduje dla nich pól ani tras.

## Robocze przykłady dla mocków

Przykłady pochodzą z projektu kontraktu i zawierają fikcyjne dane. Skrócone odpowiedzi pokazują tylko część projektowanych pól. [Projekt kontraktu](https://github.com/Matt1479/medication-organizer/blob/47f51ad367d9847508a0b079a03aa52bd8fc813a/docs/design.md#4-api-contract)

### Dodanie leku `POST /api/medications`

Żądanie

```json
{
  "name": "Lek demonstracyjny",
  "strengthValue": 500,
  "strengthUnit": "mg",
  "doseAmount": 1,
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

### Zastąpienie harmonogramu `PUT /api/medications/12/schedule`

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

## Do uzgodnienia z backendem

- Kształt odpowiedzi katalogu z sekcji o katalogu: lista bez przegródek, pusta tablica dla nieznanego kodu, obecność modeli nieaktywnych na liście i porównanie kodu bez rozróżniania wielkości liter. Format wartości QR nadrukowanej na kasetkach i jej zgodność z `qrCodeValue`.
- Pełne odpowiedzi profilu, preferencji, przypisania kasetki, planu dnia i tygodnia, historii oraz sesji przygotowania. Potrzebujemy także formatu pustych list, wyniku wyszukiwania QR i odpowiedzi przy braku bieżącej sesji.
- Dozwolone pola mutacji bez przykładów, w tym `form` przy dodawaniu leku. W modelu danych jest `form`, a skrócone żądanie nie pokazuje tego pola. Trzeba potwierdzić wymagane i opcjonalne pola, dopuszczalne wartości `timeOfDay` oraz numerację dni tygodnia.
- Reguły przypisania dawek do kasetki i przegródki oraz kształt odpowiedzi dla dawki bez przypisanej przegródki. Plan powinien przekazywać dane potrzebne kreatorowi i widokowi kasetki.
- Reguła nadawania `MISSED`, zachowanie lokalnego czasu w razie zmiany strefy lub przejścia na czas letni i zimowy oraz obsługa ponownego zgłoszenia lub korekty intake.
- Lista wymaganych przegródek do zakończenia przygotowania, sposób wskazania braków przy `409`, format błędów, kody domenowe i błędy poszczególnych pól walidacji.
