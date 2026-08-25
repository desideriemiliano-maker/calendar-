# App Tratte & Template — implementazione

Questo pacchetto contiene l'implementazione Kotlin discussa nel documento di design
(`design-app-tratte-template.md`), pronta per essere portata nel tuo progetto Android
esistente tramite Claude Code / IntelliJ.

## Struttura

```
app/src/main/java/com/desideri/viaggiotemplate/
├── ViaggioApplication.kt          # inizializza il DB al boot
├── MainActivity.kt                # host Compose + navigazione
├── domain/
│   ├── model/                     # Tratta, OpzioneOrario, Template, enum
│   ├── calcolo/                   # MotoreCalcolo (puro Kotlin, testabile)
│   └── calendar/                  # CalendarWriter (CalendarContract)
├── data/local/
│   ├── entities/                  # entità Room
│   ├── dao/                       # DAO
│   └── AppDatabase.kt
├── repository/                    # ponte Room <-> modelli di dominio
└── ui/
    ├── AppContainer.kt            # service locator minimale (sostituibile con Hilt/Koin)
    ├── navigation/AppNavigation.kt
    ├── tratte/                    # Sezione 1: libreria tratte
    ├── template/                  # Sezione 2: editor template
    └── esecuzione/                # Sezione 3: esegui/adatta/aggiungi al calendario
```

## Passi di integrazione

1. **Package name**: ho usato `com.desideri.viaggiotemplate` come placeholder — rinomina
   secondo il pacchetto del tuo progetto esistente (find & replace su tutti i file).

2. **Dipendenze Gradle** (`app/build.gradle.kts`), da aggiungere se non già presenti:

   ```kotlin
   dependencies {
       // Compose
       implementation(platform("androidx.compose:compose-bom:2024.09.00"))
       implementation("androidx.compose.material3:material3")
       implementation("androidx.compose.material:material-icons-extended")
       implementation("androidx.activity:activity-compose:1.9.2")
       implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
       implementation("androidx.navigation:navigation-compose:2.8.0")

       // Room
       implementation("androidx.room:room-runtime:2.6.1")
       implementation("androidx.room:room-ktx:2.6.1")
       ksp("androidx.room:room-compiler:2.6.1")   // richiede il plugin KSP

       // Coroutines
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
   }
   ```

   Se il progetto non usa già KSP, aggiungi in cima al file:
   ```kotlin
   plugins {
       id("com.google.devtools.ksp") version "2.0.20-1.0.24"
   }
   ```

3. **java.time**: il codice usa `java.time.LocalTime`/`LocalDate`. Se il tuo `minSdk` è
   inferiore a 26, serve il core library desugaring:
   ```kotlin
   android {
       compileOptions {
           isCoreLibraryDesugaringEnabled = true
       }
   }
   dependencies {
       coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
   }
   ```

4. **Permessi** (`AndroidManifest.xml`):
   ```xml
   <uses-permission android:name="android.permission.READ_CALENDAR" />
   <uses-permission android:name="android.permission.WRITE_CALENDAR" />
   ```
   e registra `ViaggioApplication` e `MainActivity`:
   ```xml
   <application android:name=".ViaggioApplication" ...>
       <activity android:name=".MainActivity" android:exported="true">
           <intent-filter>
               <action android:name="android.intent.action.MAIN" />
               <category android:name="android.intent.category.LAUNCHER" />
           </intent-filter>
       </activity>
   </application>
   ```

5. **DI**: `AppContainer` è un service locator minimale, comodo per partire subito.
   Se il progetto esistente usa già Hilt/Koin per l'integrazione Google Calendar,
   conviene sostituirlo con l'iniezione già in uso, per coerenza.

## Cosa manca / prossimi passi consigliati

- **Seed dati Lugano⇄Ariccia**: le Tratte e i Template che abbiamo costruito negli
  strumenti HTML non sono ancora precaricati. Se li vuoi già pronti al primo avvio,
  serve una funzione di seed che inserisca le Tratte (Ufficio→Stazione, Lugano→Milano,
  Milano→Roma, Roma→Pomezia/Albano/Pavona, ecc.) al primo avvio del database.
- **Validazione form**: gli editor di Tratta/Template accettano input minimi; andrebbero
  aggiunti controlli (es. margine negativo, nome vuoto) prima del salvataggio.
- **Test del motore di calcolo**: `MotoreCalcolo` è puro Kotlin, quindi testabile con
  JUnit senza emulatore — consiglio di scrivere gli unit test per i casi già validati
  negli strumenti HTML (Pomezia/Albano/Pavona, cadenza a 2 ore, ecc.) prima di collegarlo
  alla UI, per essere sicuri che il porting sia fedele.
- **Limite noto**: il motore di calcolo non gestisce esplicitamente il passaggio di
  mezzanotte (es. un template che si estende oltre le 24:00). Per i viaggi Lugano-Ariccia
  non dovrebbe mai verificarsi, ma se in futuro servisse, la logica di confronto minuti
  in `MotoreCalcolo.trovaUltimoSlotConArrivoEntro`/`trovaPrimoSlotConPartenzaDa` andrebbe
  rivista per lavorare su minuti assoluti anche in fase di confronto finale.
- **UI non rifinita**: le schermate sono funzionali ma minimali (niente animazioni,
  conferme di eliminazione, drag&drop per riordinare — uso frecce su/giù invece).

## Come useresti l'app, in pratica

1. **Tratte**: crei "Lugano Ufficio→Stazione" (AUTO, 15 min, margine 5), "Lugano→Milano"
   (TRENO, con l'opzione oraria :02→:17 ora dopo), "Roma Termini→Pomezia" (TRENO, con le
   tre opzioni :06/:36/:42), ecc. — una volta sola, restano in libreria.
2. **Template**: crei "Andata Lugano-Ariccia", trascini le tratte nell'ordine giusto,
   marchi "Milano→Roma" come ancora, e assegni le alternative Pomezia/Albano/Pavona allo
   slot corrispondente.
3. **Esegui**: apri il template, inserisci l'orario del treno Milano-Roma di quel giorno,
   premi "Calcola", scegli l'alternativa di destinazione se serve, aggiusti manualmente
   se necessario, premi "Aggiungi al calendario" — fatto, eventi scritti direttamente
   senza file da scaricare.
