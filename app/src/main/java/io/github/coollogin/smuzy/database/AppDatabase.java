package io.github.coollogin.smuzy.database;

import androidx.media3.common.util.UnstableApi;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import io.github.coollogin.smuzy.App;
import io.github.coollogin.smuzy.database.converter.DateConverters;
import io.github.coollogin.smuzy.database.converter.StringListConverter;
import io.github.coollogin.smuzy.database.dao.ChronologyDao;
import io.github.coollogin.smuzy.database.dao.DownloadDao;
import io.github.coollogin.smuzy.database.dao.FavoriteDao;
import io.github.coollogin.smuzy.database.dao.InternetRadioStationDao;
import io.github.coollogin.smuzy.database.dao.LyricsDao;
import io.github.coollogin.smuzy.database.dao.PinnedPlaylistDao;
import io.github.coollogin.smuzy.database.dao.SyncedPlaylistDao;
import io.github.coollogin.smuzy.database.dao.UserTagDao;
import io.github.coollogin.smuzy.database.dao.PlaylistDao;
import io.github.coollogin.smuzy.database.dao.PlaylistSongDao;
import io.github.coollogin.smuzy.database.dao.QueueDao;
import io.github.coollogin.smuzy.database.dao.RecentSearchDao;
import io.github.coollogin.smuzy.database.dao.ServerDao;
import io.github.coollogin.smuzy.database.dao.SessionMediaItemDao;
import io.github.coollogin.smuzy.model.Chronology;
import io.github.coollogin.smuzy.model.Download;
import io.github.coollogin.smuzy.model.Favorite;
import io.github.coollogin.smuzy.model.InternetRadioStationCache;
import io.github.coollogin.smuzy.model.LyricsCache;
import io.github.coollogin.smuzy.model.PinnedPlaylist;
import io.github.coollogin.smuzy.model.PlaylistSong;
import io.github.coollogin.smuzy.model.Queue;
import io.github.coollogin.smuzy.model.RecentSearch;
import io.github.coollogin.smuzy.model.Server;
import io.github.coollogin.smuzy.model.SessionMediaItem;
import io.github.coollogin.smuzy.model.SyncedPlaylist;
import io.github.coollogin.smuzy.model.UserTagAlbum;
import io.github.coollogin.smuzy.model.UserTagSong;
import io.github.coollogin.smuzy.model.UserTagSync;
import io.github.coollogin.smuzy.subsonic.models.Playlist;

@UnstableApi
@Database(
        version = 25,
        entities = {
            Queue.class,
            Server.class,
            RecentSearch.class,
            Download.class,
            Chronology.class,
            Favorite.class,
            SessionMediaItem.class,
            Playlist.class,
            PinnedPlaylist.class,
            LyricsCache.class,
            InternetRadioStationCache.class,
            PlaylistSong.class,
            SyncedPlaylist.class,
            UserTagSong.class,
            UserTagAlbum.class,
            UserTagSync.class,
        },
        autoMigrations = {
                @AutoMigration(from = 10, to = 11),
                @AutoMigration(from = 11, to = 12),
                @AutoMigration(from = 12, to = 13),
                @AutoMigration(from = 13, to = 14),
                @AutoMigration(from = 14, to = 15),
                @AutoMigration(from = 15, to = 16),
                @AutoMigration(from = 16, to = 17),
                @AutoMigration(from = 17, to = 18),
                @AutoMigration(from = 18, to = 19),
                @AutoMigration(from = 19, to = 20),
                @AutoMigration(from = 20, to = 21),
                @AutoMigration(from = 21, to = 22),
                @AutoMigration(from = 22, to = 23),
                @AutoMigration(from = 23, to = 24),
                @AutoMigration(from = 24, to = 25),
        }
)
@TypeConverters({DateConverters.class, StringListConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private final static String DB_NAME = "tempo_db";
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance() {
        if (instance == null) {
            instance = Room.databaseBuilder(App.getContext(), AppDatabase.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }

        return instance;
    }

    public abstract QueueDao queueDao();

    public abstract ServerDao serverDao();

    public abstract RecentSearchDao recentSearchDao();

    public abstract DownloadDao downloadDao();

    public abstract ChronologyDao chronologyDao();

    public abstract FavoriteDao favoriteDao();

    public abstract SessionMediaItemDao sessionMediaItemDao();

    public abstract PlaylistDao playlistDao();

    public abstract PinnedPlaylistDao pinnedPlaylistDao();

    public abstract SyncedPlaylistDao syncedPlaylistDao();

    public abstract PlaylistSongDao playlistSongDao();

    public abstract LyricsDao lyricsDao();

    public abstract InternetRadioStationDao internetRadioStationDao();

    public abstract UserTagDao userTagDao();
}
