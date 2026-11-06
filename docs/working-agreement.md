# Zasady pracy zespołu i agentów

Ten dokument obowiązuje osoby pracujące nad frontendem oraz sesje Codexa i Claude’a. Przed zmianą kodu przeczytaj issue z kryteriami odbioru i opis architektury. Przy zmianach danych lub transportu sprawdź też kontrakt API. [Tablica zespołu](https://github.com/users/az2kxx/projects/1) · [Architektura](architecture.md) · [Kontrakt API](api-contract.md)

## Praca asynchroniczna

`codex/mvp-frontend` jest gałęzią integracyjną frontendu do czasu uzgodnionego przeniesienia MVP na `main`. Od niej zaczynają wszystkie zadania MVP. Jest też gałęzią docelową PR. [Klonowanie repozytorium](../README.md#pobranie-projektu)

1. W issue zapisz właściciela, zależności i obszar plików. Jeśli zadanie potrzebuje wspólnego interfejsu, uzgodnijcie jego sygnaturę i przykładowe dane w issue. Zmiany współdzielonego kontraktu zgłaszajcie w małym PR, który można scalić przed funkcjami zależnymi od tego kontraktu.
2. Wykonaj `git fetch origin` i utwórz gałąź dla jednego issue z `origin/codex/mvp-frontend`, np. `git switch -c feat/fe-04-navigation origin/codex/mvp-frontend`. Gdy zależność jest jeszcze w PR, podaj jego link i określ, na jakiej wersji opiera się Twoja praca.
3. Edytuj pliki potrzebne do realizacji issue. Jeśli odkryjesz potrzebę szerszej zmiany, opisz ją w issue lub PR, żeby druga osoba mogła dostosować swoje zadanie. Zachowaj jej lokalne zmiany i prowadź oddzielną gałąź dla własnej pracy.
4. Uruchom sprawdzenia z README, które dotyczą zmiany. W PR wpisz `Closes #<numer>`, opisz efekt, wykonane sprawdzenia i zależności. Podaj przyczynę, jeżeli któregoś sprawdzenia nie udało się uruchomić. [Komendy w README](../README.md)
5. Druga osoba sprawdza PR. Zespół scala go do `codex/mvp-frontend` po akceptacji review i przejściu CI. Po scaleniu zamknij ręcznie zrealizowane issue. GitHub interpretuje `Closes` z opisu PR tylko dla gałęzi domyślnej. Decyzje o API i przekazaniu pracy zapisujcie w issue, PR lub dokumentacji repozytorium, żeby dało się wznowić zadanie w nowej sesji. [Łączenie PR z issue w GitHub](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)

## Dane i weryfikacja

- Przechowuj przykładowe żądania i odpowiedzi w wersjonowanych plikach fixture. Używaj fikcyjnych kont, leków i historii. Konfigurację lokalną i sekrety trzymaj poza Gitem.
- Oddzielaj odpowiedzi potwierdzone przez backend od założeń używanych w mockach. Po uzgodnieniu zmiany zaktualizuj kontrakt i fixture w tym samym PR.
- W opisie PR podaj krótki scenariusz, który reviewer może odtworzyć, oraz platformę i tryb, na których go sprawdzono. Zmiany aparatu lub powiadomień sprawdzaj na odpowiednim urządzeniu albo symulatorze, jeśli obsługuje dany scenariusz.

## Przekazanie zadania agentowi

Uruchom sesję w katalogu repozytorium lub jego osobnym worktree. Podaj numer issue, zakres i oczekiwany sposób weryfikacji. W sesji bez dostępu do repozytorium dołącz treść issue i dokumenty potrzebne do zadania.

> Przeczytaj zasady pracy i architekturę. Zrealizuj FE-04 w `composeApp/src/commonMain/`, korzystając ze wspólnego ekranu startowego FE-01. Zapisz w PR sposób sprawdzenia nawigacji, zmienione punkty wejścia oraz zależności dla kolejnych zadań.

Codex dostaje odwołanie do tego dokumentu przez `AGENTS.md`. Claude Code ładuje go przez import w `CLAUDE.md`. Zmieniaj wspólne zasady tutaj, żeby obie sesje korzystały z tej samej wersji. [OpenAI Docs](https://learn.chatgpt.com/docs/agent-configuration/agents-md) · [Dokumentacja Claude Code](https://code.claude.com/docs/en/memory#import-additional-files)
