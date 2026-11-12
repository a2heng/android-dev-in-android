---
name: Android Database
description: Store data on Android with Room, SQLite or DataStore, including schema, migrations, threading and testing. Use when the app needs persistence.
---

# Android Database

## Choose

- **DataStore (Preferences/Proto)**: small key-value settings, no schema.
- **Room**: relational data, queries, migrations, Flow support.
- **Raw SQLite**: only for tiny cases or existing code; Room is safer.

## Room setup

```groovy
implementation 'androidx.room:room-runtime:2.6.1'
annotationProcessor 'androidx.room:room-compiler:2.6.1'
// Kotlin: use ksp/kapt matching the Kotlin plugin
```

```kotlin
@Entity data class Note(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String)

@Dao interface NoteDao {
    @Query("SELECT * FROM Note ORDER BY id DESC") fun all(): Flow<List<Note>>
    @Insert suspend fun insert(note: Note): Long
    @Delete suspend fun delete(note: Note)
}

@Database(entities = [Note::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun notes(): NoteDao
}
```

## Rules

- Never access the database on the main thread.
- Use `suspend`/`Flow`; Room enforces it for queries.
- Every schema change needs a migration or `fallbackToDestructiveMigration`
  only for debug builds.
- Export the schema (`room.schemaLocation`) so migrations are reviewable.
- Test with an in-memory database (`Room.inMemoryDatabaseBuilder`).
- Back up/restore is the app's responsibility; `allowBackup` may be disabled
  for token-bearing apps.

## Check dependencies

Room artifacts may not be in the offline cache. Check with a small build; if
missing, ask before `OC_BUILD_ONLINE=1`.

## Debug on device

```sh
oc-proot 'adb shell run-as com.example.app ls databases'
oc-proot "adb shell run-as com.example.app sqlite3 databases/app.db '.tables'"
```

Run adb inside proot; `ADB=$HOME/toolchain/...; "$ADB"` outside proot gives
`Permission denied`.

`sqlite3` may not be present on the device; do not depend on it for the app.
