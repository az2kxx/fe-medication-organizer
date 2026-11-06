# Kontrakt API frontendu MVP

Ta referencja opisuje umowę, na której oprzemy klienta Etapu I. Źródłem tras i przykładów jest sekcja 4 dokumentacji backendu, odczytana 6 listopada 2026. Przykłady zawierają skrócone odpowiedzi. Pełne DTO i rozbieżności ustalimy w FE-02. [Dokumentacja backendu](https://github.com/Matt1479/medication-organizer/blob/main/docs/design.md#4-api-contract) · [FE-02](https://github.com/az2kxx/fe-medication-organizer/issues/2)

## Uruchomienie lokalne i tryby pracy

FE-01 uruchamia lokalny szkielet aplikacji. Na tym etapie do uruchomienia wystarczą narzędzia frontendowe opisane w README. FE-02 przygotuje DTO i dane testowe na podstawie uzgodnionych payloadów. FE-03 doda klienta HTTP, wspólny interfejs repozytoriów oraz konfigurację przełączania trybu. [Uruchomienie projektu](../README.md) · [FE-02](https://github.com/az2kxx/fe-medication-organizer/issues/2) · [FE-03](https://github.com/az2kxx/fe-medication-organizer/issues/3)

| Tryb docelowy | Skąd pochodzą dane |
| --- | --- |
| `MOCK` | Lokalny adapter korzysta z fikcyjnych danych i obsługuje te same ścieżki żądań, odpowiedzi oraz błędów co `LIVE`. Zapis leku, harmonogramu, przygotowania lub przyjęcia dawki aktualizuje wspólny stan demonstracyjny. |
| `LIVE` | Repozytoria wysyłają żądania HTTP do skonfigurowanego adresu backendu. Backend zwraca plan i rozstrzyga walidację, uprawnienia i konflikty. Błąd połączenia jest widoczny w aplikacji. |

## Trasy i format danych

Bazowa ścieżka to `/api`. Rejestracja, logowanie i odczyt katalogu są publiczne. Pozostałe trasy wymagają nagłówka `Authorization: Bearer <token>`. Backend sprawdza, czy użytkownik ma dostęp do zasobu.

| Metoda | Trasa | Zastosowanie |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Rejestracja |
| `POST` | `/api/auth/login` | Logowanie i token |
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

API wymienia JSON. Daty i godziny harmonogramu to wartości lokalne, np. `2026-10-30` i `08:00`. Czas zdarzenia to znacznik ISO 8601, np. `2026-10-30T08:05:00Z`. Ilość dawki i moc leku to liczby dziesiętne. DTO musi zachować ich precyzję. Dostępne preferencje to `simplifiedMode`, `textScale` i `highContrast`. Strefę czasową i ustawienia powiadomień obsługuje klient. Powiadomienia i ich odkładanie działają lokalnie w MVP.

Udane tworzenie zwykle zwraca `201`, odczyt i aktualizacja `200`, a wynik bez treści `204`. Kontrakt przewiduje `400` przy błędnych danych, `401` przy braku ważnej sesji, `403` przy braku uprawnień, `404` przy zasobie nieistniejącym lub niedostępnym, `409` przy konflikcie oraz `500` przy błędzie serwera. Szczegółowy format błędu i kody domenowe wymagają uzgodnienia w FE-02.

## Przykładowe żądania i odpowiedzi

Pola poniżej pochodzą z udokumentowanych przykładów. Dane demonstracyjne są fikcyjne. Skrócona odpowiedź pokazuje znane pola i może pomijać pozostałe dane.

### Rejestracja `POST /api/auth/register`

Żądanie

```json
{
  "email": "demo@example.com",
  "password": "demo-password",
  "displayName": "Ola"
}
```

Odpowiedź `201 Created`

```json
{
  "id": 1,
  "email": "demo@example.com",
  "displayName": "Ola"
}
```

Po rejestracji klient przechodzi do logowania, aby otrzymać token.

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
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

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

- Pełne odpowiedzi katalogu, profilu, preferencji, przypisania kasetki, planu dnia i tygodnia, historii oraz sesji przygotowania. Potrzebujemy także formatu pustych list, wyniku wyszukiwania QR i odpowiedzi przy braku bieżącej sesji.
- Dozwolone pola mutacji bez przykładów, w tym `form` przy dodawaniu leku. W modelu danych jest `form`, a skrócone żądanie nie pokazuje tego pola. Trzeba potwierdzić wymagane i opcjonalne pola, dopuszczalne wartości `timeOfDay` oraz numerację dni tygodnia.
- Reguły przypisania dawek do kasetki i przegródki oraz kształt odpowiedzi dla dawki bez przypisanej przegródki. Plan powinien przekazywać dane potrzebne kreatorowi i widokowi kasetki.
- Reguła nadawania `MISSED`, zachowanie lokalnego czasu w razie zmiany strefy lub przejścia na czas letni i zimowy oraz obsługa ponownego zgłoszenia lub korekty intake.
- Lista wymaganych przegródek do zakończenia przygotowania, sposób wskazania braków przy `409` i format błędów walidacji, w tym błędy poszczególnych pól.
