# Implementation Plan: Favorite Songs and Playlist Management

## Overview

This implementation plan breaks down the favorite songs and playlist management feature into discrete coding tasks. The approach follows three phases: (1) database and repository layer for favorites, (2) UI integration for favorites, (3) playlist management features. Each task builds incrementally with testing integrated throughout.

## Tasks

- [ ] 1. Set up database migration and favorite field
  - [ ] 1.1 Add isFavorite field to Song entity
    - Update Song.kt data class with `isFavorite: Boolean = false`
    - _Requirements: 1.1_
  
  - [ ] 1.2 Create database migration from version 4 to 5
    - Create MIGRATION_4_5 object in database class
    - Add SQL to alter songs table with new column
    - Update database version number to 5
    - Add migration to database builder
    - _Requirements: 1.2, 1.3_
  
  - [ ] 1.3 Write property test for migration data preservation
    - **Property 1: Database Migration Preserves Data**
    - **Validates: Requirements 1.3**

- [ ] 2. Implement repository layer for favorites
  - [ ] 2.1 Add DAO methods for favorite operations
    - Add updateFavoriteStatus method to SongDao
    - Add observeFavoriteStatus method to SongDao
    - Add getFavoriteSongs query to SongDao
    - Add getFavoritePlaylistId query to PlaylistDao
    - Add insertPlaylistSongCrossRef method to PlaylistDao
    - Add deletePlaylistSongCrossRef method to PlaylistDao
    - _Requirements: 1.4, 4.1, 4.2_
  
  - [ ] 2.2 Implement SongRepository favorite methods
    - Implement toggleFavorite method with playlist synchronization
    - Implement setFavorite method
    - Implement observeFavoriteStatus method
    - Handle Favorite playlist cross-reference creation/deletion
    - _Requirements: 1.4, 2.5, 2.6, 4.1, 4.2_
  
  - [ ] 2.3 Write property tests for favorite toggle
    - **Property 3: Toggle Favorite Flips Boolean**
    - **Property 7: Favorite Toggle Round Trip**
    - **Validates: Requirements 2.4, 2.5, 2.6**
  
  - [ ] 2.4 Write property tests for playlist synchronization
    - **Property 4: Favorite Songs Have Playlist Cross-Reference**
    - **Property 5: Unfavorited Songs Lack Playlist Cross-Reference**
    - **Property 6: Favorite Playlist Contains Only Favorite Songs**
    - **Validates: Requirements 4.1, 4.2, 4.3**

