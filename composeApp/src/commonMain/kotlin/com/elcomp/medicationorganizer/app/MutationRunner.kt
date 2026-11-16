package com.elcomp.medicationorganizer.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/** Signed-in UI owns mutations; leaving a form only cancels its wait, not a dispatched write. */
internal class MutationRunner(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set

    suspend fun run(block: suspend () -> Unit) {
        if (busy) throw ApiException(ApiError.CONFLICT)
        busy = true
        scope.async {
            try {
                block()
                Result.success(Unit)
            } catch (error: ApiException) {
                Result.failure<Unit>(error)
            } finally {
                busy = false
            }
        }.await().getOrThrow()
    }
}
