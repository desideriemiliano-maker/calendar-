package com.desideri.viaggiotemplate.domain.model

/** Tipo di tratta: determina quali campi si applicano nel motore di calcolo. */
enum class TipoTratta {
    TRENO,
    AUTO,
    RIUNIONE,
    A_PIEDI,
    AEREO;

    /** True per i tipi con orari programmati (opzioni ricorrenti + orari fissi), come i treni. */
    val usaOrariProgrammati: Boolean
        get() = this == TRENO || this == AEREO
}

/** Direzione di arrotondamento per inizio/fine del blocco calendario. */
enum class Arrotondamento {
    NESSUNO,
    DIFETTO,   // verso il basso (usato tipicamente per l'inizio)
    ECCESSO    // verso l'alto (usato tipicamente per la fine)
}

/** Vettore ferroviario di una tratta TRENO (solo informativo, non influenza il motore di calcolo). */
enum class Vettore {
    TRENITALIA,
    ITALO,
    SBB,
    ALTRO
}

/** Promemoria del calendario prima dell'inizio dell'evento; NESSUNA = nessun promemoria. */
enum class Notifica(val minuti: Int?, val etichetta: String) {
    NESSUNA(null, "Nessuna"),
    QUINDICI_MINUTI(15, "15 min prima"),
    TRENTA_MINUTI(30, "30 min prima"),
    UN_ORA(60, "1 ora prima")
}

/**
 * Icona opzionale che caratterizza il tipo di un [Luogo] (ufficio, casa, stazione...), mostrata
 * nella lista Luoghi e come marker sulla mappa. Il nome dell'enum è l'identificativo persistito
 * su disco (vedi [com.desideri.viaggiotemplate.data.local.entities.LuogoEntity.icona]): mai la
 * risorsa drawable direttamente, che è libera di essere rinominata in futuro senza invalidare i
 * dati salvati. La mappatura verso l'ImageVector/drawable concreto vive nel layer UI, non qui,
 * perché questo modulo domain resta senza dipendenze da Compose/risorse Android. null = nessuna
 * icona (comportamento di un Luogo prima di questa funzionalità).
 */
enum class IconaLuogo(val etichetta: String) {
    UFFICIO("Ufficio"),
    CASA("Casa"),
    STAZIONE("Stazione"),
    AEROPORTO("Aeroporto"),
    EDIFICIO("Edificio"),
    PARCHEGGIO("Parcheggio")
}
