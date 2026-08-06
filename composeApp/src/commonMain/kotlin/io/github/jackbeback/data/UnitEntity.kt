@file:OptIn(ExperimentalUuidApi::class, ExperimentalUuidApi::class)

package io.github.jackbeback.data

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.jackbeback.ui.*
import io.github.jackbeback.util.*
import io.github.jackbeback.vm.LogViewModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.uuid.*

@Serializable
@SerialName("UnitEntity")
sealed interface UnitEntity {
    val name: String
    val type: UnitType
    val pos: Position
    val stats: Stats
    val resources: Resources
    val id: String
    val roll: Roll
    val conditions: Map<ConditionType, Int>
    val resistances: Map<DamageType, Float>

    fun setRoll(new: Roll): UnitEntity {
        return when (this) {
            is Player -> {
                this.copy(roll = new)
            }

            is Enemy -> {
                this.copy(roll = new)
            }

            else -> TODO()
        }
    }

    fun addCondition(type: ConditionType, amount: Int = 1): UnitEntity {
        val newConditions = if (conditions.containsKey(type)) {
            conditions.toMutableMap().apply {
                this[type] = this[type]!! + amount
            }
        } else {
            conditions.toMutableMap().apply {
                this[type] = amount
            }
        }
        LogViewModel.current.addCondition(this, newConditions)
        return when (this) {
            is Player -> {
                this.copy(conditions = newConditions)
            }

            is Enemy -> {
                this.copy(conditions = newConditions)
            }

            else -> TODO()
        }
    }

    fun removeCondition(type: ConditionType, amount: Int = 1): UnitEntity {
        val newConditions = if (conditions.containsKey(type)) {
            conditions.toMutableMap().apply {
                val newAmount = this[type]!! - amount
                if (newAmount <= 0) {
                    this.remove(type)
                } else {
                    this[type] = newAmount
                }
            }
        } else {
            conditions
        }
        LogViewModel.current.addCondition(this, newConditions)
        return when (this) {
            is Player -> {
                this.copy(conditions = newConditions)
            }

            is Enemy -> {
                this.copy(conditions = newConditions)
            }

            else -> TODO()
        }
    }

    fun takeDamage(damage: Damage): UnitEntity {
        val realAmount = ((this.resistances[damage.type] ?: 1f) * damage.amount).roundToInt()
        val newResources = resources.copy(health = resources.health.copy(current = resources.health.current - realAmount))
        LogViewModel.current.addAttack(this, realAmount)
        return when (this) {
            is Player -> {
                this.copy(resources = newResources)
            }

            is Enemy -> {
                this.copy(resources = newResources)
            }

            else -> TODO()
        }
    }

    fun heal(amount: Int): UnitEntity {
        val newResources = resources.copy(health = resources.health.copy(current = resources.health.current + amount))
        LogViewModel.current.addHeal(this, amount)
        return when (this) {
            is Player -> {
                this.copy(resources = newResources)
            }

            is Enemy -> {
                this.copy(resources = newResources)
            }

            else -> TODO()
        }
    }

    fun moveTo(new: Position): UnitEntity {
        return when (this) {
            is Player -> {
                this.copy(pos = new)
            }

            is Enemy -> {
                this.copy(pos = new)
            }

            else -> TODO()
        }
    }

    fun takeAction(): UnitEntity {
        return when (this) {
            is Player -> {
                this.copy(resources = this.resources.takeAction())
            }

            is Enemy -> {
                this.copy(resources = this.resources.takeAction())
            }
        }
    }

    fun resetActions(): UnitEntity {
        return when (this) {
            is Player -> {
                this.copy(resources = this.resources.resetAction())
            }

            is Enemy -> {
                this.copy(resources = this.resources.resetAction())
            }
        }
    }
}

@Serializable
@SerialName("Player")
data class Player(
    override val id: String = Uuid.random().toHexString(),
    override val name: String,
    override val pos: Position = Position(0.5, 0.5),
    override val stats: Stats = Stats(),
    override val resources: Resources = Resources(createResource(12), createResource(12)),
    override val roll: Roll = Roll(),
    override val conditions: Map<ConditionType, Int> = mapOf(),
    override val resistances: Map<DamageType, Float> = mapOf(),
    val experience: Int = 0
) : UnitEntity {

    override val type: UnitType = UnitType.PLAYER
}

fun Player.getLevel(): Int {
    return when (experience) {
        in 0..8 -> 1
        in 8..16 -> 2
        in 16..32 -> 3
        in 32..64 -> 4
        in 64..128 -> 5
        in 128..256 -> 6
        in 256..512 -> 7
        in 512..1024 -> 8
        in 1024..2048 -> 9
        else -> 0
    }
}

@Serializable
@SerialName("Enemy")
data class Enemy(
    override val id: String = Uuid.random().toHexString(),
    override val name: String,
    override val pos: Position = (5 to 2).toPosition(),
    override val stats: Stats = Stats(),
    override val resources: Resources = Resources(),
    override val roll: Roll = Roll(),
    override val conditions: Map<ConditionType, Int> = mapOf(),
    override val resistances: Map<DamageType, Float> = mapOf(),
) : UnitEntity {
    override val type: UnitType = UnitType.ENEMY
}

@Serializable
@SerialName("Position")
data class Position(
    val x: Double,
    val y: Double
)


enum class UnitType {
    DESTRUCTIBLE,
    INDESTRUCTIBLE,
    ENEMY,
    PLAYER,
}

@Serializable
@SerialName("Resources")
data class Resources(
    val health: Resource = Resource(),
    val mana: Resource = Resource(),
    val action: Resource = Resource(),
    val steps: Resource = Resource(),
)

fun Resources.takeAction(amount: Int = 1): Resources {
    return this.copy(action = this.action.take(amount))
}

fun Resources.resetAction(): Resources {
    return this.copy(action = Resource())
}

@Serializable
@SerialName("Resource")
data class Resource(
    val current: Int = 1,
    val max: Int = 1,
    val buff: Int = 0
)

fun Resource.take(amount: Int): Resource {
    return this.copy(current = max(0, this.current - amount))
}

@Serializable
@SerialName("Roll")
data class Roll(val wins: List<Boolean> = listOf())

fun roll(success: Boolean) = Roll(listOf(success))

fun createResource(max: Int) = Resource(max = max, current = max, buff = 0)

@Serializable
@SerialName("Stats")
data class Stats(
    val strength: Int = 1,
    val dexterity: Int = 1,
    val constitution: Int = 1,
    val intelligence: Int = 1,
    val wisdom: Int = 1,
    val charisma: Int = 1,
    val amorClass: Int = 10
)

@Composable
fun UnitComposable(unitEntity: UnitEntity){
    when {
        unitEntity.name.contains("Wizard") -> Wizard(unitEntity)
        unitEntity.name.contains("Grunt") -> Grunt(unitEntity)
        else -> Box(Modifier.size(100.dp).clip(RoundedCornerShape(25.dp)))
    }
}
