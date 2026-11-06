package com.knowapp.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowapp.android.data.PlanState
import com.knowapp.android.ui.theme.PillShape

// Shown when someone on the free plan tries something that needs the paid plan
@Composable
fun UpgradeBanner(message: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Upgrade to continue", fontWeight = FontWeight.Bold, color = Color(0xFF78350F))
            Text(message, fontSize = 14.sp, color = Color(0xFF78350F))
            Button(onClick = { PlanState.requestUpgrade() }, shape = PillShape, modifier = Modifier.fillMaxWidth()) { Text("Upgrade for KES 200 a month", fontWeight = FontWeight.Bold) }
        }
    }
}
