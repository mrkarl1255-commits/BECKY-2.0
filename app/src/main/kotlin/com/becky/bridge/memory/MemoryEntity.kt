package com.becky.bridge.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room table row for a persistent memory entry (Fase 3.2, Bloque 2).
 *
 * Deliberately kept as a SEPARATE type from the domain model [MemoryEntry]
 * (see [MemoryEntry.toEntity]/[MemoryEntity.toDomain] below): Room
 * annotations never leak into the domain/model layer, and the domain layer
 * never needs to know about SQLite column types. [MemoryType]/[MemoryOrigin]
 * are stored as their [Enum.name] string (see [typeName]/[originName]) since
 * Room does not persist enums directly.
 *
 * Only the four *persistent* [MemoryType] values are ever written here -
 * [MemoryType.WORKING] never reaches this table, enforced by
 * [MemoryManager]/[MemoryRepository] (never by this entity itself, which
 * intentionally has no business-rule knowledge).
 */
@Entity(tableName = "memory_entries")
data class MemoryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "type_name")
    val typeName: String,

    val content: String,

    val timestamp: Long,

    val importance: Float,

    @ColumnInfo(name = "origin_name")
    val originName: String,

    val confidence: Float,

    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,

    val updatable: Boolean,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false
)

/** Maps this domain [MemoryEntry] to its Room row representation. */
fun MemoryEntry.toEntity(): MemoryEntity = MemoryEntity(
    id = id,
    typeName = type.name,
    content = content,
    timestamp = timestamp,
    importance = importance,
    originName = origin.name,
    confidence = confidence,
    lastAccessedAt = lastAccessedAt,
    updatable = updatable,
    isDeleted = isDeleted
)

/**
 * Maps this Room row back to the domain [MemoryEntry]. Returns null if
 * [typeName]/[originName] no longer match a known enum constant (defensive
 * against a corrupted/foreign-written row) rather than throwing, matching
 * the same defensive-parsing philosophy used in
 * [com.becky.bridge.identity.DataStoreIdentityRepository].
 */
fun MemoryEntity.toDomainOrNull(): MemoryEntry? {
    val type = runCatching { MemoryType.valueOf(typeName) }.getOrNull() ?: return null
    val origin = runCatching { MemoryOrigin.valueOf(originName) }.getOrNull() ?: return null
    return MemoryEntry(
        id = id,
        type = type,
        content = content,
        timestamp = timestamp,
        importance = importance,
        origin = origin,
        confidence = confidence,
        lastAccessedAt = lastAccessedAt,
        updatable = updatable,
        isDeleted = isDeleted
    )
}
