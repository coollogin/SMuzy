package io.github.coollogin.smuzy.ui.fragment

import android.content.ComponentName
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.Fade
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import io.github.coollogin.smuzy.App
import io.github.coollogin.smuzy.databinding.InnerFragmentPlayerCoverBinding
import io.github.coollogin.smuzy.model.Download
import io.github.coollogin.smuzy.service.MediaManager
import io.github.coollogin.smuzy.lan.LanRemoteSession
import io.github.coollogin.smuzy.service.MediaService
import io.github.coollogin.smuzy.subsonic.models.Child
import io.github.coollogin.smuzy.ui.components.NowPlayingArtworkPager
import io.github.coollogin.smuzy.ui.components.SpectrumVisualizer
import io.github.coollogin.smuzy.ui.dialog.PlaylistChooserDialog
import io.github.coollogin.smuzy.util.Constants
import io.github.coollogin.smuzy.util.DownloadUtil
import io.github.coollogin.smuzy.util.ExternalAudioWriter
import io.github.coollogin.smuzy.util.MappingUtil
import io.github.coollogin.smuzy.util.Preferences
import io.github.coollogin.smuzy.viewmodel.PlayerBottomSheetViewModel
import io.github.coollogin.smuzy.visualizer.VisualizerMode
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.ArrayList

@OptIn(markerClass = [UnstableApi::class])
class PlayerCoverFragment : Fragment() {

    private var bind: InnerFragmentPlayerCoverBinding? = null
    private lateinit var playerBottomSheetViewModel: PlayerBottomSheetViewModel
    private var mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>? = null
    private var mediaBrowser: MediaBrowser? = null

    private val handler = Handler(Looper.getMainLooper())

    private var queueItems by mutableStateOf<List<Child>>(emptyList())
    private var localQueue: List<Child> = emptyList()
    private var currentPlayingIndex by mutableIntStateOf(0)
    private var isRadioStation by mutableStateOf(false)
    private var radioStationArtworkUri by mutableStateOf<Uri?>(null)
    private var radioStationCoverArtId by mutableStateOf<String?>(null)
    private var isPlaying by mutableStateOf(false)
    private var coverAnimationEnabled by mutableStateOf(false)
    private var visualizerEnabled by mutableStateOf(false)
    private var visualizerMode by mutableStateOf(VisualizerMode.WAVE)
    private var retractRequest by mutableIntStateOf(0)
    private var expandRequest by mutableIntStateOf(0)
    private var pendingSkipAction: Runnable? = null
    private var skipFallbackMediaId: String? = null

    private val expandFallbackRunnable = Runnable {
        if (isPlaying && mediaBrowser?.currentMediaItem?.mediaId == skipFallbackMediaId) {
            expandRequest++
        }
    }

