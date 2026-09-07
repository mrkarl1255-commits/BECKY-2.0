package com.becky.bridge.identity

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Persists [BeckyIdentity] across app restarts (Fase 3.2, Bloque 1).
 *
 * Uses [androidx.datastore.preferences] (already declared in
 * `app/build.gradle.kts` since before this block, unused until now -
 * no new Gradle dependency was added for this file) as decided in the
 * Fase 3.2 audit/plan: Identity is a single small "document", not a
 * collection that needs SQL-style querying, so DataStore Preferences
 * is a better fit than Room here (Room is reserved for Memory in a
 * later Bloque).
 *
 * Every [BeckyIdentity] field maps to its OWN preference key (see
 * [Keys]) rather than serializing the whole object into one giant
 * JSON blob, per the explicit requirement in the project spec
 * (section 6): each field stays individually addressable/inspectable
 * (e.g. for a future diagnostics screen reading a single key), while
 * only the handful of genuinely structured/collection fields
 * (traits, principles, preferences, communicationStyle,
 * evolutionLog) use a small JSON *value* inside their own single key
 * - never one blob holding the entire identity.
 */
interface IdentityRepository {
    /** Loads the persisted identity, or null if none has ever been saved. */
    suspend fun load(): BeckyIdentity?

    /** Persists [identity], overwriting any previously saved identity. */
    suspend fun save(identity: BeckyIdentity)

    /** Reactive stream of the persisted identity (null until first saved). */
    fun observe(): Flow<BeckyIdentity?>
}

private val Context.identityDataStore by preferencesDataStore(name = "becky_identity")

/**
 * Default [IdentityRepository] backed by Jetpack DataStore Preferences.
 *
 * @param context application context (only [Context.applicationContext] is
 *   ever retained, matching the pattern already used by
 *   [com.becky.bridge.bluetooth.BridgeRepository] and
 *   [com.becky.bridge.voice.VoiceRepository]).
 */
class DataStoreIdentityRepository(context: Context) : IdentityRepository {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val NAME = stringPreferencesKey("name")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val CREATED_AT = longPreferencesKey("created_at")
        val IDENTITY_VERSION = intPreferencesKey("identity_version")
        val PERSONALITY_VERSION = intPreferencesKey("personality_version")
        val TRAITS_JSON = stringPreferencesKey("traits_json")
        val PRINCIPLES_JSON = stringPreferencesKey("principles_json")
        val PREFERENCES_JSON = stringPreferencesKey("preferences_json")
        val COMMUNICATION_STYLE_JSON = stringPreferencesKey("communication_style_json")
        val ORIGIN_STORY = stringPreferencesKey("origin_story")
        val RELATIONSHIP_CONTEXT = stringPreferencesKey("relationship_context")
        val EVOLUTION_LOG_JSON = stringPreferencesKey("evolution_log_json")
    }

    override suspend fun load(): BeckyIdentity? {
        val prefs = appContext.identityDataStore.data.first()
        return prefs.toIdentityOrNull()
    }

    override fun observe(): Flow<BeckyIdentity?> =
        appContext.identityDataStore.data.map { prefs -> prefs.toIdentityOrNull() }

    override suspend fun save(identity: BeckyIdentity) {
        appContext.identityDataStore.edit { prefs ->
            prefs[Keys.NAME] = identity.name
            prefs[Keys.DISPLAY_NAME] = identity.displayName
            prefs[Keys.CREATED_AT] = identity.createdAt
            prefs[Keys.IDENTITY_VERSION] = identity.identityVersion
            prefs[Keys.PERSONALITY_VERSION] = identity.personalityVersion
            prefs[Keys.TRAITS_JSON] = json.encodeToString(
                ListSerializer(PersonalityTrait.serializer()),
                identity.traits
            )
            prefs[Keys.PRINCIPLES_JSON] = json.encodeToString(
                ListSerializer(String.serializer()),
                identity.principles
            )
            prefs[Keys.PREFERENCES_JSON] = json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                identity.preferences
            )
            prefs[Keys.COMMUNICATION_STYLE_JSON] =
                json.encodeToString(CommunicationStyle.serializer(), identity.communicationStyle)
            prefs[Keys.ORIGIN_STORY] = identity.originStory
            prefs[Keys.RELATIONSHIP_CONTEXT] = identity.relationshipContext
            prefs[Keys.EVOLUTION_LOG_JSON] = json.encodeToString(
                ListSerializer(PersonalityEvolutionRecord.serializer()),
                identity.evolutionLog
            )
        }
    }

    /** Returns null if no identity has ever been saved (i.e. [Keys.CREATED_AT] is absent). */
    private fun Preferences.toIdentityOrNull(): BeckyIdentity? {
        val createdAt = this[Keys.CREATED_AT] ?: return null
        return BeckyIdentity(
            name = this[Keys.NAME] ?: "BECKY",
            displayName = this[Keys.DISPLAY_NAME] ?: "Rebecca",
            createdAt = createdAt,
            identityVersion = this[Keys.IDENTITY_VERSION] ?: 1,
            personalityVersion = this[Keys.PERSONALITY_VERSION] ?: 1,
            traits = this[Keys.TRAITS_JSON]?.let {
                runCatching { json.decodeFromString(ListSerializer(PersonalityTrait.serializer()), it) }.getOrNull()
            } ?: BeckyIdentity.DEFAULT_TRAITS,
            principles = this[Keys.PRINCIPLES_JSON]?.let {
                runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
            } ?: BeckyIdentity.DEFAULT_PRINCIPLES,
            preferences = this[Keys.PREFERENCES_JSON]?.let {
                runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull()
            } ?: emptyMap(),
            communicationStyle = this[Keys.COMMUNICATION_STYLE_JSON]?.let {
                runCatching { json.decodeFromString(CommunicationStyle.serializer(), it) }.getOrNull()
            } ?: CommunicationStyle(),
            originStory = this[Keys.ORIGIN_STORY] ?: BeckyIdentity.DEFAULT_ORIGIN_STORY,
            relationshipContext = this[Keys.RELATIONSHIP_CONTEXT] ?: BeckyIdentity.DEFAULT_RELATIONSHIP_CONTEXT,
            evolutionLog = this[Keys.EVOLUTION_LOG_JSON]?.let {
                runCatching { json.decodeFromString(ListSerializer(PersonalityEvolutionRecord.serializer()), it) }.getOrNull()
            } ?: emptyList()
        )
    }
}
