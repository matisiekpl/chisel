package com.mateuszwozniak.chisel.cli

import com.mateuszwozniak.chisel.protocol.PermissionDecision
import com.mateuszwozniak.chisel.protocol.PermissionRequest
import com.mateuszwozniak.chisel.protocol.StreamEvent

interface SessionListener {

    fun onEvent(event: StreamEvent)

    fun onPermission(request: PermissionRequest, respond: (PermissionDecision) -> Unit)

    fun onPermissionCancelled(requestId: String)

    fun onStandardError(text: String)

    fun onTerminated(exitCode: Int, stopped: Boolean)
}
