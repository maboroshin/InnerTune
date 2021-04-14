package com.zionhuang.music.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import androidx.paging.TerminalSeparatorType.FULLY_COMPLETE
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zionhuang.music.R
import com.zionhuang.music.constants.Constants.HEADER_ITEM_ID
import com.zionhuang.music.constants.MediaConstants.EXTRA_SONG
import com.zionhuang.music.constants.ORDER_NAME
import com.zionhuang.music.db.SongRepository
import com.zionhuang.music.db.entities.ArtistEntity
import com.zionhuang.music.db.entities.ChannelEntity
import com.zionhuang.music.db.entities.PlaylistEntity
import com.zionhuang.music.db.entities.Song
import com.zionhuang.music.download.DownloadService
import com.zionhuang.music.download.DownloadServiceConnection
import com.zionhuang.music.download.DownloadTask
import com.zionhuang.music.extensions.getActivity
import com.zionhuang.music.extensions.preference
import com.zionhuang.music.ui.activities.MainActivity
import com.zionhuang.music.ui.fragments.dialogs.EditSongDialog
import com.zionhuang.music.ui.listeners.SongPopupMenuListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SongsViewModel(application: Application) : AndroidViewModel(application) {
    val songRepository: SongRepository = SongRepository(application)

    var sortType by preference(R.string.pref_sort_type, ORDER_NAME)
    var sortDescending by preference(R.string.pref_sort_descending, true)

    val allSongsFlow: Flow<PagingData<Song>> by lazy {
        Pager(PagingConfig(pageSize = 50, enablePlaceholders = true)) {
            songRepository.getAllSongsPagingSource(sortType, sortDescending)
        }.flow.map { pagingData ->
            pagingData.insertHeaderItem(FULLY_COMPLETE, Song(HEADER_ITEM_ID))
        }.cachedIn(viewModelScope)
    }

    val allArtistsFlow: Flow<PagingData<ArtistEntity>> by lazy {
        Pager(PagingConfig(pageSize = 50)) {
            songRepository.allArtistsPagingSource
        }.flow.cachedIn(viewModelScope)
    }

    val allChannelsFlow: Flow<PagingData<ChannelEntity>> by lazy {
        Pager(PagingConfig(pageSize = 50)) {
            songRepository.allChannelsPagingSource
        }.flow.cachedIn(viewModelScope)
    }

    val allPlaylistsFlow: Flow<PagingData<PlaylistEntity>> by lazy {
        Pager(PagingConfig(pageSize = 50)) {
            songRepository.allPlaylistsPagingSource
        }.flow.cachedIn(viewModelScope)
    }

    fun getArtistSongsAsFlow(artistId: Int) = Pager(PagingConfig(pageSize = 50)) {
        songRepository.getArtistSongsAsPagingSource(artistId, sortType, sortDescending)
    }.flow.map { pagingData ->
        pagingData.insertHeaderItem(FULLY_COMPLETE, Song(HEADER_ITEM_ID))
    }.cachedIn(viewModelScope)

    fun getPlaylistSongsAsFlow(playlistId: Int) = Pager(PagingConfig(pageSize = 50)) {
        songRepository.getPlaylistSongsAsPagingSource(playlistId)
    }.flow.map { pagingData ->
        pagingData.map { it.song }
    }.cachedIn(viewModelScope)

    fun getChannelSongsAsFlow(channelId: String) = Pager(PagingConfig(pageSize = 50)) {
        songRepository.getChannelSongsAsPagingSource(channelId)
    }.flow.map { pagingData ->
        pagingData.insertHeaderItem(FULLY_COMPLETE, Song(HEADER_ITEM_ID))
    }.cachedIn(viewModelScope)

    private val _deleteSong = MutableLiveData<Song>()
    val deleteSong: LiveData<Song> get() = _deleteSong

    val songPopupMenuListener = object : SongPopupMenuListener {
        override fun editSong(song: Song, context: Context) {
            (context.getActivity() as? MainActivity)?.let { activity ->
                EditSongDialog().apply {
                    arguments = bundleOf(EXTRA_SONG to song)
                }.show(activity.supportFragmentManager, EditSongDialog.TAG)
            }
        }

        override fun playNext(song: Song, context: Context) {
            TODO("Not yet implemented")
        }

        override fun addToQueue(song: Song, context: Context) {
            TODO("Not yet implemented")
        }

        override fun addToPlaylist(song: Song, context: Context) {
            viewModelScope.launch {
                val playlists = songRepository.getPlaylists()
                MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.dialog_choose_playlist_title)
                        .setItems(playlists.map { it.name }.toTypedArray()) { _, i ->
                            viewModelScope.launch {
                                playlists[i].playlistId?.let { songRepository.insertToPlaylist(song, it) }
                            }
                        }
                        .show()
            }
        }

        override fun downloadSong(songId: String, context: Context) {
            context.startService(Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_DOWNLOAD_MUSIC
                putExtra("task", DownloadTask(id = songId))
            })
        }

        override fun deleteSong(songId: String) {
            viewModelScope.launch {
                songRepository.getSongById(songId)?.let { song ->
                    songRepository.deleteSong(songId)
                    _deleteSong.postValue(song)
                }
            }
        }
    }

    val downloadServiceConnection = DownloadServiceConnection(application)
}
