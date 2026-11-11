package com.echofree.ui.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echofree.data.models.Post
import com.echofree.data.repository.PostRepository
import com.echofree.network.ApiService
import com.echofree.util.FeedRanker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class FeedUiState {
    object Loading : FeedUiState()
    data class Success(val posts: List<Post>) : FeedUiState()
    object Error : FeedUiState()
    object Empty : FeedUiState()
}

class FeedViewModel : ViewModel() {

    private val repository = PostRepository(ApiService.create())

    private val _uiState = MutableLiveData<FeedUiState>(FeedUiState.Loading)
    val uiState: LiveData<FeedUiState> = _uiState

    private val _isExploreMode = MutableLiveData(false)
    val isExploreMode: LiveData<Boolean> = _isExploreMode

    var rankConfig = FeedRanker.RankConfig()
        private set

    private var loadJob: Job? = null

    init {
        loadFeed()
    }

    fun loadFeed(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        _uiState.value = FeedUiState.Loading
        loadJob = viewModelScope.launch {
            val result = repository.getFeed(
                forceRefresh = forceRefresh,
                exploreMode = _isExploreMode.value ?: false,
                config = rankConfig
            )
            result.fold(
                onSuccess = { posts ->
                    _uiState.value = if (posts.isEmpty()) FeedUiState.Empty
                                     else FeedUiState.Success(posts)
                },
                onFailure = { _uiState.value = FeedUiState.Error }
            )
        }
    }

    fun setExploreMode(enabled: Boolean) {
        if (_isExploreMode.value != enabled) {
            _isExploreMode.value = enabled
            loadFeed(forceRefresh = false)
        }
    }

    fun refresh() = loadFeed(forceRefresh = true)
}
