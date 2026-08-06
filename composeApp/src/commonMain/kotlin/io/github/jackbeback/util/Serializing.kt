package io.github.jackbeback.util

import io.github.jackbeback.data.Enemy
import io.github.jackbeback.data.Player
import io.github.jackbeback.data.UnitEntity
import io.github.jackbeback.ui.Grunt
import io.github.jackbeback.ui.Wizard
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.encodeToString

// Module (wie zuvor beschrieben):
val unitEntityModule = SerializersModule {
    polymorphic(UnitEntity::class) {
        subclass(Player::class, Player.serializer())
        subclass(Enemy::class, Enemy.serializer())
    }
}

val json = Json {
    serializersModule = unitEntityModule
    classDiscriminator = "kind"
    prettyPrint = true
}

expect fun save(json: String)
expect fun load(): String?



