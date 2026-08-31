package com.mateuszwozniak.chisel.state

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class ConversationEntry : BaseState() {
    var id by string()
    var title by string()
    var sessionId by string()
    var mode by string()
    var model by string()
    var effort by string()
    var updatedAt by property(0L)
}

@Service(Service.Level.PROJECT)
@State(name = "ChiselConversations", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ConversationState :
    SimplePersistentStateComponent<ConversationState.Model>(Model()) {

    class Model : BaseState() {
        var entries by list<ConversationEntry>()
        var dismissedSessions by list<String>()
        var selectedId by string()
    }

    companion object {
        fun getInstance(project: Project): ConversationState = project.service()
    }
}
