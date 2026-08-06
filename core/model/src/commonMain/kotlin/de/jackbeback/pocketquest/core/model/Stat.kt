package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/** What a [Modifier] targets. */
@Serializable
enum class Stat { MaxHp, ArmorClass, SpeedTiles, MaxAp, MaxMana, Str, Dex, Con, Int, Wis, Cha }

@Serializable
enum class Ability { Str, Dex, Con, Int, Wis, Cha }

@Serializable
enum class Flag { CantAct, Prone, Flying, Invisible }

@Serializable
enum class DamageType { Bludgeoning, Piercing, Slashing, Fire, Cold, Lightning, Poison, Force }

@Serializable
enum class Resistance { None, Resistant, Immune, Vulnerable }