    private val uiPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "cover_animation") {
                coverAnimationEnabled = Preferences.isCoverAnimationEnabled()
            }
            if (key == null || key == Preferences.VISUALIZER) {
                visualizerEnabled = Preferences.isVisualizerEnabled()
            }
            if (key == null || key == Preferences.VISUALIZER_TYPE) {
                visualizerMode = Preferences.getVisualizerMode()
            }
        }

    private fun refreshUiPreferences() {
        coverAnimationEnabled = Preferences.isCoverAnimationEnabled()
        visualizerEnabled = Preferences.isVisualizerEnabled()
        visualizerMode = Preferences.getVisualizerMode()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = InnerFragmentPlayerCoverBinding.inflate(inflater, container, false)
        bind = binding

        playerBottomSheetViewModel = ViewModelProvider(requireActivity())[PlayerBottomSheetViewModel::class.java]
        refreshUiPreferences()

        binding.nowPlayingArtworkPager.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NowPlayingArtworkPager(
                    queue = queueItems,
                    currentIndex = currentPlayingIndex,
                    isRadio = isRadioStation,
                    radioArtworkUri = radioStationArtworkUri,
                    radioCoverArtId = radioStationCoverArtId,
                    coverAnimation = coverAnimationEnabled,
                    isPlaying = isPlaying,
                    showVisualizer = visualizerEnabled,
                    visualizerMode = visualizerMode,
                    retractRequest = retractRequest,
                    onRetracted = {
                        retractRequest = 0
                        val action = pendingSkipAction
                        pendingSkipAction = null
                        skipFallbackMediaId = mediaBrowser?.currentMediaItem?.mediaId
                        action?.run()
                        // If the queue repeats the same track, no label change will follow and the
                        // record would stay hidden; ask it to come back out after a short check.
                        handler.removeCallbacks(expandFallbackRunnable)
                        handler.postDelayed(expandFallbackRunnable, 800)
                    },
                    expandRequest = expandRequest,
                    onPageSelected = pageSelected@ { page ->
                        if (page in queueItems.indices) {
                            if (LanRemoteSession.isActive()) {
                                if (LanRemoteSession.queueIsCurrent()) LanRemoteSession.routeEdit("select", page, page)
                                return@pageSelected
                            }
                            currentPlayingIndex = page
                            val browser = mediaBrowser
                            if (browser != null && browser.mediaItemCount > 0 && page in 0 until browser.mediaItemCount) {
                                browser.seekToDefaultPosition(page)
                                browser.play()
                            } else {
                                mediaBrowserListenableFuture?.let { future ->
                                    MediaManager.startQueue(future, queueItems, page)
                                }
                            }
                        }
                    },
                    onCoverClick = {
                        toggleOverlayVisibility(true)
                    }
                )
            }
        }

        initOverlay()
        initInnerButton()
        observeQueue()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        refreshUiPreferences()
        App.getInstance().preferences.registerOnSharedPreferenceChangeListener(uiPreferenceListener)
        initializeBrowser()
        bindMediaController()
        toggleOverlayVisibility(false)
    }

    override fun onStop() {
        App.getInstance().preferences.unregisterOnSharedPreferenceChangeListener(uiPreferenceListener)
        releaseBrowser()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        pendingSkipAction = null
        retractRequest = 0
        bind = null
    }

    fun canAnimateNext(): Boolean =
        coverAnimationEnabled && isPlaying && !isRadioStation && bind != null

    /** Pulls the record back into the sleeve first, then runs [action] (the actual skip). */
    fun skipWithCoverAnimation(action: Runnable) {
        if (!canAnimateNext()) {
            action.run()
            return
        }
        pendingSkipAction = action
        retractRequest++
    }

    private fun observeQueue() {
        playerBottomSheetViewModel.getQueueSong().observe(viewLifecycleOwner) { queue ->
            localQueue = queue?.map { it as Child } ?: emptyList()
            if (!LanRemoteSession.isActive()) queueItems = localQueue
        }
        LanRemoteSession.queueState().observe(viewLifecycleOwner) { if (LanRemoteSession.isActive()) refreshRemoteArtwork() }
        LanRemoteSession.state().observe(viewLifecycleOwner) { state ->
            if (state.active) refreshRemoteArtwork()
            else {
                queueItems = localQueue
                mediaBrowser?.let { updatePlaybackState(it.mediaMetadata, it.currentMediaItemIndex) }
            }
        }
        playerBottomSheetViewModel.getLiveMedia().observe(viewLifecycleOwner) { song ->
            if (!LanRemoteSession.isActive() && song != null && queueItems.isNotEmpty()) {
                val idx = queueItems.indexOfFirst { it.id == song.id }
                if (idx >= 0 && idx != currentPlayingIndex) {
                    currentPlayingIndex = idx
                }
            }
        }
    }

    private fun initTapButtonHideTransition() {
        bind?.let { binding ->
            binding.nowPlayingTapButton.visibility = View.VISIBLE
            handler.removeCallbacksAndMessages(null)
            val runnable = Runnable {
                bind?.nowPlayingTapButton?.visibility = View.GONE
            }
            handler.postDelayed(runnable, 10000)
        }
    }

    private fun refreshRemoteArtwork() {
        val state = LanRemoteSession.current()
        isRadioStation = false
        queueItems = if (LanRemoteSession.queueIsCurrent()) LanRemoteSession.queueItems()
            else if (state.count > 0) listOf(LanRemoteSession.currentSong()) else emptyList()
        currentPlayingIndex = if (LanRemoteSession.queueIsCurrent()) state.index.coerceAtLeast(0) else 0
    }

    private fun initOverlay() {
        bind?.let { binding ->
            binding.nowPlayingSongCoverButtonGroup.setOnClickListener { toggleOverlayVisibility(false) }
            binding.nowPlayingTapButton.setOnClickListener { toggleOverlayVisibility(true) }
        }
    }

    private fun toggleOverlayVisibility(isVisible: Boolean) {
        val binding = bind ?: return
        val transition = Fade().apply {
            duration = 200
            addTarget(binding.nowPlayingSongCoverButtonGroup)
        }

        TransitionManager.beginDelayedTransition(binding.root, transition)
        binding.nowPlayingSongCoverButtonGroup.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.nowPlayingTapButton.visibility = if (isVisible) View.GONE else View.VISIBLE

        if (!isVisible) {
            initTapButtonHideTransition()
        }
    }

    private fun initInnerButton() {
        playerBottomSheetViewModel.getLiveMedia().observe(viewLifecycleOwner) { song ->
            val binding = bind ?: return@observe
            if (song != null) {
                binding.innerButtonTopLeft.setOnClickListener {
                    if (Preferences.getDownloadDirectoryUri() == null) {
                        DownloadUtil.getDownloadTracker(requireContext()).download(
                            MappingUtil.mapDownload(song),
                            Download(song)
                        )
                    } else {
                        ExternalAudioWriter.downloadToUserDirectory(requireContext(), song)
                    }
                }

                binding.innerButtonTopRight.setOnClickListener {
                    val tracks = ArrayList<Child>().apply { add(song) }
                    val bundle = Bundle().apply {
                        putParcelableArrayList(Constants.TRACKS_OBJECT, tracks)
                    }
                    val dialog = PlaylistChooserDialog().apply {
                        arguments = bundle
                    }
                    dialog.show(requireActivity().supportFragmentManager, null)
                }

                binding.innerButtonBottomLeft.setOnClickListener {
                    playerBottomSheetViewModel.getMediaInstantMix(viewLifecycleOwner, song).observe(viewLifecycleOwner) { media ->
                        val future = mediaBrowserListenableFuture
                        if (future != null && media != null) {
                            MediaManager.enqueue(future, media, true)
                        }
                    }
                }

                binding.innerButtonBottomRight.setOnClickListener {
                    val playerBottomSheetFragment = requireActivity().supportFragmentManager
                        .findFragmentByTag("PlayerBottomSheet") as? PlayerBottomSheetFragment
                    playerBottomSheetFragment?.goToLyricsPage()
                }
            }
        }
    }

    private fun initializeBrowser() {
        mediaBrowserListenableFuture = MediaBrowser.Builder(
            requireContext(),
            SessionToken(requireContext(), ComponentName(requireContext(), MediaService::class.java))
        ).buildAsync()
    }

    private fun releaseBrowser() {
        mediaBrowserListenableFuture?.let { MediaBrowser.releaseFuture(it) }
        mediaBrowserListenableFuture = null
        mediaBrowser = null
    }

    private fun bindMediaController() {
        mediaBrowserListenableFuture?.let { future ->
            future.addListener({
                try {
                    val browser = future.get()
                    mediaBrowser = browser
                    setMediaBrowserListener(browser)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun setMediaBrowserListener(browser: MediaBrowser) {
        isPlaying = isPlaybackIntended(browser)
        updatePlaybackState(browser.mediaMetadata, browser.currentMediaItemIndex)

        browser.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = isPlaybackIntended(browser)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = isPlaybackIntended(browser)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = isPlaybackIntended(browser)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updatePlaybackState(mediaMetadata, browser.currentMediaItemIndex)
                toggleOverlayVisibility(false)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updatePlaybackState(browser.mediaMetadata, browser.currentMediaItemIndex)
                toggleOverlayVisibility(false)
            }
        })
    }

    // Scrubbing briefly flips Player.isPlaying to false (BUFFERING); the sleeve must follow the
    // user's intent to play instead, so the record keeps spinning during a seek.
    private fun isPlaybackIntended(player: Player): Boolean =
        player.playWhenReady && player.playbackState != Player.STATE_ENDED

    private fun updatePlaybackState(mediaMetadata: MediaMetadata, itemIndex: Int) {
        if (LanRemoteSession.isActive()) return
        if ((mediaBrowser?.mediaItemCount ?: 0) == 0) return
        val extras = mediaMetadata.extras
        val isRadio = extras != null && Constants.MEDIA_TYPE_RADIO == extras.getString("type")

        isRadioStation = isRadio
        radioStationArtworkUri = mediaMetadata.artworkUri
        radioStationCoverArtId = extras?.getString("coverArtId")

        val mediaId = mediaBrowser?.currentMediaItem?.mediaId ?: extras?.getString("id")
        if (mediaId != null && queueItems.isNotEmpty()) {
            val idx = queueItems.indexOfFirst { it.id == mediaId }
            if (idx >= 0) {
                currentPlayingIndex = idx
                return
            }
        }
        currentPlayingIndex = itemIndex.coerceAtLeast(0)
    }
}
