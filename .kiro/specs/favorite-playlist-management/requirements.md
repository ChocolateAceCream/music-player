# Requirements Document

## Introduction

This document specifies the requirements for implementing favorite songs and playlist management features in an Android music player application. The feature enables users to mark songs as favorites, rename custom playlists, and manage playlist contents through an intuitive interface.

## Glossary

- **Song**: A music track entity with metadata including id, name, author, album, cover art, file link, and timestamps
- **Playlist**: A collection of songs that can be either system-managed or user-created
- **System_Playlist**: A predefined playlist (All Songs, Recent Played, Favorite, Recent Download) marked with isSystem flag that cannot be deleted or manually modified
- **User_Playlist**: A custom playlist created by the user that can be renamed, deleted, and manually managed
- **Favorite_Status**: A boolean property indicating whether a song is marked as favorite by the user
- **PlaylistDetailScreen**: The UI screen displaying songs within a specific playlist
- **PlayerScreen**: The full-screen player UI showing currently playing song with controls and album art
- **Song_Repository**: The data access layer for song-related database operations
- **Playlist_Repository**: The data access layer for playlist-related database operations
- **Room_Database**: The local SQLite database abstraction layer using Android Room library
- **Database_Migration**: The process of updating database schema from one version to another while preserving existing data

## Requirements

### Requirement 1: Favorite Song Data Model

**User Story:** As a user, I want the system to track which songs I've marked as favorites, so that I can easily access my preferred music.

#### Acceptance Criteria

1. THE Song_Entity SHALL include an isFavorite boolean field with default value false
2. THE Room_Database SHALL migrate from version 4 to version 5 to accommodate the schema change
3. WHEN the database migration executes, THE System SHALL preserve all existing song data
4. THE Song_Repository SHALL provide methods to update the favorite status of songs

### Requirement 2: Favorite Toggle in Playlist Detail

**User Story:** As a user, I want to mark songs as favorites directly from the playlist view, so that I can quickly curate my favorite music while browsing.

#### Acceptance Criteria

1. WHEN viewing songs in PlaylistDetailScreen, THE System SHALL display a heart icon for each song item
2. WHEN a song's favorite status is true, THE System SHALL display a filled heart icon
3. WHEN a song's favorite status is false, THE System SHALL display an outline heart icon
4. WHEN a user taps the heart icon, THE System SHALL toggle the song's isFavorite field
5. WHEN a song is marked as favorite, THE System SHALL add the song to the Favorite system playlist
6. WHEN a song is unmarked as favorite, THE System SHALL remove the song from the Favorite system playlist
7. WHEN the favorite status changes, THE System SHALL update the UI immediately to reflect the new state

### Requirement 3: Favorite Toggle in Player Screen

**User Story:** As a user, I want to mark the currently playing song as a favorite from the player screen, so that I can save songs I enjoy while listening.

#### Acceptance Criteria

1. WHEN viewing PlayerScreen, THE System SHALL display a heart icon near the song information
2. WHEN the current song's favorite status is true, THE System SHALL display a filled heart icon
3. WHEN the current song's favorite status is false, THE System SHALL display an outline heart icon
4. WHEN a user taps the heart icon, THE System SHALL toggle the current song's isFavorite field
5. WHEN the current song is marked as favorite, THE System SHALL add the song to the Favorite system playlist
6. WHEN the current song is unmarked as favorite, THE System SHALL remove the song from the Favorite system playlist
7. WHEN the favorite status changes, THE System SHALL update the UI immediately to reflect the new state

### Requirement 4: Favorite Playlist Synchronization

**User Story:** As a user, I want my favorite songs to automatically appear in the Favorite playlist, so that I have a dedicated collection of my preferred music.

#### Acceptance Criteria

1. WHEN a song's isFavorite field changes to true, THE System SHALL create a PlaylistSongCrossRef entry linking the song to the Favorite system playlist
2. WHEN a song's isFavorite field changes to false, THE System SHALL delete the PlaylistSongCrossRef entry linking the song to the Favorite system playlist
3. WHEN querying the Favorite system playlist, THE System SHALL return only songs where isFavorite is true
4. THE System SHALL maintain referential integrity between Song entities and the Favorite playlist

### Requirement 5: Rename User Playlist

**User Story:** As a user, I want to rename my custom playlists, so that I can organize my music with meaningful names.

