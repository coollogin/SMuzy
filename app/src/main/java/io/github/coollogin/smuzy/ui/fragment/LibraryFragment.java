package io.github.coollogin.smuzy.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;

import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import io.github.coollogin.smuzy.R;
import io.github.coollogin.smuzy.databinding.FragmentLibraryBinding;
import io.github.coollogin.smuzy.helper.recyclerview.CustomLinearSnapHelper;
import io.github.coollogin.smuzy.interfaces.ClickCallback;
import io.github.coollogin.smuzy.model.UserTagCloudUiState;
import io.github.coollogin.smuzy.navigation.NavigationController;
import io.github.coollogin.smuzy.repository.PlaylistRepository;
import io.github.coollogin.smuzy.subsonic.models.Child;
import io.github.coollogin.smuzy.ui.activity.MainActivity;
import io.github.coollogin.smuzy.ui.adapter.AlbumAdapter;
import io.github.coollogin.smuzy.ui.adapter.ArtistAdapter;
import io.github.coollogin.smuzy.ui.adapter.GenreAdapter;
import io.github.coollogin.smuzy.ui.adapter.MusicFolderAdapter;
import io.github.coollogin.smuzy.ui.adapter.PlaylistHorizontalAdapter;
import io.github.coollogin.smuzy.util.Constants;
import io.github.coollogin.smuzy.util.Preferences;
import io.github.coollogin.smuzy.viewmodel.LibraryViewModel;
import io.github.coollogin.smuzy.viewmodel.MainViewModel;
import io.github.coollogin.smuzy.viewmodel.UserTagViewModel;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@UnstableApi
public class LibraryFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LibraryFragment";

    private FragmentLibraryBinding bind;
    private MainActivity activity;
    private NavigationController navigationController;
    private LibraryViewModel libraryViewModel;
    private MainViewModel mainViewModel;
    private UserTagViewModel userTagViewModel;

    private MusicFolderAdapter musicFolderAdapter;
    private AlbumAdapter albumAdapter;
    private ArtistAdapter artistAdapter;
    private GenreAdapter genreAdapter;
    private PlaylistHorizontalAdapter playlistHorizontalAdapter;

    private MaterialToolbar materialToolbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        navigationController = activity.getNavigationController();

        bind = FragmentLibraryBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        userTagViewModel = new ViewModelProvider(this).get(UserTagViewModel.class);

        libraryViewModel.clearCacheIfMusicFolderChanged();

        init();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initMusicFolderView();
        initAlbumView();
        initArtistView();
        initGenreView();
        initUserTagView();
        initPlaylistView();
        initSwipeToRefresh();
        initMusicFolderChangeListener();
    }

    private void initMusicFolderChangeListener() {
        mainViewModel.getActiveMusicFolderId().observe(getViewLifecycleOwner(), musicFolderId -> {
            libraryViewModel.reloadIfMusicFolderChanged(getViewLifecycleOwner());
            // Fires immediately with the current folder and again on every change; the tag cache
            // is scoped to a music folder, so load() rescans when it no longer matches.
            userTagViewModel.load(false);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.toggleBottomNavigationBarVisibilityOnOrientationChange();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPlaylistView();
        userTagViewModel.load(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        bind.albumCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_albumCatalogueFragment));
        bind.artistCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_artistCatalogueFragment));
        bind.genreCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_genreCatalogueFragment));
        bind.playlistCatalogueTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.PLAYLIST_ALL, Constants.PLAYLIST_ALL);
            activity.navController.navigate(R.id.action_libraryFragment_to_playlistCatalogueFragment, bundle);
        });

        // Album
        bind.albumCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshAlbumSample(getViewLifecycleOwner());
            return true;
        });
        bind.albumCatalogueSampleTextViewRefreshable.setOnClickListener( v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );

        // Artist
        bind.artistCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshArtistSample(getViewLifecycleOwner());
            return true;
        });
        bind.artistCatalogueSampleTextViewRefreshable.setOnClickListener( v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );

        // Genre
        bind.genreCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshGenreSample(getViewLifecycleOwner());
            return true;
        });
        bind.genreCatalogueSampleTextViewRefreshable.setOnClickListener(v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );

        // Playlist
        bind.playlistCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
            return true;
        });
        bind.playlistCatalogueSampleTextViewRefreshable.setOnClickListener( v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );
    }

    private void initAppBar() {
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);
        navigationController.setHamburgerMenuForLandscape(activity, materialToolbar);

        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void initMusicFolderView() {
        if (!Preferences.isMusicDirectorySectionVisible()) {
            bind.libraryMusicFolderSector.setVisibility(View.GONE);
            return;
        }

        bind.musicFolderRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.musicFolderRecyclerView.setHasFixedSize(true);

        musicFolderAdapter = new MusicFolderAdapter(this);
        bind.musicFolderRecyclerView.setAdapter(musicFolderAdapter);
        libraryViewModel.getMusicFolders(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), musicFolders -> {
            if (musicFolders == null) {
                if (bind != null) bind.libraryMusicFolderSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryMusicFolderSector.setVisibility(!musicFolders.isEmpty() ? View.VISIBLE : View.GONE);

                musicFolderAdapter.setItems(musicFolders);
            }
        });
    }

    private void initAlbumView() {
        bind.albumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.albumRecyclerView.setHasFixedSize(true);

        albumAdapter = new AlbumAdapter(this);
        bind.albumRecyclerView.setAdapter(albumAdapter);
        libraryViewModel.getAlbumSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.libraryAlbumSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryAlbumSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                albumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper albumSnapHelper = new CustomLinearSnapHelper();
        albumSnapHelper.attachToRecyclerView(bind.albumRecyclerView);
    }

    private void initArtistView() {
        bind.artistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.artistRecyclerView.setHasFixedSize(true);

        artistAdapter = new ArtistAdapter(this, false, false);
        bind.artistRecyclerView.setAdapter(artistAdapter);
        libraryViewModel.getArtistSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.libraryArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                artistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper artistSnapHelper = new CustomLinearSnapHelper();
        artistSnapHelper.attachToRecyclerView(bind.artistRecyclerView);
    }

    private void initGenreView() {
        bind.genreRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false));
        bind.genreRecyclerView.setHasFixedSize(true);

        genreAdapter = new GenreAdapter(this);
        bind.genreRecyclerView.setAdapter(genreAdapter);

        libraryViewModel.getGenreSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), genres -> {
            if (genres == null) {
                if (bind != null) bind.libraryGenresSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryGenresSector.setVisibility(!genres.isEmpty() ? View.VISIBLE : View.GONE);

                genreAdapter.setItems(genres);
            }
        });

        CustomLinearSnapHelper genreSnapHelper = new CustomLinearSnapHelper();
        genreSnapHelper.attachToRecyclerView(bind.genreRecyclerView);
    }

    private void initUserTagView() {
        bind.userTagCloudView.setOnTagClickListener(this::onUserTagClick);
        bind.userTagRetryButton.setOnClickListener(v -> userTagViewModel.load(true));

        bind.userTagCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            userTagViewModel.load(true);
            return true;
        });
        bind.userTagCatalogueSampleTextViewRefreshable.setOnClickListener(v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );

        userTagViewModel.getState().observe(getViewLifecycleOwner(), this::renderUserTagState);
    }

    private void renderUserTagState(UserTagCloudUiState state) {
        if (bind == null || state == null) return;

        if (state.getStatus() == UserTagCloudUiState.Status.UNSUPPORTED) {
            bind.libraryUserTagSector.setVisibility(View.GONE);
            return;
        }
        bind.libraryUserTagSector.setVisibility(View.VISIBLE);

        boolean hasTags = !state.getTags().isEmpty();
        bind.userTagCloudView.setTags(state.getTags());
        bind.userTagCloudView.setVisibility(hasTags ? View.VISIBLE : View.GONE);
        bind.userTagProgress.setVisibility(
                state.getStatus() == UserTagCloudUiState.Status.LOADING && !hasTags ? View.VISIBLE : View.GONE
        );

        int statusTextRes = 0;
        boolean retryVisible = false;
        switch (state.getStatus()) {
            case LOADING:
                if (!hasTags) statusTextRes = R.string.user_tag_loading;
                break;
            case EMPTY:
                statusTextRes = R.string.user_tag_empty;
                break;
            case ERROR:
                statusTextRes = R.string.user_tag_error;
                retryVisible = true;
                break;
            default:
                break;
        }

        if (statusTextRes != 0) {
            bind.userTagStatusText.setText(statusTextRes);
            bind.userTagStatusText.setVisibility(View.VISIBLE);
        } else {
            bind.userTagStatusText.setVisibility(View.GONE);
        }
        bind.userTagRetryButton.setVisibility(retryVisible ? View.VISIBLE : View.GONE);
    }

    private void onUserTagClick(String tag) {
        userTagViewModel.albumsForTag(tag, albums -> {
            if (bind == null || !isAdded()) return;

            if (albums.isEmpty()) {
                Toast.makeText(requireContext(), R.string.user_tag_no_albums, Toast.LENGTH_SHORT).show();
                return;
            }

            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.ALBUMS_OBJECT, new ArrayList<>(albums));
            bundle.putString(Constants.ALBUM_LIST_TITLE, tag);
            activity.navController.navigate(R.id.action_libraryFragment_to_albumListPageFragment, bundle);
        });
    }

    private void initPlaylistView() {
        bind.playlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistRecyclerView.setHasFixedSize(true);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.playlistRecyclerView.setAdapter(playlistHorizontalAdapter);
        libraryViewModel.getPlaylistSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null) {
                if (bind != null) bind.libraryPlaylistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryPlaylistSector.setVisibility(!playlists.isEmpty() ? View.VISIBLE : View.GONE);

                playlistHorizontalAdapter.setItems(playlists);
            }
        });
    }

    private void refreshPlaylistView() {
        final Handler handler = new Handler();

        final Runnable runnable = () -> {
            if (getView() != null && bind != null && libraryViewModel != null)
                libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
        };

        handler.postDelayed(runnable, 100);
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }

    @Override
    public void onGenreClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistRowBottomSheetDialog, bundle);
    }

    @Override
    public void onMusicFolderClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.indexFragment, bundle);
    }

    public void initSwipeToRefresh() {
        bind.swipeLibraryToRefresh.setOnRefreshListener(() -> {
            pullToRefresh();
            bind.swipeLibraryToRefresh.setRefreshing(false);
        });
    }

    private void pullToRefresh() {
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        libraryViewModel.refreshAlbumSample(lifecycleOwner);
        libraryViewModel.refreshGenreSample(lifecycleOwner);
        libraryViewModel.refreshArtistSample(lifecycleOwner);
        libraryViewModel.refreshPlaylistSample(lifecycleOwner);
        userTagViewModel.load(true);

    }
}
