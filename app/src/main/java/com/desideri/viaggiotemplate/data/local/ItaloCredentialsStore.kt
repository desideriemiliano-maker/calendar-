package com.desideri.viaggiotemplate.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credenziali di un account Italo (NTV) reale, alternative al login "guest" di default. */
data class CredenzialiItalo(val username: String, val password: String)

/**
 * Persiste le credenziali di un account Italo reale, usate da `OrariItaloClient` al posto del
 * login "guest" pubblico (bloccato lato server dal 2026 in poi). Username e password sono cifrati
 * con una chiave AES-256/GCM custodita nell'Android Keystore (mai esportabile dal dispositivo):
 * su disco finiscono solo IV + testo cifrato, codificati in Base64, in normali SharedPreferences.
 * Si usa direttamente Keystore + [Cipher] invece di `androidx.security.crypto.EncryptedSharedPreferences`
 * perche' quest'ultima e' stata deprecata da Google (aprile 2025) proprio a favore di questo
 * pattern, evitando cosi' anche di aggiungere una dipendenza.
 */
class ItaloCredentialsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("italo_credenziali", Context.MODE_PRIVATE)

    var credenziali: CredenzialiItalo?
        get() {
            val username = decifra(prefs.getString(CHIAVE_USERNAME, null)) ?: return null
            val password = decifra(prefs.getString(CHIAVE_PASSWORD, null)) ?: return null
            return CredenzialiItalo(username, password)
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(CHIAVE_USERNAME).remove(CHIAVE_PASSWORD).apply()
            } else {
                prefs.edit()
                    .putString(CHIAVE_USERNAME, cifra(value.username))
                    .putString(CHIAVE_PASSWORD, cifra(value.password))
                    .apply()
            }
        }

    private fun chiaveSegreta(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS_CHIAVE, null) as? SecretKey)?.let { return it }
        val generatore = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generatore.init(
            KeyGenParameterSpec.Builder(ALIAS_CHIAVE, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generatore.generateKey()
    }

    /** IV (12 byte, casuale ad ogni chiamata perche' generato da [Cipher] stesso) + testo cifrato, in Base64. */
    private fun cifra(testo: String): String {
        val cipher = Cipher.getInstance(TRASFORMAZIONE).apply { init(Cipher.ENCRYPT_MODE, chiaveSegreta()) }
        val cifrato = cipher.doFinal(testo.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cifrato, Base64.NO_WRAP)
    }

    private fun decifra(valore: String?): String? {
        if (valore == null) return null
        return try {
            val bytes = Base64.decode(valore, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, DIMENSIONE_IV)
            val cifrato = bytes.copyOfRange(DIMENSIONE_IV, bytes.size)
            val cipher = Cipher.getInstance(TRASFORMAZIONE).apply {
                init(Cipher.DECRYPT_MODE, chiaveSegreta(), GCMParameterSpec(TAG_BIT, iv))
            }
            String(cipher.doFinal(cifrato), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val ALIAS_CHIAVE = "italo_credenziali_key"
        const val TRASFORMAZIONE = "AES/GCM/NoPadding"
        const val DIMENSIONE_IV = 12
        const val TAG_BIT = 128
        const val CHIAVE_USERNAME = "username"
        const val CHIAVE_PASSWORD = "password"
    }
}
