package com.example.automix

import com.example.audio.DjAudioEngine
import com.example.model.AutomixTransition
import com.example.model.DeckId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Automix controller for executing automated beat-matched DJ transitions between decks.
 */
class AutomixController(
    private val engine: DjAudioEngine,
    private val scope: CoroutineScope
) {
    private var automixJob: Job? = null

    private val _isAutomixRunning = MutableStateFlow(false)
    val isAutomixRunning = _isAutomixRunning.asStateFlow()

    private val _currentTransition = MutableStateFlow(AutomixTransition.FILTER_FADE)
    val currentTransition = _currentTransition.asStateFlow()

    fun setTransition(transition: AutomixTransition) {
        _currentTransition.value = transition
    }

    fun startAutomix(transitionBeats: Int = 16) {
        if (_isAutomixRunning.value) return
        _isAutomixRunning.value = true

        automixJob = scope.launch(Dispatchers.Default) {
            val stateA = engine.deckAState.value
            val stateB = engine.deckBState.value

            // Determine incoming and outgoing decks based on which is playing
            val outgoingDeckId = if (stateA.isPlaying && !stateB.isPlaying) {
                DeckId.DECK_A
            } else if (stateB.isPlaying && !stateA.isPlaying) {
                DeckId.DECK_B
            } else {
                DeckId.DECK_A
            }

            val incomingDeckId = if (outgoingDeckId == DeckId.DECK_A) DeckId.DECK_B else DeckId.DECK_A

            // Ensure incoming deck starts playing and syncs tempo
            engine.syncDecks(outgoingDeckId, incomingDeckId)
            engine.togglePlay(incomingDeckId)

            val bpm = if (outgoingDeckId == DeckId.DECK_A) stateA.effectiveBpm else stateB.effectiveBpm
            val beatMs = (60.0 / bpm * 1000.0)
            val totalTransitionMs = (transitionBeats * beatMs).toLong()
            val startTime = System.currentTimeMillis()

            val startPos = if (outgoingDeckId == DeckId.DECK_A) -1f else 1f
            val targetPos = if (outgoingDeckId == DeckId.DECK_A) 1f else -1f

            val transitionType = _currentTransition.value

            while (System.currentTimeMillis() - startTime < totalTransitionMs && _isAutomixRunning.value) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / totalTransitionMs.toFloat()).coerceIn(0f, 1f)

                // Move crossfader smoothly
                val currentXfade = startPos + (targetPos - startPos) * progress
                engine.setCrossfaderPosition(currentXfade)

                // Apply transition-specific acoustic FX
                when (transitionType) {
                    AutomixTransition.FILTER_FADE -> {
                        // Highpass outgoing deck gradually, open up incoming deck
                        val outFilter = progress * 0.7f
                        val inFilter = (1f - progress) * -0.7f
                        engine.setFilterKnob(outgoingDeckId, outFilter)
                        engine.setFilterKnob(incomingDeckId, inFilter)
                    }
                    AutomixTransition.ECHO_DROP -> {
                        if (progress > 0.75f) {
                            engine.setMasterFxType(com.example.model.MasterFxType.ECHO)
                            engine.setFxDryWet((progress - 0.75f) * 4f * 0.7f)
                        }
                    }
                    else -> {}
                }

                delay(30L)
            }

            // Finish transition: stop outgoing deck and reset filters
            if (_isAutomixRunning.value) {
                engine.setCrossfaderPosition(targetPos)
                engine.setFilterKnob(outgoingDeckId, 0f)
                engine.setFilterKnob(incomingDeckId, 0f)
                engine.togglePlay(outgoingDeckId)
            }

            _isAutomixRunning.value = false
        }
    }

    fun stopAutomix() {
        _isAutomixRunning.value = false
        automixJob?.cancel()
        automixJob = null
        engine.setFilterKnob(DeckId.DECK_A, 0f)
        engine.setFilterKnob(DeckId.DECK_B, 0f)
    }
}
