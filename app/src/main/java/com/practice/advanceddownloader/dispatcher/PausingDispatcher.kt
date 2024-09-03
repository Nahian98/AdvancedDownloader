package com.practice.advanceddownloader.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

class PausingDispatcher(
    private val queue: PausingDispatchQueue,
    private val baseDispatcher: CoroutineDispatcher,
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (queue.isPaused) {
            queue.queue(context, block, baseDispatcher)
        } else {
            baseDispatcher.dispatch(context, block)
        }
    }
}