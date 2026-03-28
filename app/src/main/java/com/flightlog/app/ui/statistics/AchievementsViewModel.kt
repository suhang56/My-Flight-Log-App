package com.flightlog.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightlog.app.data.achievements.AchievementDefinitions
import com.flightlog.app.data.achievements.Tier
import com.flightlog.app.data.local.entity.Achievement
import com.flightlog.app.data.repository.AchievementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AchievementUiItem(
    val id: String,
    val name: String,
    val description: String,
    val tier: Tier,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val unlockedAt: Long?,
    val isNew: Boolean
)

data class AchievementsUiState(
    val unlockedCount: Int = 0,
    val totalCount: Int = AchievementDefinitions.ALL.size,
    val tiers: List<TierGroup> = emptyList()
)

data class TierGroup(
    val tier: Tier,
    val items: List<AchievementUiItem>
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    val uiState: StateFlow<AchievementsUiState> = achievementRepository.getAll()
        .map { achievements -> buildUiState(achievements) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())

    fun markAllSeen() {
        viewModelScope.launch {
            achievementRepository.markAllSeen()
        }
    }

    private fun buildUiState(achievements: List<Achievement>): AchievementsUiState {
        val achievementMap = achievements.associateBy { it.id }
        val unlockedCount = achievements.count { it.unlockedAt != null }

        val items = AchievementDefinitions.ALL.map { def ->
            val achievement = achievementMap[def.id]
            AchievementUiItem(
                id = def.id,
                name = def.name,
                description = def.description,
                tier = def.tier,
                icon = def.icon,
                unlockedAt = achievement?.unlockedAt,
                isNew = achievement?.unlockedAt != null && achievement.seenByUser == false
            )
        }

        // Group by tier, ordered Platinum -> Gold -> Silver -> Bronze
        val tierOrder = listOf(Tier.PLATINUM, Tier.GOLD, Tier.SILVER, Tier.BRONZE)
        val tiers = tierOrder.map { tier ->
            TierGroup(
                tier = tier,
                items = items.filter { it.tier == tier }
            )
        }

        return AchievementsUiState(
            unlockedCount = unlockedCount,
            totalCount = AchievementDefinitions.ALL.size,
            tiers = tiers
        )
    }
}