#### Acceptance Criteria

1. WHEN viewing a user playlist in PlaylistDetailScreen, THE System SHALL provide a rename option
2. WHEN viewing a system playlist in PlaylistDetailScreen, THE System SHALL NOT provide a rename option
3. WHEN a user initiates rename, THE System SHALL display a dialog with a text input field pre-filled with the current playlist name
4. WHEN a user submits an empty playlist name, THE System SHALL reject the rename and display an error message
5. WHEN a user submits a playlist name that already exists, THE System SHALL reject the rename and display an error message
6. WHEN a user submits a valid unique playlist name, THE System SHALL update the playlist name in the database
7. WHEN the playlist name is updated, THE System SHALL refresh the UI to display the new name

### Requirement 6: Add Songs to User Playlist

**User Story:** As a user, I want to add songs to my custom playlists, so that I can curate personalized music collections.

#### Acceptance Criteria

1. WHEN viewing a user playlist in PlaylistDetailScreen, THE System SHALL display an "Add Songs" button
2. WHEN viewing a system playlist in PlaylistDetailScreen, THE System SHALL NOT display an "Add Songs" button
3. WHEN a user taps the "Add Songs" button, THE System SHALL display a song picker dialog showing all available songs
4. WHEN displaying the song picker, THE System SHALL allow selection of multiple songs
5. WHEN a user confirms song selection, THE System SHALL create PlaylistSongCrossRef entries for each selected song
6. WHEN songs are added to the playlist, THE System SHALL update the PlaylistDetailScreen to display the newly added songs
7. WHEN a user attempts to add a song already in the playlist, THE System SHALL prevent duplicate entries

### Requirement 7: Remove Songs from User Playlist

**User Story:** As a user, I want to remove songs from my custom playlists, so that I can maintain relevant music collections.

#### Acceptance Criteria

1. WHEN viewing songs in a user playlist, THE System SHALL provide a mechanism to remove individual songs
2. WHEN viewing songs in a system playlist, THE System SHALL NOT provide a mechanism to manually remove songs
3. WHEN a user initiates song removal, THE System SHALL delete the corresponding PlaylistSongCrossRef entry
4. WHEN a song is removed from the playlist, THE System SHALL update the UI to remove the song from the displayed list
5. WHEN a song is removed from a user playlist, THE Song_Entity SHALL remain in the database
6. WHEN a song is removed from a user playlist, THE System SHALL NOT affect the song's favorite status

### Requirement 8: UI Consistency and Material Design

**User Story:** As a user, I want the new features to match the existing app design, so that I have a consistent and familiar experience.

#### Acceptance Criteria

1. THE System SHALL implement all UI components using Jetpack Compose
2. THE System SHALL follow Material 3 design guidelines
3. THE System SHALL use hot pink (#FF69B4) as the primary color for interactive elements
4. WHEN displaying heart icons, THE System SHALL use Material Icons (filled and outline variants)
5. THE System SHALL maintain consistency with existing screen layouts and navigation patterns

### Requirement 9: Data Persistence and Repository Pattern

**User Story:** As a developer, I want data operations to follow the existing architecture, so that the codebase remains maintainable and consistent.

#### Acceptance Criteria

1. THE System SHALL implement all database operations through Song_Repository and Playlist_Repository
2. THE System SHALL use Room database annotations for entity definitions and queries
3. THE System SHALL implement database migration from version 4 to version 5 with proper migration strategy
4. WHEN database operations fail, THE System SHALL handle exceptions gracefully
5. THE System SHALL maintain the existing ViewModel architecture (PlayerViewModel, PlaylistDetailViewModel, LibraryViewModel)

### Requirement 10: Performance and Responsiveness

**User Story:** As a user, I want the app to respond quickly to my actions, so that I have a smooth and enjoyable experience.

#### Acceptance Criteria

1. WHEN a user toggles favorite status, THE System SHALL update the UI within 100 milliseconds
2. WHEN a user adds songs to a playlist, THE System SHALL complete the operation and update the UI within 500 milliseconds
3. WHEN a user removes a song from a playlist, THE System SHALL update the UI within 100 milliseconds
4. THE System SHALL perform all database operations on background threads to prevent UI blocking
5. THE System SHALL use coroutines for asynchronous operations following Kotlin best practices
