package com.mateuszwozniak.chisel.ui

import com.intellij.util.ui.ImageUtil
import com.mateuszwozniak.chisel.model.PromptAttachment
import com.mateuszwozniak.chisel.util.ChiselPaths
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

object AttachmentTransfer {

    fun read(transferable: Transferable?): List<PromptAttachment> {
        if (transferable == null) return emptyList()
        files(transferable).takeIf { it.isNotEmpty() }?.let { return it }
        return listOfNotNull(image(transferable))
    }

    fun holdsFiles(transferable: Transferable?): Boolean {
        if (transferable == null) return false
        if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) return true
        return transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    }

    private fun files(transferable: Transferable): List<PromptAttachment> {
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
        val files = runCatching {
            @Suppress("UNCHECKED_CAST")
            transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
        }.getOrNull() ?: return emptyList()
        return files.mapNotNull { PromptAttachment.of(it.toPath()) }
    }

    private fun image(transferable: Transferable): PromptAttachment? {
        if (!transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) return null
        val image = runCatching {
            transferable.getTransferData(DataFlavor.imageFlavor) as? Image
        }.getOrNull() ?: return null
        return store(ImageUtil.toBufferedImage(image))
    }

    private fun store(image: BufferedImage): PromptAttachment? = runCatching {
        val directory = ChiselPaths.images() ?: return null
        val file = directory.resolve(UUID.randomUUID().toString() + ".png")
        ImageIO.write(image, "png", file.toFile())
        PromptAttachment.of(file)
    }.getOrNull()
}
