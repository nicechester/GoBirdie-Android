package io.github.nicechester.gobirdie.ui.scorecards

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nicechester.gobirdie.core.data.RoundStore
import io.github.nicechester.gobirdie.core.model.Round
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ScorecardsViewModel @Inject constructor(
    private val roundStore: RoundStore,
) : ViewModel() {

    private val _rounds = MutableStateFlow<List<Round>>(emptyList())
    val rounds: StateFlow<List<Round>> = _rounds

    fun load() {
        _rounds.value = roundStore.loadAll()
    }

    fun delete(id: String) {
        roundStore.delete(id)
        _rounds.value = _rounds.value.filter { it.id != id }
    }
}
