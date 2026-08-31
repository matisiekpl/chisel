package com.mateuszwozniak.chisel.model

data class AccountProfile(
    val fullName: String?,
    val email: String?,
    val organizationName: String?,
    val organizationRole: String?,
    val plan: String?,
    val billing: String?,
)
