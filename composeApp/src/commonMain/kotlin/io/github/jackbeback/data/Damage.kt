package io.github.jackbeback.data

data class Damage(val type: DamageType, val amount: Int)

enum class DamageType {
    FIRE,
    WATER,
    ELECTRIC,
    FORCE,
    ICE,
    POISON,
    PIERCING,
    SLICING,
    BLUDGEONING
}
