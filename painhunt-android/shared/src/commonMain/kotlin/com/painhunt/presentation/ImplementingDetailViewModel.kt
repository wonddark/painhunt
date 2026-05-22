package com.painhunt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.painhunt.data.IdeasRepository
import com.painhunt.data.ImplementationsRepository
import com.painhunt.domain.Idea
import com.painhunt.domain.Implementation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImplementingDetailUiState(
    val implementation: Implementation? = null,
    val idea: Idea? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val removed: Boolean = false,
    val error: String? = null,
)

class ImplementingDetailViewModel(
    private val implementationsRepository: ImplementationsRepository,
    private val ideasRepository: IdeasRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImplementingDetailUiState())
    val uiState: StateFlow<ImplementingDetailUiState> = _uiState.asStateFlow()

    fun load(implementationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val implementation = implementationsRepository.getById(implementationId)
                val idea = ideasRepository.getIdeaById(implementation.ideaId)
                _uiState.update { it.copy(implementation = implementation, idea = idea, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleTask(goalIndex: Int, taskIndex: Int) {
        val impl = _uiState.value.implementation ?: return
        val updatedGoals = impl.goals.mapIndexed { gi, goal ->
            if (gi == goalIndex) {
                goal.copy(tasks = goal.tasks.mapIndexed { ti, task ->
                    if (ti == taskIndex) task.copy(done = !task.done) else task
                })
            } else goal
        }
        val updatedImpl = impl.copy(goals = updatedGoals)
        _uiState.update { it.copy(implementation = updatedImpl, isSaving = true) }
        viewModelScope.launch {
            try {
                implementationsRepository.updateGoals(impl.id, updatedGoals)
                _uiState.update { it.copy(isSaving = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(implementation = impl, isSaving = false, error = e.message) }
            }
        }
    }

    fun remove() {
        val impl = _uiState.value.implementation ?: return
        viewModelScope.launch {
            try {
                implementationsRepository.remove(impl.id)
                _uiState.update { it.copy(removed = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
