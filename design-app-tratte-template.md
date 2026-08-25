# App Android — Tratte configurabili e Template di viaggio

## 1. Obiettivo

Sostituire i tool HTML (Lugano⇄Ariccia) con un'app nativa Android che:

- gestisce una **libreria di Tratte** riutilizzabili (treno / auto / riunione)
- permette di comporre le Tratte in **Template** ordinati (es. "Andata Lugano-Ariccia", "Ritorno Sera", "Ritorno Mattina")
- calcola gli orari propagando avanti/indietro da una Tratta "ancora", con margini e arrotondamenti configurabili per Tratta
- scrive gli eventi **direttamente** nel Google Calendar del telefono (via Calendar Provider Android), senza passare da file `.ics`

Si appoggia al progetto Kotlin/Gradle già esistente (comparatore treni SBB/Trenitalia/Italo con integrazione Google Calendar), estendendolo invece di ripartire da zero.

---

## 2. Modello dati

### 2.1 `Tratta` (entità riutilizzabile, salvata in libreria)

| Campo | Tipo | Note |
|---|---|---|
| `id` | UUID | chiave primaria |
| `nome` | String | es. "Treno Lugano→Milano" |
| `tipo` | Enum: `TRENO`, `AUTO`, `RIUNIONE` | determina quali campi si applicano |
| `durataMinutiReale` | Int | durata effettiva (camminata/guida/riunione fissa) |
| `margineMinuti` | Int | buffer di cambio/coincidenza, default per tipo (5 per auto/riunione, 20 per treno) |
| `arrotondaInizio` | Enum: `NESSUNO`, `DIFETTO`, `ECCESSO` | come arrotondare l'inizio dell'evento calendario |
| `arrotondaFine` | Enum: `NESSUNO`, `DIFETTO`, `ECCESSO` | come arrotondare la fine dell'evento calendario |
| `stepArrotondamento` | Int | minuti (default 10) |
| `opzioniOrario` | `List<OpzioneOrario>` | solo per `TRENO`; vuoto per `AUTO`/`RIUNIONE` |

### 2.2 `OpzioneOrario` (pattern di uno slot ferroviario)

| Campo | Tipo | Note |
|---|---|---|
| `minutoPartenza` | Int | es. 43 |
| `cadenzaOre` | Int | 1 = ogni ora, 2 = ogni due ore, ecc. |
| `parità` | Int? | rilevante solo se `cadenzaOre > 1`: 0=ore pari, 1=ore dispari |
| `offsetOreArrivo` | Int | 0 = stessa ora, 1 = ora dopo, ecc. |
| `minutoArrivo` | Int | es. 29 |
| `etichetta` | String? | opzionale, per distinguere alternative (es. "via Pomezia") |

Una `Tratta` di tipo `TRENO` può avere **più `OpzioneOrario`** (es. Roma Termini→Pomezia ha tre slot: :06, :36, :42). Questo riproduce esattamente la logica "alternative" già validata negli strumenti HTML.

### 2.3 `Template`

| Campo | Tipo | Note |
|---|---|---|
| `id` | UUID | |
| `nome` | String | es. "Ritorno Sera" |
| `tratte` | `List<TemplateTratta>` | ordine di viaggio |

### 2.4 `TemplateTratta` (istanza di una Tratta dentro un Template)

| Campo | Tipo | Note |
|---|---|---|
| `trattaId` | UUID | riferimento a `Tratta` |
| `ordine` | Int | posizione nel viaggio |
| `titoloPersonalizzato` | String? | override del titolo evento, es. `"{oraPartenza} Casa / {destinazione} {oraArrivo}"` con placeholder |
| `èAncora` | Boolean | quale tratta è il punto di partenza del calcolo |
| `opzioneOrarioSelezionata` | Int? | indice dell'opzione scelta, se la Tratta ne ha più di una (persistito come "ultima scelta" per quel Template, poi modificabile ad ogni generazione) |

---

## 3. Motore di calcolo (propagazione avanti/indietro)

Stessa logica già scritta e validata in JavaScript, da portare 1:1 in Kotlin:

