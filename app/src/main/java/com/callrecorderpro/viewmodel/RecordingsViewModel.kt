package com.callrecorderpro.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorderpro.data.RecordingItem
import com.callrecorderpro.data.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeFilter { ALL, TODAY, WEEK, MONTH }

data class RecordingsUiState(
    val recordings: List<RecordingItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val timeFilter: TimeFilter = TimeFilter.ALL,
    val totalCount: Int = 0,
    val totalDurationSeconds: Long = 0L
)

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordingRepository(application)

    private val _allRecordings  = MutableStateFlow<List<RecordingItem>>(emptyList())
    private val _isLoading      = MutableStateFlow(true)
    private val _error          = MutableStateFlow<String?>(null)
    private val _searchQuery    = MutableStateFlow("")
    private val _timeFilter     = MutableStateFlow(TimeFilter.ALL)

    val uiState: StateFlow<RecordingsUiState> = combine(
        _allRecordings, _isLoading, _error, _searchQuery, _timeFilter
    ) { all, loading, error, query, filter ->
        val filtered = applyFilters(all, query, filter)
        RecordingsUiState(
            recordings = filtered,
            isLoading = loading,
            error = error,
            searchQuery = query,
            timeFilter = filter,
            totalCount = filtered.size,
            totalDurationSeconds = filtered.sumOf { it.durationSeconds.toLong() }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordingsUiState()
    )

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _allRecordings.value = repository.getAllRecordings()
            } catch (e: Exception) {
                _error.value = "Could not load recordings: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setTimeFilter(filter: TimeFilter) { _timeFilter.value = filter }

    fun deleteRecording(item: RecordingItem) {
        viewModelScope.launch {
            repository.deleteRecording(item)
            _allRecordings.value = _allRecordings.value.filter { it.id != item.id }
        }
    }

    private fun applyFilters(
        items: List<RecordingItem>,
        query: String,
        filter: TimeFilter
    ): List<RecordingItem> {
        val now = System.currentTimeMillis()
        val cutoff = when (filter) {
            TimeFilter.ALL   -> 0L
            TimeFilter.TODAY -> startOfDay()
            TimeFilter.WEEK  -> now - 7L * 24 * 60 * 60 * 1000
            TimeFilter.MONTH -> now - 30L * 24 * 60 * 60 * 1000
        }
        return items
            .filter { it.timestampMs >= cutoff }
            .filter { item ->
                if (query.isBlank()) true
                else {
                    item.displayName.contains(query, ignoreCase = true) ||
                    item.phoneNumber.contains(query)
                }
            }
    }

    private fun startOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
