package com.mateuszwozniak.chisel.ui.approval

import com.intellij.openapi.util.Key

object AnnotationKeys {

    const val DIFF_PLACE = "Chisel.Review"

    val MODEL: Key<AnnotationModel> = Key.create("chisel.annotation.model")
}