1. Si parte dalla `TemplateTratta` marcata come **ancora**, con un orario fornito dall'utente al momento della generazione.
2. **Propagazione indietro**: per ogni tratta precedente nell'ordine, si cerca — tra le sue `OpzioniOrario` (se treno) — lo slot più tardivo il cui arrivo rispetta `arrivo ≤ inizio_tratta_successiva − margineMinuti`. Per auto/riunione, si sottrae semplicemente `durataMinutiReale + margineMinuti`.
3. **Propagazione avanti**: per ogni tratta successiva, si cerca lo slot più precoce con `partenza ≥ fine_tratta_precedente + margineMinuti`. Per auto/riunione si somma `durataMinutiReale + margineMinuti`.
4. **Titolo evento**: sempre con orari reali (non arrotondati).
5. **Blocco calendario**: `DTSTART`/`DTEND` con arrotondamento applicato secondo `arrotondaInizio`/`arrotondaFine`/`stepArrotondamento` della singola Tratta.

Interfaccia proposta:

```kotlin
interface MotoreCalcolo {
    fun calcola(template: Template, ancoraIndex: Int, orarioAncora: LocalTime): List<EventoCalcolato>
}

data class EventoCalcolato(
    val trattaId: UUID,
    val titolo: String,          // con orari reali
    val inizioReale: LocalTime,
    val fineReale: LocalTime,
    val inizioBlocco: LocalTime, // arrotondato
    val fineBlocco: LocalTime,
    val opzioniAlternative: List<EventoCalcolato> = emptyList() // per treni con più slot
)
```

---

## 4. Schermate (proposta)

1. **Libreria Tratte** — lista, crea/modifica/duplica/elimina una `Tratta`. Form dedicato per `TRENO` con editor delle `OpzioniOrario` (aggiungi slot, imposta cadenza/parità).
2. **Editor Template** — drag&drop o frecce su/giù per ordinare le Tratte scelte dalla libreria; selezione della tratta-ancora; anteprima testuale del percorso.
3. **Esecuzione Template** — schermata "genera evento": scegli data, inserisci l'orario per la tratta-ancora, vedi il calcolo propagato con le eventuali alternative (stessa UI a card con "cambio X min" già validata), conferma → scrittura in calendario.
4. **(Opzionale) Storico** — ultimi template generati, per rigenerare velocemente con orario diverso.

---

## 5. Integrazione Calendario

- Permesso `WRITE_CALENDAR` (+ `READ_CALENDAR` per leggere l'ID del calendario di default)
- Inserimento eventi via `ContentResolver.insert(CalendarContract.Events.CONTENT_URI, values)`
- Vantaggio rispetto ai tool HTML: nessun file, nessun passaggio da Chrome/Drive, l'evento appare istantaneamente

---

## 6. Domande aperte da chiarire prima di scrivere il codice

1. **Stack UI**: il progetto esistente usa XML/View o Jetpack Compose? (Compose è consigliato se si parte da un punto neutro, ma meglio restare coerenti con l'esistente)
2. **Persistenza**: Room (SQLite) è l'opzione naturale — confermi?
3. **Titolo evento personalizzabile**: preferisci un template testuale con placeholder (`{oraPartenza} {luogoPartenza} / {luogoArrivo} {oraArrivo}`) configurabile per Tratta, o un formato fisso uguale per tutte?
4. **Alternative multiple**: per una Tratta con più `OpzioniOrario` (es. Roma→Pomezia/Albano/Pavona), oggi nel modello ho tenuto "destinazione" come parte della singola Tratta con più opzioni orario alternative. Alternativa: modellare Pomezia/Albano/Pavona come **tre Tratte distinte**, e la scelta tra loro avviene scegliendo quale inserire nel Template. Quale preferisci? (la prima è più compatta, la seconda è più semplice da implementare e capire)
5. **Migrazione dai tool HTML esistenti**: vuoi che i template Andata/Ritorno che abbiamo già costruito vengano pre-caricati come dati di default nell'app (seed iniziale del database), così parti già con le tratte Lugano⇄Ariccia pronte?

---

## 7. Prossimi passi

Una volta chiarito quanto sopra, il lavoro si scompone in:

1. Entità Room (`Tratta`, `OpzioneOrario`, `Template`, `TemplateTratta`) + DAO
2. Motore di calcolo (puro Kotlin, testabile senza Android — utile per scrivere unit test prima di collegare la UI)
3. Schermate Libreria Tratte + Editor Template
4. Schermata Esecuzione + scrittura CalendarContract
5. Seed dati Lugano⇄Ariccia (se confermato al punto 6.5)

Ognuno di questi punti può essere un file/commit separato da portare via Claude Code nel tuo progetto.
