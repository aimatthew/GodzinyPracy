# Konfiguracja kopii Google Drive

Kod aplikacji korzysta z Google Identity Services, Drive REST API v3 i zakresu
`https://www.googleapis.com/auth/drive.appdata`. Nie wymaga Firebase, własnego
serwera ani pliku `google-services.json`.

## Google Cloud Console

1. Utwórz projekt, np. `Godziny Pracy`.
2. W sekcji **APIs & Services > Library** włącz **Google Drive API**.
3. W **Google Auth Platform** uzupełnij nazwę aplikacji, adres pomocy,
   kontakt dewelopera i publiczny adres polityki prywatności.
4. Dodaj zakres `https://www.googleapis.com/auth/drive.appdata`.
5. Jeżeli aplikacja jest w trybie testowym, dodaj używane konta Google jako
   użytkowników testowych.
6. Utwórz klienta OAuth typu **Android**:
   - package name: `pl.godzinypracy.workly`
   - SHA-1: odcisk certyfikatu, którym podpisywana jest aplikacja.
7. Przed publikacją dodaj również SHA-1 certyfikatu Google Play App Signing.

## Zachowanie aplikacji

- Pierwsze połączenie pokazuje systemowy wybór konta i zgodę Google.
- Jeśli kopia już istnieje, aplikacja pyta, czy ją przywrócić, czy zastąpić
  danymi z telefonu.
- Po każdej zmianie wpisu, stawki lub normy zlecana jest synchronizacja.
- Bez internetu zadanie czeka na połączenie.
- Dodatkowa synchronizacja kontrolna wykonywana jest co 12 godzin.
- Odłączenie usuwa ukryty plik kopii i cofa zgodę OAuth.

## Ważne

Nie należy dodawać zakresu `drive`, ponieważ dawałby dostęp do całej zawartości
Dysku. Aplikacja potrzebuje wyłącznie `drive.appdata`.
