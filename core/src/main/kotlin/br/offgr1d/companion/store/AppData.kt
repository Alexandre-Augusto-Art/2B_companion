package br.offgr1d.companion.store

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
)

@Serializable
data class SavedMessage(val fromUser: Boolean, val text: String)

class AppData(private val store: BlobStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun settings(): Settings = store.read("settings")?.let { json.decodeFromString<Settings>(it.toString(Charsets.UTF_8)) } ?: Settings()
    fun save(s: Settings) = store.write("settings", json.encodeToString(Settings.serializer(), s).toByteArray())

    fun memories(): List<String> = store.read("memories")?.let { json.decodeFromString<List<String>>(it.toString(Charsets.UTF_8)) }.orEmpty()
    fun saveMemories(m: List<String>) =
        if (m.isEmpty()) store.delete("memories") else store.write("memories", json.encodeToString(m).toByteArray())

    fun history(): List<SavedMessage> =
        store.read("history")?.let { json.decodeFromString<List<SavedMessage>>(it.toString(Charsets.UTF_8)) }.orEmpty()
    fun saveHistory(m: List<SavedMessage>) =
        if (m.isEmpty()) store.delete("history") else store.write("history", json.encodeToString(m.takeLast(100)).toByteArray())
}
