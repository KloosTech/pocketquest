package io.github.jackbeback.vm

import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.ViewModel
import io.github.jackbeback.data.Action
import io.github.jackbeback.data.Position
import io.github.jackbeback.data.Skill
import io.github.jackbeback.data.TargetedSkill
import io.github.jackbeback.data.magicMissile
import io.github.jackbeback.util.DiceType
import io.github.jackbeback.util.roll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.Random.Default.nextDouble

class SkillViewModel : ViewModel() {
    companion object {
        val instance = SkillViewModel()
    }

    private val _skillQueue = MutableStateFlow<List<TargetedSkill>>(listOf())
    val skillQueue = _skillQueue.asStateFlow()

    fun addSkill(new: TargetedSkill) {
        if (new.skill.name == "Magic Missiles") {
            val missiles = mutableListOf<TargetedSkill>()
            repeat(roll(DiceType.D4, 2)) {
                val success = Random.nextBoolean()
                if (success) missiles.add(new.copy(skill = magicMissile)) else missiles.add(
                    //create a missile that does nothing on hit
                    new.copy(
                        end = new.end.randomMiss(),
                        skill = magicMissile.copy(action = Action())
                    )
                )

            }
            _skillQueue.update {
                it.toMutableList().apply { addAll(missiles) }
            }
        } else {
            _skillQueue.update {
                it + new
            }
        }

    }

    fun removeSkill() {
        _skillQueue.update {
            it.drop(1)
        }
    }
}

fun Random.position(): Position {
    val x = Random.nextDouble()
    val y = Random.nextDouble()
    return Position(x, y)
}

fun Position.randomMiss(maxDelta: Double = 0.1): Position {
    val angle = Random.nextDouble() * 2 * PI
    val newX = x + maxDelta * cos(angle)
    val newY = y + maxDelta * sin(angle)
    return Position(
        newX.coerceIn(0.0, 1.0),
        newY.coerceIn(0.0, 1.0)
    )
}
