package fleet.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import noria.NoriaContext
import noria.cell

abstract class ComposeCell<out T> {
  internal abstract val state: State<T>
}

@PublishedApi
internal class ComposeCellImpl<T> : ComposeCell<T>() {
  @PublishedApi
  internal val mutableState = mutableStateOf<T?>(null)

  @Suppress("UNCHECKED_CAST")
  override val state: State<T>
    get() = mutableState as State<T>
}

/**
 * Provides a way to cache a result of [calculation] and recalculate it only when dependencies of [calculation] changed.
 *
 * Returned [ComposeCell] should be consumed by [CellConsumer]
 *
 * Note: this is the Compose compatible replacement to [NoriaContext.cell]
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun <T> NoriaContext.rememberCell(noinline calculation: @Composable NoriaContext.() -> T): ComposeCell<T> {
  val cell = remember { ComposeCellImpl<T>() }
  CellUpdater(cell, calculation)
  return cell
}

@Composable
@PublishedApi
internal fun <T> NoriaContext.CellUpdater(cell: ComposeCellImpl<T>, calculation: @Composable NoriaContext.() -> T) {
  cell.mutableState.value = calculation()
}

@Composable
fun <T> NoriaContext.CellConsumer(cell: ComposeCell<T>, content: @Composable NoriaContext.(State<T>) -> Unit) {
  content(cell.state)
}

@Composable
fun <T1, T2> NoriaContext.CellConsumer(cell1: ComposeCell<T1>, cell2: ComposeCell<T2>, content: @Composable NoriaContext.(State<T1>, State<T2>) -> Unit) {
  content(cell1.state, cell2.state)
}

@Composable
fun <T1, T2, T3> NoriaContext.CellConsumer(cell1: ComposeCell<T1>, cell2: ComposeCell<T2>, cell3: ComposeCell<T3>, content: @Composable NoriaContext.(State<T1>, State<T2>, State<T3>) -> Unit) {
  content(cell1.state, cell2.state, cell3.state)
}

@Composable
fun <T1, T2, T3, T4> NoriaContext.CellConsumer(cell1: ComposeCell<T1>, cell2: ComposeCell<T2>, cell3: ComposeCell<T3>, cell4: ComposeCell<T4>, content: @Composable NoriaContext.(State<T1>, State<T2>, State<T3>, State<T4>) -> Unit) {
  content(cell1.state, cell2.state, cell3.state, cell4.state)
}

@Composable
fun <T1, T2, T3, T4, T5> NoriaContext.CellConsumer(cell1: ComposeCell<T1>, cell2: ComposeCell<T2>, cell3: ComposeCell<T3>, cell4: ComposeCell<T4>, cell5: ComposeCell<T5>, content: @Composable NoriaContext.(State<T1>, State<T2>, State<T3>, State<T4>, State<T5>) -> Unit) {
  content(cell1.state, cell2.state, cell3.state, cell4.state, cell5.state)
}