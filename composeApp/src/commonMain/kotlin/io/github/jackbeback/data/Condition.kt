package io.github.jackbeback.data

import io.github.jackbeback.vm.LogViewModel


enum class ConditionType {
    Burn,
    Poison,
    Cold,
}


fun getConditionEffect(type: ConditionType): (UnitEntity, Int) -> UnitEntity {
    return conditionEffects[type] ?: { unit, amount -> unit }
}

val conditionEffects: Map<ConditionType, (UnitEntity, Int) -> UnitEntity> = mapOf(
    ConditionType.Burn to { unit, amount ->
        LogViewModel.current.conditionDamage(unit, ConditionType.Burn, amount)
        unit.takeDamage(Damage(DamageType.FIRE, amount)) },
    ConditionType.Poison to { unit, amount ->
        LogViewModel.current.conditionDamage(unit, ConditionType.Poison, amount)
        unit.takeDamage(Damage(DamageType.POISON, amount)) },
    ConditionType.Cold to { unit, amount ->
        LogViewModel.current.conditionDamage(unit, ConditionType.Cold, amount)
        unit.takeDamage(Damage(DamageType.ICE, amount)) },
)


