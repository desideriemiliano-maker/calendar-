package com.desideri.viaggiotemplate.domain.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Richiede una singola rilevazione della posizione attuale via FusedLocationProvider. Il chiamante
 * deve aver già verificato/ottenuto il permesso ACCESS_FINE_LOCATION o ACCESS_COARSE_LOCATION
 * (@SuppressLint qui sotto è per il compilatore, non sostituisce quel controllo). Ritorna null se
 * il GPS è spento o la posizione non è altrimenti disponibile entro il timeout di sistema — mai
 * un'eccezione, per lasciare alla UI la scelta del messaggio d'errore invece di un fallimento silenzioso.
 */
@SuppressLint("MissingPermission")
suspend fun posizioneAttuale(context: Context): Location? = suspendCancellableCoroutine { continuazione ->
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellationSource = CancellationTokenSource()
    continuazione.invokeOnCancellation { cancellationSource.cancel() }
    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token)
        .addOnSuccessListener { location -> if (continuazione.isActive) continuazione.resume(location) }
        .addOnFailureListener { if (continuazione.isActive) continuazione.resume(null) }
}
