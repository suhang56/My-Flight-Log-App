package com.flightlog.app.ui.logbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightlog.app.R

private val StarGold = Color(0xFFFFB300)

@Composable
fun RatingSection(
    currentRating: Int?,
    onRatingChanged: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.rating_label),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (star in 1..5) {
                val isFilled = currentRating != null && star <= currentRating
                val description = stringResource(R.string.rating_star_button, star)
                Icon(
                    imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = description,
                    tint = if (isFilled) StarGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = description }
                        .clickable {
                            if (currentRating == star) {
                                onRatingChanged(null)
                            } else {
                                onRatingChanged(star)
                            }
                        }
                )
            }
        }
        if (currentRating == null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.rating_not_rated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
