package com.elcomp.medicationorganizer.core.ui

import androidx.compose.runtime.*
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.stringResource

private sealed interface LoadResult<out T> {
    data object Loading : LoadResult<Nothing>
    data class Failed(val reason: ApiError) : LoadResult<Nothing>
    data class Ready<T>(val value: T) : LoadResult<T>
}

@Composable
internal fun <T> LoadContent(vararg keys: Any?, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState<LoadResult<T>>(LoadResult.Loading, *arrayOf<Any?>(*keys, retry)) {
        value = LoadResult.Loading
        value = try { LoadResult.Ready(load()) } catch (error: ApiException) { LoadResult.Failed(error.error) }
    }
    when (val current = state) {
        LoadResult.Loading -> AppLoadingState()
        is LoadResult.Failed -> AppErrorState(
            message = stringResource(if (current.reason == ApiError.NOT_FOUND) Res.string.full_unavailable else Res.string.full_load_failed),
            onRetry = { retry++ },
        )
        is LoadResult.Ready -> content(current.value)
    }
}
