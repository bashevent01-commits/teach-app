package com.knowapp.android.data

import kotlinx.coroutines.flow.MutableStateFlow

// Lets one screen ask the app to open another, and carries a member chosen for a payment
object NavRequests {
    val route = MutableStateFlow<String?>(null)
    val payMemberId = MutableStateFlow<Int?>(null)
}
