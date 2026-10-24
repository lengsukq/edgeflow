package com.edgeflow.service

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/** Owns TextToSpeech in the app process; all engine operations stay off receiver and input threads. */
internal class SpeechController(context: Context) {
    private val appContext = context.applicationContext
    private val worker = HandlerThread("EdgeFlow-TTS").apply { start() }
    private val handler = Handler(worker.looper)
    private val utteranceIds = AtomicLong()
    private val pendingTexts = mutableListOf<String>()

    // These fields are only accessed on the worker thread.
    private var engine: TextToSpeech? = null
    private var initialized = false
    @Volatile private var shuttingDown = false

    private val idleShutdown = Runnable { releaseEngine() }

    fun speak(text: String) {
        val request = SpeechRequest.parse(com.edgeflow.core.Ipc.PERFORM_ACTION, text) ?: return
        if (shuttingDown) return
        handler.post {
            if (shuttingDown) return@post
            handler.removeCallbacks(idleShutdown)
            if (initialized) {
                speakNow(request)
            } else {
                if (pendingTexts.size == MAX_PENDING_UTTERANCES) {
                    pendingTexts.removeAt(0)
                    Log.w(TAG, "discarded oldest pending speech request")
                }
                pendingTexts += request
                initializeEngine()
            }
        }
    }

    /** Called by Application.onTerminate where the runtime provides that callback. */
    fun shutdown() {
        if (shuttingDown) return
        shuttingDown = true
        if (!handler.post {
                pendingTexts.clear()
                releaseEngine()
                handler.removeCallbacksAndMessages(null)
                worker.quitSafely()
            }
        ) {
            worker.quitSafely()
        }
    }

    private fun initializeEngine() {
        if (engine != null) return
        try {
            engine = TextToSpeech(appContext) { status ->
                handler.post { onEngineInitialized(status) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "could not initialize text-to-speech", t)
            pendingTexts.clear()
            releaseEngine()
        }
    }

    private fun onEngineInitialized(status: Int) {
        if (engine == null) return
        if (shuttingDown) {
            releaseEngine()
            return
        }
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "text-to-speech initialization failed with status $status")
            pendingTexts.clear()
            releaseEngine()
            return
        }

        initialized = true
        pendingTexts.toList().forEach(::speakNow)
        pendingTexts.clear()
        handler.removeCallbacks(idleShutdown)
        handler.postDelayed(idleShutdown, ENGINE_IDLE_TIMEOUT_MS)
    }

    private fun speakNow(text: String) {
        val currentEngine = engine ?: return
        try {
            val result = currentEngine.speak(
                text,
                TextToSpeech.QUEUE_ADD,
                null,
                "edgeflow-${utteranceIds.incrementAndGet()}",
            )
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "text-to-speech rejected an utterance")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "text-to-speech utterance failed", t)
        }
        handler.removeCallbacks(idleShutdown)
        handler.postDelayed(idleShutdown, ENGINE_IDLE_TIMEOUT_MS)
    }

    private fun releaseEngine() {
        handler.removeCallbacks(idleShutdown)
        val currentEngine = engine
        engine = null
        initialized = false
        if (currentEngine != null) {
            runCatching { currentEngine.shutdown() }
                .onFailure { Log.w(TAG, "text-to-speech shutdown failed", it) }
        }
    }

    private companion object {
        const val TAG = "EdgeFlowSpeech"
        const val MAX_PENDING_UTTERANCES = 8
        const val ENGINE_IDLE_TIMEOUT_MS = 60_000L
    }
}
