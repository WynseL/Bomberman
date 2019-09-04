package com.wynsel.bomberman.models

data class Recipient(
    val name: String? = null,
    val mobileNumber: String? = null,
    var amount: Int = 0,
    var message: String? = null
)