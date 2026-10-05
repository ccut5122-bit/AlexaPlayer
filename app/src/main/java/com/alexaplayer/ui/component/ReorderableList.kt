package com.alexaplayer.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/**
 * Long press to pick a row up, drag to move it, release to drop it in place.
 *
 * The list is updated on every swap rather than on release, so the rows part around the
 * finger instead of jumping at the end.
 */
class ReorderState(
    val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggedIndex: Int? by mutableStateOf(null)
        private set

    internal var dragOffsetY by mutableFloatStateOf(0f)
        private set

    fun onDragStart(offsetY: Float) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            offsetY >= info.offset && offsetY < info.offset + info.size
        } ?: return
        draggedIndex = item.index
        dragOffsetY = 0f
    }

    fun onDrag(deltaY: Float) {
        val current = draggedIndex ?: return
        val info = listState.layoutInfo.visibleItemsInfo
        val currentItem = info.firstOrNull { it.index == current } ?: return
        dragOffsetY += deltaY
        val center = currentItem.offset + (currentItem.size / 2f) + dragOffsetY
        val target = info.firstOrNull { candidate ->
            candidate.index != current && center.toInt() in candidate.offset..(candidate.offset + candidate.size)
        } ?: return
        if (target.index != current) {
            onMove(current, target.index)
            dragOffsetY += (currentItem.offset - target.offset).toFloat()
            draggedIndex = target.index
        }
    }

    fun onDragEnd() {
        draggedIndex = null
        dragOffsetY = 0f
    }

    fun offsetFor(index: Int): Int = if (index == draggedIndex) dragOffsetY.roundToInt() else 0
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderState = remember(listState) { ReorderState(listState, onMove) }

/** Attach to the list that contains the reorderable rows. */
fun Modifier.reorderable(state: ReorderState): Modifier = pointerInput(state.listState) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset.y) },
        onDrag = { change, amount ->
            change.consume()
            state.onDrag(amount.y)
        },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragEnd() },
    )
}
