package com.flightlog.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.repository.AchievementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NavBadgeViewModel @Inject constructor(
    achievementRepository: AchievementRepository
) : ViewModel() {

    val hasUnseenAchievements: StateFlow<Boolean> = achievementRepository.getAll()
        .map { achievements ->
            achievements.any { it.unlockedAt != null && !it.seenByUser }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
