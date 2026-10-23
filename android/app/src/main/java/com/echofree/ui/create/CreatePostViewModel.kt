package com.echofree.ui.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echofree.data.models.CreatePostRequest
import com.echofree.data.repository.PostRepository
import com.echofree.network.ApiService
import kotlinx.coroutines.launch

sealed class CreateState {
    object Loading : CreateState()
    object Success : CreateState()
    data class Error(val message: String) : CreateState()
}

class CreatePostViewModel : ViewModel() {

    private val repository = PostRepository(ApiService.create())

    private val _createState = MutableLiveData<CreateState?>()
    val createState: LiveData<CreateState?> = _createState

    fun createPost(content: String, tags: List<String>, authorName: String) {
        _createState.value = CreateState.Loading
        viewModelScope.launch {
            val result = repository.createPost(
                CreatePostRequest(content, tags, authorName)
            )
            _createState.value = result.fold(
                onSuccess = { CreateState.Success },
                onFailure = { CreateState.Error(it.message ?: "Failed to post") }
            )
        }
    }
}
