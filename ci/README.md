## debug.keystore

Keystore di firma per le build di debug generate dal workflow GitHub Actions
(`.github/workflows/build-apk.yml`), non quello del computer di chi sviluppa
in locale (che resta il proprio, in `~/.android/debug.keystore` o simile).

Senza un keystore fisso, ogni run del workflow partirebbe da una macchina
vuota e Gradle ne genererebbe uno nuovo e casuale ad ogni build: un
fingerprint SHA-1 diverso ogni volta, impossibile da registrare una volta
sola su un client OAuth (es. per il backup su Google Drive, vedi
`GoogleDriveAutorizzatore`).

Alias/password standard di un debug keystore Android (`androiddebugkey` /
`android`), come quelli generati automaticamente da Android Studio — non è
usato per firmare release, solo build di debug: nessun problema a tenerlo
nel repository.

Fingerprint di questo keystore:
```
SHA1: F2:1B:43:41:0E:3A:7A:B0:B5:B8:C0:55:E7:EE:1A:13:8D:62:8B:30
```
