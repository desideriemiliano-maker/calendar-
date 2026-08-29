package com.desideri.viaggiotemplate.data.remote.drive

import com.desideri.viaggiotemplate.domain.backup.BackupDriveException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Metadati di un file trovato nell'App Data folder (cartella nascosta, privata di quest'app, dentro il Drive dell'utente). */
data class FileDrive(val id: String, val nome: String, val modifiedTimeIso: String, val dimensioneByte: Long?)

/**
 * Legge/scrive un singolo file nell'App Data folder di Google Drive (`spaces=appDataFolder`),
 * visibile solo a quest'app e mai all'utente in un client Drive normale. Richiede un access token
 * già autorizzato con lo scope `drive.appdata` (vedi [com.desideri.viaggiotemplate.domain.backup.GoogleDriveAutorizzatore]).
 *
 * Chiamate REST dirette via [HttpURLConnection] (come [com.desideri.viaggiotemplate.data.remote.OrariItaloClient]
 * e gli altri client orari) invece delle librerie `google-api-client`/`google-api-services-drive`:
 * evita di aggiungere quelle dipendenze (pesanti, pensate per Java server-side) solo per due
 * chiamate REST molto semplici.
 */
class DriveAppDataClient {

    private val baseUrl = "https://www.googleapis.com/drive/v3/files"
    private val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files"

    /** Cerca il backup per nome nell'App Data folder. Null se non esiste ancora nessun backup. */
    fun trovaBackup(accessToken: String, nomeFile: String): FileDrive? {
        val query = "name='${nomeFile.replace("'", "\\'")}' and trashed=false"
        val url = "$baseUrl?spaces=appDataFolder" +
            "&q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
            "&fields=${java.net.URLEncoder.encode("files(id,name,modifiedTime,size)", "UTF-8")}"
        val risposta = eseguiGet(url, accessToken, "ricerca del backup")
        val file: JSONArray = risposta.optJSONArray("files") ?: JSONArray()
        if (file.length() == 0) return null
        val primo = file.getJSONObject(0)
        return primo.toFileDrive()
    }

    /** Scarica il contenuto binario del file [fileId]. */
    fun scarica(accessToken: String, fileId: String): ByteArray {
        val connessione = apri("$baseUrl/$fileId?alt=media", accessToken, "GET")
        return try {
            verificaCodiceRisposta(connessione, "download del backup")
            connessione.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            throw BackupDriveException.ErroreRete("download del backup", e)
        } finally {
            connessione.disconnect()
        }
    }

    /**
     * Crea (se [fileIdEsistente] è null) o aggiorna (altrimenti) il backup nell'App Data folder con
     * [contenuto]. Ritorna l'id del file (nuovo o esistente), utile per riusarlo nel prossimo backup
     * senza dover rifare una ricerca.
     */
    fun carica(accessToken: String, fileIdEsistente: String?, nomeFile: String, contenuto: ByteArray): String {
        val metadata = JSONObject().apply {
            put("name", nomeFile)
            if (fileIdEsistente == null) put("parents", JSONArray().put("appDataFolder"))
        }
        val url = if (fileIdEsistente == null) "$uploadUrl?uploadType=multipart" else "$uploadUrl/$fileIdEsistente?uploadType=multipart"
        val risposta = eseguiUploadMultipart(url, accessToken, metadata, contenuto, aggiornamento = fileIdEsistente != null)
        return risposta.getString("id")
    }

    private fun JSONObject.toFileDrive() = FileDrive(
        id = getString("id"),
        nome = getString("name"),
        modifiedTimeIso = optString("modifiedTime"),
        dimensioneByte = optString("size").toLongOrNull()
    )

    private fun apri(url: String, accessToken: String, metodo: String): HttpURLConnection {
        val connessione = URL(url).openConnection() as HttpURLConnection
        connessione.connectTimeout = 15_000
        connessione.readTimeout = 30_000
        connessione.requestMethod = metodo
        connessione.setRequestProperty("Authorization", "Bearer $accessToken")
        return connessione
    }

    private fun eseguiGet(url: String, accessToken: String, operazione: String): JSONObject {
        val connessione = apri(url, accessToken, "GET")
        return try {
            verificaCodiceRisposta(connessione, operazione)
            JSONObject(connessione.inputStream.bufferedReader().use { it.readText() })
        } catch (e: IOException) {
            throw BackupDriveException.ErroreRete(operazione, e)
        } finally {
            connessione.disconnect()
        }
    }

    /**
     * L'aggiornamento di un file esistente su Drive richiede il metodo HTTP PATCH, ma
     * `java.net.HttpURLConnection` accetta solo la lista fissa di metodi standard (GET/POST/HEAD/
     * OPTIONS/PUT/DELETE/TRACE) e rifiuta "PATCH" con `ProtocolException`. Le API Google supportano
     * ufficialmente l'override via header per questo esatto motivo: si manda un POST con
     * `X-HTTP-Method-Override: PATCH` invece di forzare PATCH a basso livello con reflection.
     */
    private fun eseguiUploadMultipart(
        url: String,
        accessToken: String,
        metadata: JSONObject,
        contenuto: ByteArray,
        aggiornamento: Boolean
    ): JSONObject {
        val boundary = "----CalendarioPlusPlusBackup${System.currentTimeMillis()}"
        val connessione = apri(url, accessToken, "POST")
        if (aggiornamento) connessione.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        connessione.doOutput = true
        connessione.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

        val preambolo = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--").append(boundary).append("\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val epilogo = "\r\n--$boundary--".toByteArray(Charsets.UTF_8)

        connessione.setFixedLengthStreamingMode(preambolo.size + contenuto.size + epilogo.size)

        return try {
            connessione.outputStream.use { out ->
                out.write(preambolo)
                out.write(contenuto)
                out.write(epilogo)
            }
            verificaCodiceRisposta(connessione, "caricamento del backup")
            JSONObject(connessione.inputStream.bufferedReader().use { it.readText() })
        } catch (e: IOException) {
            throw BackupDriveException.ErroreRete("caricamento del backup", e)
        } finally {
            connessione.disconnect()
        }
    }

    private fun verificaCodiceRisposta(connessione: HttpURLConnection, operazione: String) {
        val codice = connessione.responseCode
        if (codice !in 200..299) {
            val corpoErrore = connessione.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw BackupDriveException.ErroreRete(operazione, IOException("HTTP $codice: $corpoErrore"))
        }
    }
}