- [ ] 3. Create reusable favorite icon component
  - [ ] 3.1 Implement FavoriteIconButton composable
    - Create composable with isFavorite parameter and onToggle callback
    - Use Material Icons Filled.Favorite and Outlined.FavoriteBorder
    - Apply hot pink color (#FF69B4) when favorited
    - Add accessibility content descriptions
    - _Requirements: 2.1, 2.2, 2.3, 8.3, 8.4_
  
  - [ ] 3.2 Write unit tests for FavoriteIconButton
    - Test icon rendering for true/false states
    - Test color application
    - Test click handling
    - _Requirements: 2.2, 2.3, 8.3_

- [ ] 4. Integrate favorites into PlayerScreen
  - [ ] 4.1 Update PlayerViewModel with favorite state
    - Add isFavorite StateFlow
    - Implement toggleFavorite method
    - Observe current song's favorite status
    - _Requirements: 3.4, 3.5, 3.6_
  
  - [ ] 4.2 Add FavoriteIconButton to PlayerScreen UI
    - Position heart icon near song information
    - Connect to PlayerViewModel favorite state
    - Wire up toggle callback
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [ ] 4.3 Write integration tests for PlayerScreen favorites
    - Test favorite toggle updates database
    - Test icon state reflects database state
    - Test playlist synchronization
    - _Requirements: 3.4, 3.5, 3.6_

- [ ] 5. Integrate favorites into PlaylistDetailScreen
  - [ ] 5.1 Update PlaylistDetailViewModel with favorite toggle
    - Implement toggleSongFavorite method
    - _Requirements: 2.4, 2.5, 2.6_
  
  - [ ] 5.2 Add FavoriteIconButton to song items in PlaylistDetailScreen
    - Add heart icon to each song list item
    - Connect to song's favorite status
    - Wire up toggle callback to ViewModel
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  
  - [ ] 5.3 Write integration tests for PlaylistDetailScreen favorites
    - Test favorite toggle updates database
    - Test icon state reflects database state
    - Test playlist synchronization
    - _Requirements: 2.4, 2.5, 2.6_

- [ ] 6. Checkpoint - Ensure favorite functionality works end-to-end
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Implement playlist rename functionality
  - [ ] 7.1 Add PlaylistRepository rename methods
    - Implement renamePlaylist method with validation
    - Implement isPlaylistNameUnique method
    - Add updatePlaylistName method to PlaylistDao
    - Add countPlaylistsWithName query to PlaylistDao
    - _Requirements: 5.4, 5.5, 5.6_
  
  - [ ] 7.2 Write property tests for playlist rename validation
    - **Property 8: Empty Playlist Names Rejected**
    - **Property 9: Duplicate Playlist Names Rejected**
    - **Property 10: Valid Unique Names Accepted**
    - **Validates: Requirements 5.4, 5.5, 5.6**
  
  - [ ] 7.3 Create RenamePlaylistDialog composable
    - Create dialog with text input field
    - Display validation error messages
    - Handle confirm and dismiss actions
    - _Requirements: 5.3, 5.4, 5.5_
  
  - [ ] 7.4 Update PlaylistDetailViewModel with rename state
    - Add showRenameDialog StateFlow
    - Add renameError StateFlow
    - Implement showRenameDialog, hideRenameDialog methods
    - Implement renamePlaylist method with validation
    - _Requirements: 5.4, 5.5, 5.6, 5.7_
  
  - [ ] 7.5 Add rename option to PlaylistDetailScreen
    - Add rename menu item to top app bar (only for user playlists)
    - Display RenamePlaylistDialog when triggered
    - Connect dialog to ViewModel state and methods
    - _Requirements: 5.1, 5.2, 5.3_
  
  - [ ] 7.6 Write unit tests for rename validation
    - Test empty name rejection
    - Test whitespace-only name rejection
    - Test duplicate name rejection
    - Test system playlist rejection
    - Test valid name acceptance
    - _Requirements: 5.4, 5.5, 5.6_

- [ ] 8. Implement add songs to playlist functionality
  - [ ] 8.1 Add PlaylistRepository methods for adding songs
    - Implement addSongsToPlaylist method
    - Handle duplicate prevention with INSERT OR IGNORE
    - _Requirements: 6.5, 6.7_
  
  - [ ] 8.2 Write property tests for adding songs
    - **Property 11: Adding Songs Creates Cross-References**
    - **Property 12: Duplicate Song Addition Prevented**
    - **Validates: Requirements 6.5, 6.7**
  
  - [ ] 8.3 Create AddSongsDialog composable
    - Create dialog with scrollable song list
    - Implement multi-select with checkboxes
    - Disable songs already in playlist
    - Show selection count in confirm button
    - _Requirements: 6.3, 6.4, 6.7_
  
  - [ ] 8.4 Update PlaylistDetailViewModel with add songs state
    - Add showAddSongsDialog StateFlow
    - Add availableSongs StateFlow
    - Implement showAddSongsDialog, hideAddSongsDialog methods
    - Implement addSongsToPlaylist method
    - Load all songs when dialog opens
    - _Requirements: 6.5, 6.6_
  
  - [ ] 8.5 Add "Add Songs" button to PlaylistDetailScreen
    - Add floating action button (only for user playlists)
    - Display AddSongsDialog when triggered
    - Connect dialog to ViewModel state and methods
    - Pass current playlist song IDs to dialog
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  
  - [ ] 8.6 Write integration tests for adding songs
    - Test songs added to database
    - Test duplicate prevention
    - Test UI updates after adding
    - _Requirements: 6.5, 6.6, 6.7_

- [ ] 9. Implement remove songs from playlist functionality
  - [ ] 9.1 Add PlaylistRepository method for removing songs
    - Implement removeSongFromPlaylist method
    - Verify song entity is preserved
    - _Requirements: 7.3, 7.5_
  
  - [ ] 9.2 Write property tests for removing songs
    - **Property 13: Removing Song Deletes Cross-Reference Only**
    - **Property 14: Playlist Removal Preserves Favorite Status**
    - **Validates: Requirements 7.3, 7.5, 7.6**
  
  - [ ] 9.3 Update PlaylistDetailViewModel with remove method
    - Implement removeSongFromPlaylist method
    - Check playlist is not system playlist
    - _Requirements: 7.2, 7.3_
  
  - [ ] 9.4 Add swipe-to-delete to PlaylistDetailScreen song items
    - Implement SwipeToDismiss for song items (only for user playlists)
    - Show delete icon on swipe
    - Call ViewModel remove method on dismiss
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [ ] 9.5 Write integration tests for removing songs
    - Test song removed from playlist
    - Test song entity preserved
    - Test favorite status preserved
    - Test system playlist protection
    - _Requirements: 7.3, 7.4, 7.5, 7.6_

- [ ] 10. Add error handling and edge cases
  - [ ] 10.1 Implement error handling in repositories
    - Wrap database operations in try-catch
    - Return Result types for operations that can fail
    - Log errors for debugging
    - _Requirements: 9.4_
  
  - [ ] 10.2 Write property test for error handling
    - **Property 15: Database Errors Handled Gracefully**
    - **Validates: Requirements 9.4**
  
  - [ ] 10.3 Add error display in ViewModels and UI
    - Propagate errors from repositories to ViewModels
    - Display Snackbar for database errors
    - Display inline errors for validation failures
    - _Requirements: 9.4_
  
  - [ ] 10.4 Write unit tests for error scenarios
    - Test database failure handling
    - Test validation error display
    - Test system playlist protection
    - _Requirements: 9.4_

- [ ] 11. Final checkpoint - Ensure all features work end-to-end
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks including tests are required for comprehensive implementation
- The design uses Kotlin, so all implementation will be in Kotlin
- Each property test should run minimum 100 iterations
- Use Kotest Property Testing or junit-quickcheck for property-based tests
- All database operations should use coroutines on Dispatchers.IO
- Follow existing code style and architecture patterns
- Material 3 components should be used throughout
- Hot pink (#FF69B4) should be used for favorite icons when active
