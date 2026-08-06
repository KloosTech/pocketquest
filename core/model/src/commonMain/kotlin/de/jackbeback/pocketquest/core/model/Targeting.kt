package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TargetMode { SelfOnly, SingleEntity, Point, Direction, Path }

@Serializable
sealed interface Range {
    @Serializable @SerialName("melee") data object Melee : Range
    @Serializable @SerialName("tiles") data class Tiles(val n: Int) : Range
    @Serializable @SerialName("selfRange") data object SelfRange : Range
}

@Serializable
sealed interface Shape {
    @Serializable @SerialName("single") data object Single : Shape
    @Serializable @SerialName("sphere") data class Sphere(val radius: Int) : Shape
    @Serializable @SerialName("cone") data class Cone(val length: Int, val degrees: Int) : Shape
    @Serializable @SerialName("line") data class Line(val length: Int) : Shape
    @Serializable @SerialName("rect") data class Rect(val width: Int, val height: Int) : Shape
}

/** All non-null fields must match (AND) — e.g. faction=Enemy + requireAlive=true is "living enemies". */
@Serializable
data class TargetFilter(
    val faction: Faction? = null,
    val requireAlive: Boolean = true,
    val hasStatus: StatusId? = null,
    val excludeSelf: Boolean = false,
)

@Serializable
data class Targeting(
    val mode: TargetMode,
    val range: Range,
    val shape: Shape,
    val filter: TargetFilter = TargetFilter(),
    val requiresLoS: Boolean = true,
    val maxTargets: Int = 1,
)
