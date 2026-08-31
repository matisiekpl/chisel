package com.mateuszwozniak.chisel.state

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.mateuszwozniak.chisel.model.AgentMode
import com.mateuszwozniak.chisel.model.AgentModel
import com.mateuszwozniak.chisel.model.EffortLevel

@Service(Service.Level.APP)
@State(name = "ChiselSettings", storages = [Storage("chisel.xml")])
class ChiselSettings : SimplePersistentStateComponent<ChiselSettings.Model>(Model()) {

    class Model : BaseState() {
        var defaultMode by enum(AgentMode.PLAN)
        var planModel by enum(AgentModel.OPUS)
        var planEffort by enum(EffortLevel.HIGH)
        var implementationModel by enum(AgentModel.SONNET)
        var implementationEffort by enum(EffortLevel.MEDIUM)
    }

    var defaultMode: AgentMode
        get() = state.defaultMode
        set(value) {
            state.defaultMode = value
        }

    var planModel: AgentModel
        get() = state.planModel
        set(value) {
            state.planModel = value
        }

    var planEffort: EffortLevel
        get() = state.planEffort
        set(value) {
            state.planEffort = value
        }

    var implementationModel: AgentModel
        get() = state.implementationModel
        set(value) {
            state.implementationModel = value
        }

    var implementationEffort: EffortLevel
        get() = state.implementationEffort
        set(value) {
            state.implementationEffort = value
        }

    fun modelFor(mode: AgentMode): AgentModel =
        if (mode == AgentMode.IMPLEMENTATION) implementationModel else planModel

    fun effortFor(mode: AgentMode): EffortLevel =
        if (mode == AgentMode.IMPLEMENTATION) implementationEffort else planEffort

    companion object {
        fun getInstance(): ChiselSettings = service()
    }
}
