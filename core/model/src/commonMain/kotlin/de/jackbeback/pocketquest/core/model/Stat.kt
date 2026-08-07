package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/** What a [Modifier] targets. */
@Serializable
enum class Stat { MaxHp, ArmorClass, SpeedTiles, MaxAp, MaxMana, Str, Dex, Con, Int, Wis, Cha }

@Serializable
enum class Ability { Str, Dex, Con, Int, Wis, Cha }

/** doc10/doc18: `Taunted` narrows `:core:ai`'s target selection — see `Entity.tauntedBy(cat)` in `:core:rules`, which reads WHO taunted an entity by walking its statuses directly (this generic flag set alone can't answer "by whom"). */
@Serializable
enum class Flag { CantAct, Prone, Flying, Invisible, Taunted }

@Serializable
enum class DamageType { Bludgeoning, Piercing, Slashing, Fire, Cold, Lightning, Poison, Force }

@Serializable
enum class Resistance { None, Resistant, Immune, Vulnerable }
