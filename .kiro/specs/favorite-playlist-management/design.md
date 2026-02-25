# Design Document: Favorite Songs and Playlist Management

## Overview

This design implements favorite song tracking and playlist management features for an Android music player application. The solution extends the existing Room database schema, adds UI controls for favoriting songs, and provides playlist management capabilities while maintaining the existing repository pattern and Jetpack Compose architecture.

The implementation is divided into three phases:
1. **Favorite Songs**: Add favorite tracking to songs with UI toggles and automatic Favorite playlist synchronization
2. **Rename Playlist**: Enable renaming of user-created playlists with validation
3. **Manage Playlist Songs**: Add and remove songs from user playlists

## Architecture

### Database Layer

**Schema Changes (Migration v4 → v5)**:
- Add `isFavorite: Boolean` field to `Song` entity with default value `false`
- No changes to `Playlist` or `PlaylistSongCrossRef` entities
- Migration strategy: `ALTER TABLE songs ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0`

**Database Structure**:
```kotlin
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val album: String,
    val coverPageLink: String,
    val link: String,
    val lastPlayedAt: Long?,
    val downloadedAt: Long?,
    val isFavorite: Boolean = false  // NEW FIELD
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isSystem: Boolean = false
)

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String
)
```

### Repository Layer

**SongRepository Extensions**:
```kotlin
interface SongRepository {
    // Existing methods...
    
    // New methods for favorite management
    suspend fun toggleFavorite(songId: String): Boolean
    suspend fun setFavorite(songId: String, isFavorite: Boolean)
    fun observeFavoriteStatus(songId: String): Flow<Boolean>
}
```

**PlaylistRepository Extensions**:
```kotlin
interface PlaylistRepository {
    // Existing methods...
    
    // New methods for playlist management
    suspend fun renamePlaylist(playlistId: Long, newName: String): Result<Unit>
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<String>)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)
    suspend fun isPlaylistNameUnique(name: String, excludeId: Long? = null): Boolean
    suspend fun getFavoritePlaylistId(): Long
}
```

**Implementation Details**:

The `toggleFavorite` method will:
1. Query current favorite status
2. Toggle the boolean value
3. Update the Song entity
4. If favorited: add to Favorite playlist via PlaylistSongCrossRef
5. If unfavorited: remove from Favorite playlist
6. Return new favorite status

The `renamePlaylist` method will:
1. Validate name is not empty
2. Check name uniqueness (excluding current playlist)
3. Verify playlist is not a system playlist
4. Update playlist name
5. Return Result.success or Result.failure with error message

### ViewModel Layer

**PlayerViewModel Extensions**:
```kotlin
class PlayerViewModel : ViewModel() {
    // Existing properties...
    
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()
    
    fun toggleFavorite() {
        viewModelScope.launch {
            currentSong.value?.let { song ->
                val newStatus = songRepository.toggleFavorite(song.id)
                _isFavorite.value = newStatus
            }
        }
    }
    
    private fun observeCurrentSongFavorite(songId: String) {
        viewModelScope.launch {
            songRepository.observeFavoriteStatus(songId)
                .collect { isFavorite ->
                    _isFavorite.value = isFavorite
                }
        }
    }
}
```

**PlaylistDetailViewModel Extensions**:
```kotlin
class PlaylistDetailViewModel : ViewModel() {
    // Existing properties...
    
    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()
    
    private val _showAddSongsDialog = MutableStateFlow(false)
    val showAddSongsDialog: StateFlow<Boolean> = _showAddSongsDialog.asStateFlow()
    
    private val _availableSongs = MutableStateFlow<List<Song>>(emptyList())
    val availableSongs: StateFlow<List<Song>> = _availableSongs.asStateFlow()
    
    private val _renameError = MutableStateFlow<String?>(null)
    val renameError: StateFlow<String?> = _renameError.asStateFlow()
    
    fun toggleSongFavorite(songId: String) {
        viewModelScope.launch {
            songRepository.toggleFavorite(songId)
        }
    }
    
    fun showRenameDialog() {
        if (currentPlaylist.value?.isSystem == false) {
            _showRenameDialog.value = true
        }
    }
    
    fun hideRenameDialog() {
        _showRenameDialog.value = false
        _renameError.value = null
    }
    
    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            val trimmedName = newName.trim()
            if (trimmedName.isEmpty()) {
                _renameError.value = "Playlist name cannot be empty"
                return@launch
            }
            
            currentPlaylist.value?.let { playlist ->
                val result = playlistRepository.renamePlaylist(playlist.id, trimmedName)
                result.fold(
                    onSuccess = { hideRenameDialog() },
                    onFailure = { error -> _renameError.value = error.message }
                )
            }
        }
    }
    
    fun showAddSongsDialog() {
        if (currentPlaylist.value?.isSystem == false) {
            viewModelScope.launch {
                _availableSongs.value = songRepository.getAllSongs()
                _showAddSongsDialog.value = true
            }
        }
    }
    
    fun hideAddSongsDialog() {
        _showAddSongsDialog.value = false
    }
    
    fun addSongsToPlaylist(songIds: List<String>) {
        viewModelScope.launch {
            currentPlaylist.value?.let { playlist ->
                playlistRepository.addSongsToPlaylist(playlist.id, songIds)
                hideAddSongsDialog()
            }
        }
    }
    
    fun removeSongFromPlaylist(songId: String) {
        viewModelScope.launch {
            currentPlaylist.value?.let { playlist ->
                if (!playlist.isSystem) {
                    playlistRepository.removeSongFromPlaylist(playlist.id, songId)
                }
            }
        }
    }
}
```

