package com.mateuszwozniak.chisel.service

import com.mateuszwozniak.chisel.protocol.PermissionDecision
import com.mateuszwozniak.chisel.protocol.PermissionRequest

interface PermissionRouter {

    fun handle(
        controller: ConversationController,
        request: PermissionRequest,
        respond: (PermissionDecision) -> Unit,
    )

    fun cancel(requestId: String)
}
