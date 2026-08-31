package com.mateuszwozniak.chisel.ui

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.mateuszwozniak.chisel.model.AgentModel
import com.mateuszwozniak.chisel.model.EffortLevel
import com.mateuszwozniak.chisel.state.ChiselSettings
import kotlin.reflect.KMutableProperty0

class ChiselConfigurable : BoundConfigurable("Chisel") {

    private val settings = ChiselSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        group("Plan") {
            row("Model:") { modelBox(settings::planModel) }
            row("Effort:") { effortBox(settings::planEffort) }
        }
        group("Implementation") {
            row("Model:") { modelBox(settings::implementationModel) }
            row("Effort:") { effortBox(settings::implementationEffort) }
        }
        row {
            comment("Applies to conversations started after the change.")
        }
    }

    private fun Row.modelBox(
        property: KMutableProperty0<AgentModel>,
    ): Cell<ComboBox<AgentModel>> = comboBox(AgentModel.entries.toList())
        .applyToComponent {
            renderer = SimpleListCellRenderer.create("") { model: AgentModel -> model.label }
        }
        .bind(property)

    private fun Row.effortBox(
        property: KMutableProperty0<EffortLevel>,
    ): Cell<ComboBox<EffortLevel>> = comboBox(EffortLevel.entries.toList())
        .applyToComponent {
            renderer = SimpleListCellRenderer.create("") { effort: EffortLevel -> effort.label }
        }
        .bind(property)

    private fun <T : Any> Cell<ComboBox<T>>.bind(
        property: KMutableProperty0<T>,
    ): Cell<ComboBox<T>> = bindItem({ property.get() }, { value -> value?.let(property::set) })
}