## Components and Interfaces

### UI Components

**FavoriteIconButton**:
A reusable composable for displaying and toggling favorite status.

```kotlin
@Composable
fun FavoriteIconButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = if (isFavorite) Color(0xFFFF69B4) else MaterialTheme.colorScheme.onSurface
        )
    }
}
```

**RenamePlaylistDialog**:
A dialog for renaming playlists with validation.

```kotlin
@Composable
fun RenamePlaylistDialog(
    currentName: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    isError = errorMessage != null,
                    singleLine = true
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

**AddSongsDialog**:
A dialog for selecting multiple songs to add to a playlist.

```kotlin
@Composable
fun AddSongsDialog(
    availableSongs: List<Song>,
    currentPlaylistSongIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectedSongIds = remember { mutableStateListOf<String>() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Songs") },
        text = {
            LazyColumn {
                items(availableSongs) { song ->
                    val isInPlaylist = currentPlaylistSongIds.contains(song.id)
                    val isSelected = selectedSongIds.contains(song.id)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isInPlaylist) {
                                if (isSelected) {
                                    selectedSongIds.remove(song.id)
                                } else {
                                    selectedSongIds.add(song.id)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected || isInPlaylist,
                            onCheckedChange = null,
                            enabled = !isInPlaylist
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(song.name)
                            Text(
                                text = song.author,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedSongIds.toList()) },
                enabled = selectedSongIds.isNotEmpty()
            ) {
                Text("Add (${selectedSongIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

### Screen Updates

**PlaylistDetailScreen Modifications**:
1. Add FavoriteIconButton to each song item in the list
2. Add rename menu option in top app bar (only for user playlists)
3. Add "Add Songs" floating action button (only for user playlists)
4. Add swipe-to-delete gesture for song items (only for user playlists)
5. Display RenamePlaylistDialog when rename is triggered
6. Display AddSongsDialog when add songs is triggered

**PlayerScreen Modifications**:
1. Add FavoriteIconButton near song title/artist information
2. Observe favorite status from PlayerViewModel
3. Update icon state when song changes or favorite status changes

## Data Models

### Song Entity Update

```kotlin
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val album: String,
    val coverPageLink: String,
    val link: String,
    val lastPlayedAt: Long?,
    val downloadedAt: Long?,
    val isFavorite: Boolean = false
)
```

### Database Migration

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isFavorite column to songs table
        database.execSQL(
            "ALTER TABLE songs ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
        )
    }
}
```

### DAO Updates

**SongDao**:
```kotlin
@Dao
interface SongDao {
    // Existing methods...
    
    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: String, isFavorite: Boolean)
    
    @Query("SELECT isFavorite FROM songs WHERE id = :songId")
    fun observeFavoriteStatus(songId: String): Flow<Boolean>
    
    @Query("SELECT * FROM songs WHERE isFavorite = 1")
    fun getFavoriteSongs(): Flow<List<Song>>
}
```

**PlaylistDao**:
```kotlin
@Dao
interface PlaylistDao {
    // Existing methods...
    
    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, newName: String)
    
    @Query("SELECT COUNT(*) FROM playlists WHERE name = :name AND id != :excludeId")
    suspend fun countPlaylistsWithName(name: String, excludeId: Long): Int
    
    @Query("SELECT id FROM playlists WHERE name = 'Favorite' AND isSystem = 1")
    suspend fun getFavoritePlaylistId(): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)
    
    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSongCrossRef(playlistId: Long, songId: String)
}
```


## Correctness Properties

A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.

### Property 1: Database Migration Preserves Data

*For any* set of songs existing before migration, executing the migration from version 4 to version 5 should preserve all song data (id, name, author, album, coverPageLink, link, lastPlayedAt, downloadedAt) and add the isFavorite field with default value false.

**Validates: Requirements 1.3**

### Property 2: Heart Icon Reflects Favorite Status

*For any* song, the heart icon displayed in the UI should be filled when isFavorite is true and outline when isFavorite is false.

**Validates: Requirements 2.2, 2.3, 3.2, 3.3**

### Property 3: Toggle Favorite Flips Boolean

*For any* song, toggling the favorite status should flip the isFavorite field from true to false or false to true.

**Validates: Requirements 2.4, 3.4**

### Property 4: Favorite Songs Have Playlist Cross-Reference

*For any* song where isFavorite is true, a PlaylistSongCrossRef entry should exist linking the song to the Favorite system playlist.

**Validates: Requirements 2.5, 3.5, 4.1**

### Property 5: Unfavorited Songs Lack Playlist Cross-Reference

*For any* song where isFavorite is false, no PlaylistSongCrossRef entry should exist linking the song to the Favorite system playlist.

**Validates: Requirements 2.6, 3.6, 4.2**

### Property 6: Favorite Playlist Contains Only Favorite Songs

*For any* query of the Favorite system playlist, all returned songs should have isFavorite set to true, and all songs with isFavorite set to true should be in the results.

**Validates: Requirements 4.3**

### Property 7: Favorite Toggle Round Trip

*For any* song, toggling favorite twice (favorite → unfavorite → favorite or unfavorite → favorite → unfavorite) should result in the same state as toggling once, with correct playlist cross-reference state.

**Validates: Requirements 2.5, 2.6, 3.5, 3.6**

### Property 8: Empty Playlist Names Rejected

*For any* string composed entirely of whitespace characters (including empty string), attempting to rename a playlist to that name should fail with an error message.

**Validates: Requirements 5.4**

### Property 9: Duplicate Playlist Names Rejected

*For any* existing playlist name, attempting to rename a different playlist to that name should fail with an error message.

**Validates: Requirements 5.5**

### Property 10: Valid Unique Names Accepted

*For any* non-empty, non-whitespace, unique playlist name, renaming a user playlist to that name should succeed and update the database.

**Validates: Requirements 5.6**

### Property 11: Adding Songs Creates Cross-References

*For any* list of song IDs and user playlist, adding those songs to the playlist should create PlaylistSongCrossRef entries for each song-playlist pair.

**Validates: Requirements 6.5**

### Property 12: Duplicate Song Addition Prevented

*For any* song already in a playlist, attempting to add that song again should not create duplicate PlaylistSongCrossRef entries.

**Validates: Requirements 6.7**

### Property 13: Removing Song Deletes Cross-Reference Only

*For any* song in a user playlist, removing the song should delete the PlaylistSongCrossRef entry but preserve the Song entity in the database.

**Validates: Requirements 7.3, 7.5**

### Property 14: Playlist Removal Preserves Favorite Status

*For any* song removed from a user playlist, the song's isFavorite field should remain unchanged.

**Validates: Requirements 7.6**

### Property 15: Database Errors Handled Gracefully

*For any* database operation that throws an exception, the system should catch the exception and return an error result without crashing.

**Validates: Requirements 9.4**

## Error Handling

### Database Errors

**Migration Failures**:
- If migration from v4 to v5 fails, Room will throw an exception
- The app should catch this and potentially fall back to destructive migration (with user warning)
- Log migration errors for debugging

**Repository Operation Failures**:
- All repository methods should return `Result<T>` or nullable types
- Catch SQLiteException and other database exceptions
- Return meaningful error messages to ViewModels
- ViewModels should expose error states to UI

**Favorite Toggle Errors**:
- If song doesn't exist: log error, no-op
- If Favorite playlist doesn't exist: create it or log critical error
- If cross-ref insertion fails: rollback favorite status change

**Playlist Rename Errors**:
- Empty name: return validation error
- Duplicate name: return validation error
- System playlist: return permission error
- Database error: return generic error with logging

**Add/Remove Song Errors**:
- Song doesn't exist: filter out invalid IDs
- Playlist doesn't exist: return error
- System playlist modification: return permission error
- Database error: return generic error with logging

### UI Error Display

**Error Messages**:
- Validation errors: display in dialog with red text
- Database errors: show Snackbar with retry option
- Permission errors: show Snackbar explaining restriction

**Error Recovery**:
- Retry mechanism for transient database errors
- Refresh UI state after errors to ensure consistency
- Log all errors for debugging

## Testing Strategy

### Unit Testing

Unit tests will verify specific examples, edge cases, and error conditions:

**Database Layer Tests**:
- Test migration from v4 to v5 with sample data
- Test DAO methods with specific inputs
- Test foreign key constraints
- Test unique constraint on playlist names

**Repository Layer Tests**:
- Test toggleFavorite with specific songs
- Test renamePlaylist with edge cases (empty, duplicate, system playlist)
- Test addSongsToPlaylist with empty list, single song, multiple songs
- Test removeSongFromPlaylist with valid and invalid IDs
- Test error handling with mocked database failures

**ViewModel Layer Tests**:
- Test state updates with specific user actions
- Test error state propagation
- Test dialog visibility state management

**UI Component Tests**:
- Test FavoriteIconButton renders correct icon for true/false
- Test RenamePlaylistDialog displays error messages
- Test AddSongsDialog selection state management

### Property-Based Testing

Property-based tests will verify universal properties across randomized inputs using a Kotlin property-based testing library (e.g., Kotest Property Testing or junit-quickcheck).

**Configuration**:
- Minimum 100 iterations per property test
- Each test tagged with: `@Tag("Feature: favorite-playlist-management, Property N: [property text]")`

**Property Test Suite**:

1. **Property 1: Database Migration Preserves Data**
   - Generate random list of songs
   - Insert into v4 database
   - Run migration
   - Verify all songs exist with same data plus isFavorite=false

2. **Property 2: Heart Icon Reflects Favorite Status**
   - Generate random songs with random isFavorite values
   - Verify icon type matches isFavorite (filled vs outline)

3. **Property 3: Toggle Favorite Flips Boolean**
   - Generate random song with random initial isFavorite
   - Toggle favorite
   - Verify isFavorite is flipped

4. **Property 4: Favorite Songs Have Playlist Cross-Reference**
   - Generate random songs with isFavorite=true
   - Verify cross-ref exists for each

5. **Property 5: Unfavorited Songs Lack Playlist Cross-Reference**
   - Generate random songs with isFavorite=false
   - Verify no cross-ref exists for each

6. **Property 6: Favorite Playlist Contains Only Favorite Songs**
   - Generate random mix of favorite and non-favorite songs
   - Query Favorite playlist
   - Verify all results have isFavorite=true
   - Verify all favorite songs are in results

7. **Property 7: Favorite Toggle Round Trip**
   - Generate random song with random initial state
   - Toggle twice
   - Verify state matches single toggle
   - Verify cross-ref state is correct

8. **Property 8: Empty Playlist Names Rejected**
   - Generate random whitespace strings (spaces, tabs, newlines, empty)
   - Attempt rename
   - Verify all fail with error

9. **Property 9: Duplicate Playlist Names Rejected**
   - Generate random existing playlist names
   - Attempt to rename different playlist to existing name
   - Verify all fail with error

10. **Property 10: Valid Unique Names Accepted**
    - Generate random non-empty, non-whitespace, unique names
    - Rename playlist
    - Verify all succeed

11. **Property 11: Adding Songs Creates Cross-References**
    - Generate random list of song IDs
    - Add to random user playlist
    - Verify cross-ref exists for each

12. **Property 12: Duplicate Song Addition Prevented**
    - Generate random song already in playlist
    - Attempt to add again
    - Verify only one cross-ref exists

13. **Property 13: Removing Song Deletes Cross-Reference Only**
    - Generate random song in playlist
    - Remove from playlist
    - Verify cross-ref deleted but song entity exists

14. **Property 14: Playlist Removal Preserves Favorite Status**
    - Generate random song with random isFavorite in user playlist
    - Remove from playlist
    - Verify isFavorite unchanged

15. **Property 15: Database Errors Handled Gracefully**
    - Generate random database operations
    - Mock random database exceptions
    - Verify system returns error result without crashing

### Integration Testing

Integration tests will verify end-to-end flows:

- Test favorite toggle from PlaylistDetailScreen updates database and UI
- Test favorite toggle from PlayerScreen updates database and UI
- Test renaming playlist updates database and refreshes all screens showing that playlist
- Test adding songs to playlist updates database and refreshes PlaylistDetailScreen
- Test removing songs from playlist updates database and refreshes PlaylistDetailScreen
- Test that favoriting a song makes it appear in Favorite system playlist
- Test that unfavoriting a song removes it from Favorite system playlist

### Test Coverage Goals

- Unit test coverage: 80%+ for repository and ViewModel layers
- Property test coverage: All 15 correctness properties
- Integration test coverage: All major user flows
- UI test coverage: All new composables and screen modifications
