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
