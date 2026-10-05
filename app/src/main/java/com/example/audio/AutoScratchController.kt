package com.example.audio

import com.example.model.DeckId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Controller for executing automated rhythmically-synchronized DJ scratch routines.
 */
class AutoScratchController(
    private val engine: DjAudioEngine,
    private val scope: CoroutineScope
) {
    private var activeJob: Job? = null

    val scratchPatterns = listOf(
        "Baby Scratch",
        "Chirp Scratch",
        "Transformer",
        "Flare Scratch",
        "Orbit Scratch",
        "Scribble",
        "Crab Scratch"
    )

    fun startPattern(deckId: DeckId, pattern: String, bpm: Double) {
        stopPattern(deckId)
        val beatMs = ((60.0 / bpm) * 1000.0).toLong()

        activeJob = scope.launch(Dispatchers.Default) {
            engine.onPlatterTouch(deckId, true)
            try {
                when (pattern) {
                    "Baby Scratch" -> runBabyScratch(deckId, beatMs)
                    "Chirp Scratch" -> runChirpScratch(deckId, beatMs)
                    "Transformer" -> runTransformerScratch(deckId, beatMs)
                    "Flare Scratch" -> runFlareScratch(deckId, beatMs)
                    "Orbit Scratch" -> runOrbitScratch(deckId, beatMs)
                    "Scribble" -> runScribbleScratch(deckId, beatMs)
                    "Crab Scratch" -> runCrabScratch(deckId, beatMs)
                    else -> runBabyScratch(deckId, beatMs)
                }
            } finally {
                engine.onPlatterTouch(deckId, false)
            }
        }
    }

    fun stopPattern(deckId: DeckId) {
        activeJob?.cancel()
        activeJob = null
        engine.onPlatterTouch(deckId, false)
    }

    private suspend fun runBabyScratch(deckId: DeckId, beatMs: Long) {
        val halfBeat = beatMs / 2
        for (i in 0 until 4) {
            // Forward push
            engine.onPlatterRotate(deckId, 35f)
            delay(halfBeat / 2)
            // Backward pull
            engine.onPlatterRotate(deckId, -35f)
            delay(halfBeat / 2)
        }
    }

    private suspend fun runChirpScratch(deckId: DeckId, beatMs: Long) {
        val step = beatMs / 4
        for (i in 0 until 6) {
            engine.onPlatterRotate(deckId, 45f)
            delay(step / 2)
            engine.onPlatterRotate(deckId, -45f)
            delay(step / 2)
        }
    }

    private suspend fun runTransformerScratch(deckId: DeckId, beatMs: Long) {
        val eighth = beatMs / 8
        for (i in 0 until 8) {
            engine.onPlatterRotate(deckId, 18f)
            delay(eighth)
        }
    }

    private suspend fun runFlareScratch(deckId: DeckId, beatMs: Long) {
        val step = beatMs / 6
        for (i in 0 until 4) {
            engine.onPlatterRotate(deckId, 25f)
            delay(step)
            engine.onPlatterRotate(deckId, 25f)
            delay(step)
            engine.onPlatterRotate(deckId, -50f)
            delay(step)
        }
    }

    private suspend fun runOrbitScratch(deckId: DeckId, beatMs: Long) {
        val step = beatMs / 4
        for (i in 0 until 4) {
            engine.onPlatterRotate(deckId, 40f)
            delay(step)
            engine.onPlatterRotate(deckId, -40f)
            delay(step)
        }
    }

    private suspend fun runScribbleScratch(deckId: DeckId, beatMs: Long) {
        val fastStep = 20L
        for (i in 0 until 20) {
            engine.onPlatterRotate(deckId, 12f)
            delay(fastStep)
            engine.onPlatterRotate(deckId, -12f)
            delay(fastStep)
        }
    }

    private suspend fun runCrabScratch(deckId: DeckId, beatMs: Long) {
        val tapStep = 18L
        for (i in 0 until 4) {
            for (finger in 0 until 4) {
                engine.onPlatterRotate(deckId, 8f)
                delay(tapStep)
            }
            engine.onPlatterRotate(deckId, -32f)
            delay(60L)
        }
    }
}
