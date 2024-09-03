package com.practice.advanceddownloader.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class PausingDispatchQueue : AbstractCoroutineContextElement(Key) {

    private val paused = AtomicBoolean(false)
    private val queue = ArrayDeque<Resumer>()

    val isPaused: Boolean
        get() = paused.get()

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        if (paused.compareAndSet(true, false)) {
            dispatchNext()
        }
    }

    fun queue(context: CoroutineContext, block: Runnable, dispatcher: CoroutineDispatcher) {
        queue.addLast(Resumer(dispatcher, context, block))
    }

    private fun dispatchNext() {
        val resumer = queue.removeFirstOrNull() ?: return
        resumer.dispatch()
    }

    private inner class Resumer(
        private val dispatcher: CoroutineDispatcher,
        private val context: CoroutineContext,
        private val block: Runnable,
    ) : Runnable {
        override fun run() {
            block.run()
            if (!paused.get()) {
                dispatchNext()
            }
        }

        fun dispatch() {
            dispatcher.dispatch(context, this)
        }
    }

    companion object Key : CoroutineContext.Key<PausingDispatchQueue>
}