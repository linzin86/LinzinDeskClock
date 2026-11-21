package com.linzin.linzindeskclock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WeatherStatusLabels(info: WeatherInfo, isRefreshing: Boolean, onRefresh: () -> Unit) {
    val fixTime = info.location?.knownFixTime(System.currentTimeMillis())
    val locationTime = fixTime?.let {
        SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(Date(it))
    } ?: stringResource(R.string.weather_location_time_unknown)
    val source = stringResource(when (info.location?.provider) {
        "gps" -> R.string.weather_location_source_gps
        "network" -> R.string.weather_location_source_network
        "passive" -> R.string.weather_location_source_passive
        else -> R.string.weather_location_source_unknown
    })
    Text(
        text = stringResource(R.string.weather_last_location, locationTime, source),
        color = Color(0xFF81C7D4), fontSize = 11.sp, lineHeight = 14.sp
    )
    if (info.isStale) {
        val time = SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(info.fetchedAtMillis))
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(enabled = !isRefreshing, role = Role.Button, onClick = onRefresh),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.weather_update_unavailable, time),
                color = Color(0xFF81C7D4),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            Text(
                text = stringResource(if (isRefreshing) R.string.weather_refreshing_title else R.string.weather_retry),
                color = Color(0xFF81C7D4),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}
