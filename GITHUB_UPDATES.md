# Aktualizacje aplikacji przez GitHub

Aplikacja mo?e okresowo sprawdza? publiczne wydania w GitHub Releases. Nie wymaga
w?asnego serwera, konta u?ytkownika ani tokenu zapisanego w aplikacji.

## Konfiguracja repozytorium

1. Repozytorium musi by? publiczne, poniewa? aplikacja korzysta z anonimowego
   endpointu GitHub API.
2. W pliku `gradle.properties` ustaw:

   ```properties
   GITHUB_REPOSITORY=nazwa-konta/nazwa-repozytorium
   ```

3. Zwi?ksz `versionCode` i `versionName` w `app/build.gradle.kts`.
4. Zbuduj podpisany plik APK tym samym certyfikatem co poprzedni? wersj?.
5. W repozytorium wybierz **Releases > Draft a new release**.
6. Ustaw tag odpowiadaj?cy wersji, np. `v1.0.1`, i do??cz plik APK.

## Dzia?anie w aplikacji

- U?ytkownik w??cza opcj? **Aktualizacje aplikacji** w zak?adce **Wi?cej**.
- Na Androidzie 13 lub nowszym aplikacja prosi o zgod? na powiadomienia.
- Pierwsze sprawdzenie jest wykonywane po w??czeniu opcji, a kolejne co 12 godzin.
- Je?li tag najnowszego wydania jest wy?szy od zainstalowanego `versionName`,
  aplikacja pokazuje jedno powiadomienie dla danego wydania.
- Dotkni?cie powiadomienia otwiera stron? wydania na GitHub. Aplikacja nie
  instaluje aktualizacji bez wiedzy u?ytkownika.

## Wa?ne

APK aktualizacji musi by? podpisany tym samym kluczem co zainstalowana aplikacja.
W przeciwnym razie Android nie pozwoli zainstalowa? go jako aktualizacji.
