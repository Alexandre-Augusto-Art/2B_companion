package br.offgr1d.companion.store

import br.offgr1d.companion.alerts.AlertState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Padrões de privacidade: nada é guardado nem sai do aparelho sem a pessoa ligar. */
@Serializable
data class Settings(
    val modelId: String = "gemma-4-e2b",
    val useGpu: Boolean = false,
    val useTor: Boolean = false,
    val searxUrl: String = "",
    val saveHistory: Boolean = false,
    val blockScreenshots: Boolean = true,
    /** Opções que ficam ligadas até a pessoa desligar. */
    val web: Boolean = false,
    val autoScreen: Boolean = false,
    val autoClear: Boolean = false,
    val alertW4k3: Boolean = false,
    val alertQuote: Boolean = false,
    val alertActs: Boolean = false,
    /** Vazio = todos os estados. */
    val alertUfs: List<String> = emptyList(),
    /** Já respondeu se quer avisos. */
    val alertsAsked: Boolean = false,
) {
    val anyAlert get() = alertW4k3 || alertQuote || alertActs
}

@Serializable
data class SavedMessage(val fromUser: Boolean, val text: String)

class AppData(private val store: BlobStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun settings(): Settings = store.read("settings")?.let { json.decodeFromString<Settings>(it.toString(Charsets.UTF_8)) } ?: Settings()
    fun save(s: Settings) = store.write("settings", json.encodeToString(Settings.serializer(), s).toByteArray())

    fun memories(): List<String> = store.read("memories")?.let { json.decodeFromString<List<String>>(it.toString(Charsets.UTF_8)) }.orEmpty()
    fun saveMemories(m: List<String>) =
        if (m.isEmpty()) store.delete("memories") else store.write("memories", json.encodeToString(m).toByteArray())

    fun alerts(): AlertState = store.read("alerts")?.let { json.decodeFromString<AlertState>(it.toString(Charsets.UTF_8)) } ?: AlertState()
    fun saveAlerts(a: AlertState) = store.write("alerts", json.encodeToString(AlertState.serializer(), a).toByteArray())

    fun history(): List<SavedMessage> =
        store.read("history")?.let { json.decodeFromString<List<SavedMessage>>(it.toString(Charsets.UTF_8)) }.orEmpty()
    fun saveHistory(m: List<SavedMessage>) =
        if (m.isEmpty()) store.delete("history") else store.write("history", json.encodeToString(m.takeLast(100)).toByteArray())
}
