package com.desideri.viaggiotemplate.domain.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.desideri.viaggiotemplate.domain.log.AttivitaLogger
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Scope minimo necessario: accesso solo all'App Data folder (cartella nascosta privata di quest'app), non all'intero Drive dell'utente. */
private const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

sealed interface RisultatoAutorizzazioneDrive {
    data class Autorizzato(val accessToken: String) : RisultatoAutorizzazioneDrive
    /** Serve mostrare il consenso Google: [richiesta] va lanciata con un `ActivityResultLauncher<IntentSenderRequest>`. */
    data class RichiedeConsenso(val richiesta: IntentSenderRequest) : RisultatoAutorizzazioneDrive
}

/**
 * Ottiene un access token con lo scope Drive App Data usando l'Authorization API di Google Identity
 * Services (`com.google.android.gms.auth.api.identity`), non la (deprecata) `GoogleSignIn`/
 * `GoogleSignInClient`. Non serve un login "identità" separato (Credential Manager / Sign in with
 * Google): questa funzione basta da sola, l'account viene scelto/confermato dall'utente nella UI di
 * sistema che Play Services mostra se necessario (nessun account già autorizzato con questo scope,
 * o consenso mai dato prima).
 */
object GoogleDriveAutorizzatore {

    /**
     * Prima chiamata di un flusso di autorizzazione. Se l'utente ha già concesso lo scope in
     * passato ritorna subito l'access token; altrimenti ritorna la richiesta di consenso da
     * lanciare, il cui esito va poi passato a [completaConsenso].
     */
    suspend fun richiedi(activity: Activity): RisultatoAutorizzazioneDrive = suspendCancellableCoroutine { cont ->
        val richiesta = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_APPDATA)))
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(richiesta)
            .addOnSuccessListener { risultato ->
                val pendingIntent = risultato.pendingIntent
                when {
                    risultato.hasResolution() && pendingIntent != null ->
                        cont.resume(
                            RisultatoAutorizzazioneDrive.RichiedeConsenso(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        )
                    risultato.accessToken != null ->
                        cont.resume(RisultatoAutorizzazioneDrive.Autorizzato(risultato.accessToken!!))
                    else -> {
                        // Play Services ha risposto "con successo" ma senza consenso da risolvere né
                        // token: nessuna ApiException da cui estrarre un codice (vedi
                        // BackupDriveException.AutorizzazioneNegata), quindi l'unico modo per capire
                        // il motivo è ispezionare l'oggetto risultato stesso — loggato qui nel
                        // Registro Attività (menu dell'app) così è consultabile senza adb/Logcat.
                        AttivitaLogger.errore(
                            "Autorizzazione Drive: risultato senza consenso né token",
                            "pendingIntent=$pendingIntent hasResolution=${risultato.hasResolution()} " +
                                "accessToken=${risultato.accessToken} risultato=$risultato"
                        )
                        cont.resumeWithException(BackupDriveException.AutorizzazioneNegata())
                    }
                }
            }
            .addOnFailureListener { errore ->
                cont.resumeWithException(BackupDriveException.AutorizzazioneNegata(errore))
            }
    }

    /** Da chiamare con il risultato del launcher lanciato per [RisultatoAutorizzazioneDrive.RichiedeConsenso]. */
    fun completaConsenso(activity: Activity, esitoOk: Boolean, dati: Intent?): String {
        if (!esitoOk) {
            // La resolution/pendingIntent di Play Services (scelta account, consenso) è tornata
            // con esito non-OK: non un'ApiException, quindi loggato qui per lo stesso motivo del
            // ramo "senza consenso né token" in richiedi(). Intent.toString() da solo non mostra
            // il contenuto degli extra (es. uno Status con codice/messaggio dell'errore reale),
            // quindi li si estrae esplicitamente qui.
            val extra = dati?.extras?.keySet()
                ?.joinToString { chiave -> "$chiave=${dati.extras?.get(chiave)}" }
                ?: "nessuno"
            AttivitaLogger.errore(
                "Autorizzazione Drive: resolution intent tornata con esito non-OK",
                "extra: $extra | dati=$dati"
            )
            throw BackupDriveException.AutorizzazioneNegata()
        }
        return try {
            val risultato = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(dati)
            risultato.accessToken ?: run {
                AttivitaLogger.errore(
                    "Autorizzazione Drive: resolution intent OK ma senza token",
                    "hasResolution=${risultato.hasResolution()} pendingIntent=${risultato.pendingIntent} risultato=$risultato"
                )
                throw BackupDriveException.AutorizzazioneNegata()
            }
        } catch (e: ApiException) {
            AttivitaLogger.errore("Autorizzazione Drive: ApiException dopo la resolution", e.toString())
            throw BackupDriveException.AutorizzazioneNegata(e)
        }
    }
}
