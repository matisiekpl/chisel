package com.mateuszwozniak.chisel.cli

import com.mateuszwozniak.chisel.model.AccountProfile
import com.mateuszwozniak.chisel.protocol.obj
import com.mateuszwozniak.chisel.protocol.string

object ClaudeAccount {

    fun read(): AccountProfile? {
        val account = ClaudeConfiguration.read()?.obj("oauthAccount") ?: return null
        return AccountProfile(
            fullName = account.string("fullName") ?: account.string("displayName"),
            email = account.string("emailAddress"),
            organizationName = account.string("organizationName"),
            organizationRole = account.string("organizationRole"),
            plan = account.string("organizationType"),
            billing = account.string("billingType"),
        )
    }
}
