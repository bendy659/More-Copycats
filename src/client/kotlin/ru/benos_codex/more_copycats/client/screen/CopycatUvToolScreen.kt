package ru.benos_codex.more_copycats.client.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.BlockItem
import ru.benos_codex.more_copycats.block.entity.CopycatBiteBlockEntity
import ru.benos_codex.more_copycats.block.entity.CopycatByteBlockEntity
import ru.benos_codex.more_copycats.menu.CopycatUvToolMenu
import ru.benos_codex.more_copycats.network.UvToolNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.renderer.texture.TextureAtlasSprite

class CopycatUvToolScreen(
    menu: CopycatUvToolMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<CopycatUvToolMenu>(menu, playerInventory, title) {

    companion object {
        private val UI_TEX = Identifier.parse("more_copycats:textures/gui/copycat_uv_tool/ui.png")
        private val BIG_UI_TEX = Identifier.parse("more_copycats:textures/gui/copycat_uv_tool/big_ui.png")

        private const val UI_W  = 176
        private const val UI_H  = 182
        private const val TEX_W = 256
        private const val TEX_H = 256

        private const val FIELD_U        = 192
        private const val FIELD_V_NORMAL = 0
        private const val FIELD_V_FOCUS  = 16
        private const val FIELD_W        = 30
        private const val FIELD_H        = 12

        private const val BTN_U          = 192
        private const val BTN_V_NORMAL   = 32
        private const val BTN_V_FOCUS    = 48
        private const val BTN_V_PRESSED  = 64
        private const val BTN_V_SELECTED = 80

        private const val SLICE_CORNER = 3
        private const val SLICE_MID_W  = 2
        private const val SLICE_MID_H  = 2
        private const val SLICE_GAP    = 1

        private const val LABEL_X = 5
        private const val LABEL_Y = 19
        private const val FIELD_X = 15
        private const val FIELD_Y = 18

        private const val TEXT_COLOR = 0xFFFFD6A0.toInt()

        private const val EDIT_X = 56
        private const val EDIT_Y = 17
        private const val EDIT_W = 62
        private const val EDIT_H = 62

        private const val BIG_EDIT_X = 9
        private const val BIG_EDIT_Y = 10
        private const val BIG_EDIT_W = 158
        private const val BIG_EDIT_H = 158

        private const val TILE_SIZE = 16
    }

    private data class FieldEntry(val label: String, val labelX: Int, val labelY: Int, val box: EditBox)

    private val fields = mutableListOf<FieldEntry>()
    private lateinit var resetButton: NineSliceButton
    private lateinit var resetSizeButton: NineSliceButton
    private var expandRectX = 0
    private var expandRectY = 0
    private var closeRectX = 0
    private var closeRectY = 0

    private enum class DragMode { NONE, MOVE, RESIZE, PAN }
    private var dragMode = DragMode.NONE
    private var dragStartMouseX = 0.0
    private var dragStartMouseY = 0.0
    private var dragStartU = 0
    private var dragStartV = 0
    private var dragStartW = 1
    private var dragStartH = 1
    private var panX = 0.0
    private var panY = 0.0
    private var panStartX = 0.0
    private var panStartY = 0.0

    private var zoom = 1.0f
    private var zoomTarget = 1.0f
    private var showBigEditor = false
    private var lastSent: UvSnapshot? = null
    private var sourceFace: Direction = Direction.NORTH

    override fun init() {
        imageWidth = UI_W
        imageHeight = UI_H
        super.init()

        fields.clear()
        val face = Direction.entries.getOrNull(menu.faceOrdinal) ?: Direction.NORTH
        sourceFace = face
        val init = loadInitialUv(face)
        sourceFace = init.source
        val labels = listOf("X", "Y", "W", "H")
        val baseLabelY = LABEL_Y
        val baseFieldY = FIELD_Y
        val step = FIELD_H + 1

        for (i in labels.indices) {
            val label = labels[i]
            val labelY = baseLabelY + step * i
            val fieldY = baseFieldY + step * i
            val box = EditBox(font, leftPos + FIELD_X + 1, topPos + fieldY + 1, FIELD_W, FIELD_H, Component.empty())
            box.setBordered(false)
            box.setTextColor(TEXT_COLOR)
            box.setTextColorUneditable(TEXT_COLOR)
            box.setMaxLength(3)
            box.setValue(
                when (i) {
                    0 -> init.u.toString()
                    1 -> init.v.toString()
                    2 -> init.w.toString()
                    3 -> init.h.toString()
                    else -> "0"
                }
            )
            addRenderableWidget(box)
            fields.add(FieldEntry(label, LABEL_X, labelY, box))
        }

        resetButton = NineSliceButton(leftPos + 4, topPos + 73, 40, 8, Component.literal("Reset X/Y")) {
            setFieldValue(0, "0")
            setFieldValue(1, "0")
        }
        addRenderableWidget(resetButton)

        resetSizeButton = NineSliceButton(leftPos + 4, topPos + 73 + 8 + 2, 40, 8, Component.literal("Reset W/H")) {
            val size = getDefaultPartSize()
            setFieldValue(2, size.toString())
            setFieldValue(3, size.toString())
        }
        addRenderableWidget(resetSizeButton)

        expandRectX = leftPos + 130
        expandRectY = topPos + 75
        closeRectX = leftPos + 157
        closeRectY = topPos + 169

        lastSent = currentSnapshot()
    }

    override fun renderBg(gui: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        drawBackground(gui)
        for (field in fields) {
            drawFieldBg(gui, field.box, field.box.isFocused)
        }
        if (!showBigEditor) {
            drawEditor(gui, EDIT_X, EDIT_Y, EDIT_W, EDIT_H)
            drawExpandButtonIcon(gui)
        }
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(gui, mouseX, mouseY, partialTick)
        if (showBigEditor) {
            drawBigOverlay(gui)
        }
    }

    private fun drawBackground(gui: GuiGraphics) {
        gui.blit(RenderPipelines.GUI_TEXTURED, UI_TEX, leftPos - 1, topPos - 1, 0f, 0f, imageWidth, imageHeight, TEX_W, TEX_H)
    }

    private fun drawFieldBg(gui: GuiGraphics, field: EditBox, focused: Boolean) {
        val v = if (focused) FIELD_V_FOCUS else FIELD_V_NORMAL
        gui.blit(RenderPipelines.GUI_TEXTURED, UI_TEX, field.x - 2, field.y - 2, FIELD_U.toFloat(), v.toFloat(), FIELD_W, FIELD_H, TEX_W, TEX_H)
    }

    private fun drawExpandButtonIcon(gui: GuiGraphics) {
        gui.blit(RenderPipelines.GUI_TEXTURED, UI_TEX, expandRectX, expandRectY, 213f, 96f, 7, 7, TEX_W, TEX_H)
    }

    private fun drawCloseButtonIcon(gui: GuiGraphics) {
        gui.blit(RenderPipelines.GUI_TEXTURED, UI_TEX, closeRectX, closeRectY, 213f, 96f, 7, 7, TEX_W, TEX_H)
    }

    private fun drawBigOverlay(gui: GuiGraphics) {
        gui.fill(0, 0, width, height, 0xAA000000.toInt())
        gui.blit(RenderPipelines.GUI_TEXTURED, BIG_UI_TEX, leftPos - 1, topPos - 1, 0f, 0f, imageWidth, imageHeight, TEX_W, TEX_H)
        drawEditor(gui, BIG_EDIT_X, BIG_EDIT_Y, BIG_EDIT_W, BIG_EDIT_H)
        drawCloseButtonIcon(gui)
    }

    private fun drawEditor(gui: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        val ex = leftPos + x
        val ey = topPos + y

        // Debug background
        gui.fill(ex, ey, ex + w, ey + h, 0x553366FF)

        val baseTile = getMaterialTileSize()
        zoom += (zoomTarget - zoom) * 0.2f
        val tile = baseTile * zoom
        val gridSize = tile * 3f
        val gridX = ex + (w - gridSize) / 2f + panX
        val gridY = ey + (h - gridSize) / 2f + panY

        gui.enableScissor(ex, ey, ex + w, ey + h)

        val sprite = getMaterialSprite()
        if (sprite != null) {
            for (gy in 0 until 3) {
                for (gx in 0 until 3) {
                    val dx = (gridX + gx * tile).toInt()
                    val dy = (gridY + gy * tile).toInt()
                    gui.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, dx, dy, tile.toInt(), tile.toInt())
                }
            }
        }

        // 3x3 grid
        val gridColor = 0x55FFFFFF
        gui.vLine((gridX + tile).toInt(), gridY.toInt(), (gridY + gridSize).toInt(), gridColor)
        gui.vLine((gridX + tile * 2f).toInt(), gridY.toInt(), (gridY + gridSize).toInt(), gridColor)
        gui.hLine(gridX.toInt(), (gridX + gridSize).toInt(), (gridY + tile).toInt(), gridColor)
        gui.hLine(gridX.toInt(), (gridX + gridSize).toInt(), (gridY + tile * 2f).toInt(), gridColor)

        // Selection
        val u = getFieldInt(0, 0)
        val v = getFieldInt(1, 0)
        val w = clampSize(getFieldInt(2, 1))
        val h = clampSize(getFieldInt(3, 1))

        val scale = tile / 16f
        val selX = (gridX + (gridSize - w * scale) / 2f + u * scale).toInt()
        val selY = (gridY + (gridSize - h * scale) / 2f + v * scale).toInt()
        val selW = (w * scale).toInt()
        val selH = (h * scale).toInt()

        gui.fill(selX, selY, selX + selW, selY + selH, 0x44FFFFFF)
        gui.hLine(selX, selX + selW, selY, 0x88FFFFFF.toInt())
        gui.hLine(selX, selX + selW, selY + selH, 0x88FFFFFF.toInt())
        gui.vLine(selX, selY, selY + selH, 0x88FFFFFF.toInt())
        gui.vLine(selX + selW, selY, selY + selH, 0x88FFFFFF.toInt())

        val handleSize = 2
        gui.fill(selX - handleSize, selY - handleSize, selX + handleSize + 1, selY + handleSize + 1, 0xFF00C2FF.toInt())
        gui.fill(selX + selW - handleSize, selY + selH - handleSize, selX + selW + handleSize + 1, selY + selH + handleSize + 1, 0xFFFFC857.toInt())

        gui.disableScissor()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()
        val button = mouseButtonEvent.button()
        if (showBigEditor) {
            if (button == 0 && isInside(mouseX, mouseY, closeRectX, closeRectY, 7, 7)) {
                playClickSound()
                setBigEditorVisible(false)
                return true
            }
            if (handleEditorClick(mouseX, mouseY, button)) {
                return true
            }
            return true
        }
        if (button == 0 && isInside(mouseX, mouseY, expandRectX, expandRectY, 7, 7)) {
            playClickSound()
            setBigEditorVisible(true)
            return true
        }
        if (handleEditorClick(mouseX, mouseY, button)) {
            return true
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (dragMode != DragMode.NONE) {
            handleEditorDrag(mouseButtonEvent.x(), mouseButtonEvent.y())
            return true
        }
        if (showBigEditor) {
            return true
        }
        return super.mouseDragged(mouseButtonEvent, dragX, dragY)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (dragMode != DragMode.NONE) {
            dragMode = DragMode.NONE
            return true
        }
        if (showBigEditor) {
            return true
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    private fun handleEditorClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val rect = getEditorRect()
        val ex = rect[0]
        val ey = rect[1]
        val ew = rect[2]
        val eh = rect[3]
        if (mouseX < ex || mouseY < ey || mouseX >= ex + ew || mouseY >= ey + eh) return false

        if (button == 1 || button == 2) {
            dragMode = DragMode.PAN
            dragStartMouseX = mouseX
            dragStartMouseY = mouseY
            panStartX = panX
            panStartY = panY
            return true
        }

        val u = getFieldInt(0, 0)
        val v = getFieldInt(1, 0)
        val w = clampSize(getFieldInt(2, 1))
        val h = clampSize(getFieldInt(3, 1))

        val baseTile = getMaterialTileSize()
        val tile = baseTile * zoom
        val gridSize = tile * 3f
        val gridX = ex + (ew - gridSize) / 2f + panX
        val gridY = ey + (eh - gridSize) / 2f + panY
        val scale = tile / 16.0
        val selX = gridX + (gridSize - w * scale) / 2.0 + u * scale
        val selY = gridY + (gridSize - h * scale) / 2.0 + v * scale
        val selW = w * scale
        val selH = h * scale

        val handleSize = 2.0
        val leftTopHit = mouseX >= selX - handleSize && mouseX <= selX + handleSize &&
            mouseY >= selY - handleSize && mouseY <= selY + handleSize
        val rightBottomHit = mouseX >= selX + selW - handleSize && mouseX <= selX + selW + handleSize &&
            mouseY >= selY + selH - handleSize && mouseY <= selY + selH + handleSize
        val inside = mouseX >= selX && mouseX <= selX + selW && mouseY >= selY && mouseY <= selY + selH

        dragMode = when {
            rightBottomHit -> DragMode.RESIZE
            leftTopHit || inside -> DragMode.MOVE
            else -> DragMode.PAN
        }

        if (dragMode == DragMode.NONE) return false

        dragStartMouseX = mouseX
        dragStartMouseY = mouseY
        dragStartU = u
        dragStartV = v
        dragStartW = w
        dragStartH = h
        panStartX = panX
        panStartY = panY
        return true
    }

    private fun handleEditorDrag(mouseX: Double, mouseY: Double) {
        val dx = mouseX - dragStartMouseX
        val dy = mouseY - dragStartMouseY
        val scaleX = 16.0 / (getMaterialTileSize() * zoom)
        val scaleY = 16.0 / (getMaterialTileSize() * zoom)

        when (dragMode) {
            DragMode.MOVE -> {
                val newU = (dragStartU + dx * scaleX).toInt()
                val newV = (dragStartV + dy * scaleY).toInt()
                setFieldValue(0, newU.toString())
                setFieldValue(1, newV.toString())
                sendUpdateIfChanged()
            }
            DragMode.RESIZE -> {
                val newW = clampSize((dragStartW + dx * scaleX).toInt())
                val newH = clampSize((dragStartH + dy * scaleY).toInt())
                val absU = relToAbs(dragStartU, dragStartW)
                val absV = relToAbs(dragStartV, dragStartH)
                val newRelU = absToRel(absU, newW)
                val newRelV = absToRel(absV, newH)
                setFieldValue(0, newRelU.toString())
                setFieldValue(1, newRelV.toString())
                setFieldValue(2, newW.toString())
                setFieldValue(3, newH.toString())
                sendUpdateIfChanged()
            }
            DragMode.PAN -> {
                panX = panStartX + dx
                panY = panStartY + dy
            }
            else -> Unit
        }
    }

    override fun renderLabels(gui: GuiGraphics, mouseX: Int, mouseY: Int) {
        for (field in fields) {
            gui.drawString(font, field.label, field.labelX + 1, field.labelY + 1, TEXT_COLOR, false)
        }
    }

    private fun setFieldValue(index: Int, value: String) {
        if (index in fields.indices) {
            fields[index].box.setValue(value)
        }
    }

    private fun setBigEditorVisible(visible: Boolean) {
        showBigEditor = visible
    }

    private fun isInside(mouseX: Double, mouseY: Double, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h
    }

    private fun playClickSound() {
        Minecraft.getInstance().soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f))
    }

    private fun getFieldInt(index: Int, default: Int): Int {
        if (index !in fields.indices) return default
        return fields[index].box.value.toIntOrNull() ?: default
    }

    private fun getEditorRect(): IntArray {
        return if (showBigEditor) {
            intArrayOf(leftPos + BIG_EDIT_X, topPos + BIG_EDIT_Y, BIG_EDIT_W, BIG_EDIT_H)
        } else {
            intArrayOf(leftPos + EDIT_X, topPos + EDIT_Y, EDIT_W, EDIT_H)
        }
    }

    override fun containerTick() {
        super.containerTick()
        sendUpdateIfChanged()
    }

    private fun sendUpdateIfChanged() {
        val snapshot = currentSnapshot() ?: return
        if (snapshot == lastSent) return
        ClientPlayNetworking.send(
            UvToolNetworking.UvToolUpdatePayload(
                menu.pos,
                menu.partIndex,
                menu.faceOrdinal,
                snapshot.u,
                snapshot.v,
                snapshot.w,
                snapshot.h,
                snapshot.sourceFace.ordinal,
                snapshot.materialId != null,
                snapshot.materialId
            )
        )
        lastSent = snapshot
    }

    private data class UvSnapshot(
        val u: Int,
        val v: Int,
        val w: Int,
        val h: Int,
        val sourceFace: Direction,
        val materialId: Identifier?
    )

    private fun currentSnapshot(): UvSnapshot? {
        if (minecraft?.level == null) return null
        val u = getFieldInt(0, 0)
        val v = getFieldInt(1, 0)
        val w = clampSize(getFieldInt(2, 1))
        val h = clampSize(getFieldInt(3, 1))
        val materialId = getMaterialId()
        val absU = relToAbs(u, w)
        val absV = relToAbs(v, h)
        return UvSnapshot(absU, absV, w, h, sourceFace, materialId)
    }

    private fun getMaterialId(): Identifier? {
        val stack = menu.materialSlot.item
        val item = stack.item as? BlockItem ?: return null
        return BuiltInRegistries.BLOCK.getKey(item.block)
    }

    private fun getMaterialTileSize(): Float {
        val stack = menu.materialSlot.item
        val blockItem = stack.item as? BlockItem
        val fromSlot = blockItem?.block?.defaultBlockState()

        val level = minecraft?.level ?: return TILE_SIZE.toFloat()
        val be = level.getBlockEntity(menu.pos)
        val face = Direction.entries.getOrNull(menu.faceOrdinal) ?: Direction.NORTH
        val state = fromSlot ?: when (be) {
            is CopycatByteBlockEntity -> be.getFaceState(menu.partIndex, face)
            is CopycatBiteBlockEntity -> be.getFaceState(menu.partIndex, face)
            else -> null
        } ?: return TILE_SIZE.toFloat()

        val sprite = Minecraft.getInstance().blockRenderer.blockModelShaper.getParticleIcon(state)
        val w = sprite.contents().width().toFloat()
        val h = sprite.contents().height().toFloat()
        val size = maxOf(w, h)
        return if (size <= 0f) TILE_SIZE.toFloat() else size
    }

    private fun getMaterialSprite(): TextureAtlasSprite? {
        val stack = menu.materialSlot.item
        val blockItem = stack.item as? BlockItem
        val fromSlot = blockItem?.block?.defaultBlockState()

        val level = minecraft?.level ?: return null
        val be = level.getBlockEntity(menu.pos)
        val face = Direction.entries.getOrNull(menu.faceOrdinal) ?: Direction.NORTH
        val state = fromSlot ?: when (be) {
            is CopycatByteBlockEntity -> be.getFaceState(menu.partIndex, face)
            is CopycatBiteBlockEntity -> be.getFaceState(menu.partIndex, face)
            else -> null
        } ?: return null

        return Minecraft.getInstance().blockRenderer.blockModelShaper.getParticleIcon(state)
    }

    private fun loadInitialUv(face: Direction): InitUv {
        val level = minecraft?.level
        val be = level?.getBlockEntity(menu.pos)
        val partSize = getDefaultPartSize()

        val (uAbs, vAbs, w, h, src) = when (be) {
            is CopycatByteBlockEntity -> InitUv(
                be.getUvU(menu.partIndex, face),
                be.getUvV(menu.partIndex, face),
                be.getUvW(menu.partIndex, face),
                be.getUvH(menu.partIndex, face),
                be.getSourceFace(menu.partIndex, face)
            )
            is CopycatBiteBlockEntity -> InitUv(
                be.getUvU(menu.partIndex, face),
                be.getUvV(menu.partIndex, face),
                be.getUvW(menu.partIndex, face),
                be.getUvH(menu.partIndex, face),
                be.getSourceFace(menu.partIndex, face)
            )
            else -> InitUv(0, 0, partSize, partSize, face)
        }

        val wu = if (w <= 0) partSize else w
        val hv = if (h <= 0) partSize else h
        val relU = absToRel(uAbs, wu)
        val relV = absToRel(vAbs, hv)
        return InitUv(relU, relV, wu, hv, src)
    }

    private data class InitUv(
        val u: Int,
        val v: Int,
        val w: Int,
        val h: Int,
        val source: Direction
    )

    private fun getDefaultPartSize(): Int {
        val level = minecraft?.level
        val be = level?.getBlockEntity(menu.pos)
        return when (be) {
            is CopycatByteBlockEntity -> 8
            is CopycatBiteBlockEntity -> 4
            else -> 8
        }
    }

    private fun relToAbs(rel: Int, size: Int): Int {
        val base = (16 - size) / 2
        return base + rel
    }

    private fun absToRel(abs: Int, size: Int): Int {
        val base = (16 - size) / 2
        return abs - base
    }

    private fun clampSize(value: Int): Int {
        return value.coerceAtLeast(1)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val rect = getEditorRect()
        val ex = rect[0]
        val ey = rect[1]
        val ew = rect[2]
        val eh = rect[3]
        if (mouseX >= ex && mouseY >= ey && mouseX < ex + ew && mouseY < ey + eh) {
            val step = if (vertical > 0) 1.1f else 0.9f
            zoomTarget = (zoomTarget * step).coerceIn(0.5f, 4f)
            return true
        }
        if (showBigEditor) {
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    private class NineSliceButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        message: Component,
        onPress: OnPress
    ) : net.minecraft.client.gui.components.Button(x, y, width, height, message, onPress, DEFAULT_NARRATION) {

        var selected: Boolean = false

        override fun renderContents(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val v = when {
                selected -> BTN_V_SELECTED
                isHoveredOrFocused && Minecraft.getInstance().mouseHandler.isLeftPressed -> BTN_V_PRESSED
                isHoveredOrFocused -> BTN_V_FOCUS
                else -> BTN_V_NORMAL
            }
            blitNine(gui, x, y, width, height, BTN_U, v)

            val scale = 0.65f
            val font = Minecraft.getInstance().font
            val textWidth = font.width(message)
            val textX = x + (width - textWidth * scale) / 2f
            val textY = y + (height - font.lineHeight * scale) / 2f
            gui.pose().pushMatrix()
            gui.pose().scale(scale, scale)
            gui.pose().translate(textX / scale, textY / scale)
            gui.drawString(font, message, 0, 0, TEXT_COLOR, false)
            gui.pose().popMatrix()
        }

        private fun blitNine(gui: GuiGraphics, x: Int, y: Int, w: Int, h: Int, u: Int, v: Int) {
            val corner = SLICE_CORNER
            val midTexW = SLICE_MID_W
            val midTexH = SLICE_MID_H
            val gap = SLICE_GAP

            val midW = (w - corner * 2).coerceAtLeast(0)
            val midH = (h - corner * 2).coerceAtLeast(0)

            val uLeft = u
            val uMid = u + corner + gap
            val uRight = u + corner + gap + midTexW + gap
            val vTop = v
            val vMid = v + corner + gap
            val vBottom = v + corner + gap + midTexH + gap

            // corners
            blitUi(gui, x, y, uLeft, vTop, corner, corner)
            blitUi(gui, x + w - corner, y, uRight, vTop, corner, corner)
            blitUi(gui, x, y + h - corner, uLeft, vBottom, corner, corner)
            blitUi(gui, x + w - corner, y + h - corner, uRight, vBottom, corner, corner)

            // edges
            if (midW > 0) {
                blitRepeat(gui, x + corner, y, midW, corner, uMid, vTop, midTexW, corner)
                blitRepeat(gui, x + corner, y + h - corner, midW, corner, uMid, vBottom, midTexW, corner)
            }
            if (midH > 0) {
                blitRepeat(gui, x, y + corner, corner, midH, uLeft, vMid, corner, midTexH)
                blitRepeat(gui, x + w - corner, y + corner, corner, midH, uRight, vMid, corner, midTexH)
            }

            // center
            if (midW > 0 && midH > 0) {
                blitRepeat(gui, x + corner, y + corner, midW, midH, uMid, vMid, midTexW, midTexH)
            }
        }

        private fun blitUi(gui: GuiGraphics, x: Int, y: Int, u: Int, v: Int, w: Int, h: Int) {
            gui.blit(RenderPipelines.GUI_TEXTURED, UI_TEX, x, y, u.toFloat(), v.toFloat(), w, h, TEX_W, TEX_H)
        }

        private fun blitRepeat(gui: GuiGraphics, x: Int, y: Int, w: Int, h: Int, u: Int, v: Int, sw: Int, sh: Int) {
            var dx = 0
            while (dx < w) {
                val drawW = (w - dx).coerceAtMost(sw)
                var dy = 0
                while (dy < h) {
                    val drawH = (h - dy).coerceAtMost(sh)
                    blitUi(gui, x + dx, y + dy, u, v, drawW, drawH)
                    dy += drawH
                }
                dx += drawW
            }
        }
    }

}
