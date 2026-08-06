package io.github.jackbeback.data

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.*
import io.github.jackbeback.ui.*
import io.github.jackbeback.util.*
import org.jetbrains.compose.resources.*
import pocketquest.composeapp.generated.resources.*
import pocketquest.composeapp.generated.resources.Res
import kotlin.math.*


data class TargetedSkill(val skill: Skill, val start: Position, val end: Position, val projectile: @Composable () -> Unit)

data class Skill(
    val name: String,
    val description: String,
    val needTarget: Boolean,
    val needHitRoll: Boolean,
    val theme: SkillTheme,
    val action: Action,
    val skillIcon: @Composable () -> Unit
)

data class Action(val onSource: (UnitEntity) -> UnitEntity = { it }, val onTarget: (UnitEntity) -> UnitEntity = { it })


fun Position.angleTo(other: Position): Double {
    val dx = other.x - x
    val dy = other.y - y

    // Berechne den Arkustangens des Verhältnisses dy/dx
    // atan2 berücksichtigt die Quadranten korrekt
    val radians = atan2(dy, dx)
    println("start: $this, end: $other, angle: $radians, degrees: ${radians.toDegrees()}")
    // Konvertiere das Ergebnis von Radiant zu Grad
    return radians.toDegrees() + 90.0
}


fun createTargetSkill(start: Position, end: Position, skill: Skill): TargetedSkill {
    return TargetedSkill(skill, start, end) {
        val angle = start.angleTo(end)
        println("Start: ($start), End: ($end), Angle: $angle")

        val sprite = when (skill.theme) {
            SkillTheme.ARCANE -> painterResource(Res.drawable.missile)
            SkillTheme.NATURE -> painterResource(Res.drawable.missile)
            SkillTheme.FIRE -> painterResource(Res.drawable.projectile_fire)
            SkillTheme.ICE -> painterResource(Res.drawable.missile)
        }

        val size = when (skill.theme) {
            SkillTheme.ARCANE -> 25.dp
            SkillTheme.NATURE -> null
            SkillTheme.FIRE -> 50.dp
            SkillTheme.ICE -> null
        }

        Box(
            modifier = Modifier
                .size(size ?: 45.dp)
                .rotate(angle.toFloat())
        ){
            Image(sprite, null, modifier = Modifier.size(45.dp))
        }
    }
}

val thornWhip = Skill(
    "Thorn Whip", "Take 2 Damage. Deal 2 D4 to target", true, true, SkillTheme.NATURE,
    Action(
        onSource = { source -> source.takeDamage(Damage(DamageType.PIERCING, 2)) },
        onTarget = { target -> target.takeDamage(Damage(DamageType.PIERCING,roll(DiceType.D4, 2))) }
    )) {
    Image(painterResource(Res.drawable.ThornWhip), null)
}

val basicHeal = Skill(
    "basic Heal", "Heal for 2", false, false, SkillTheme.ARCANE,
    Action(
        onSource = { source -> source.heal(2) },
        onTarget = { target -> target }
    )) {
    Image(painterResource(Res.drawable.BasicHeal), null)
}

val magicMissiles = Skill(
    "Magic Missiles",
    "Shoots 2 x d4 Missiles. Missiles have 50% chance to miss",
    true,
    false,
    SkillTheme.ARCANE,
    Action(
        onSource = { source -> source },
        onTarget = { target -> target }
    )) {
    Image(painterResource(Res.drawable.MagicMissile), null)
}

val magicMissile = Skill(
    "Magic Missile",
    "Shoots a single Missile, which has a 50% Chance to hit a different tile",
    true,
    false,
    SkillTheme.ARCANE,
    Action(
        onSource = { source -> source },
        onTarget = { target -> target.takeDamage(Damage(DamageType.PIERCING, 2)) },
    )) {
    Image(painterResource(Res.drawable.MagicMissile), null)
}

val fireBall = Skill(
    "Fire Ball",
    "Fire Ball",
    true,
    false,
    SkillTheme.FIRE,
    Action(
        onSource = {source -> source},
        onTarget = { target -> target.takeDamage(Damage(DamageType.FIRE, 4)) },
    )){
    Image(painterResource(Res.drawable.skill_ignite), null)
}

val ignite = Skill(
    "Ignite",
    "Ignites Taget dealing 1d4 fire damage and Burning the Target for 1d6",
    true,
    false,
    SkillTheme.FIRE,
    Action(
        onSource = { source -> source },
        onTarget = { target -> target.addCondition(ConditionType.Burn, roll(DiceType.D6))}
    )) {
    Image(painterResource(Res.drawable.skill_ignite), null)
}


