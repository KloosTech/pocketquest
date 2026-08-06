package de.jackbeback.pocketquest.core.rules.serialization

import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer

/**
 * JVM-only (desktopTest, not commonTest — kotlin-reflect isn't portably
 * available on iOS/Native): walks every sealed subclass of Effect/
 * GameEvent/Modifier/Expiry automatically via kotlin-reflect and fails if
 * any of them serializes with a fallback fully-qualified-class-name
 * discriminator instead of an explicit @SerialName. This can't be forgotten
 * the way a manually-enumerated list could — a new subtype with no
 * @SerialName fails immediately, without needing its own test entry.
 *
 * Checks the actual runtime discriminator kotlinx.serialization produces,
 * not annotation presence directly — that's the thing docs/06-persistence.md
 * actually cares about ("stable discriminators, not class names").
 */
@OptIn(ExperimentalSerializationApi::class)
class SerialNameEnforcementTest {

    private fun <T : Any> allSealedLeaves(root: KClass<T>): List<KClass<out T>> =
        root.sealedSubclasses.flatMap { sub ->
            if (sub.isSealed) allSealedLeaves(sub) else listOf(sub)
        }

    private fun assertEveryLeafHasAnExplicitSerialName(root: KClass<*>) {
        val leaves = allSealedLeaves(root)
        assertTrue(leaves.isNotEmpty(), "${root.simpleName} has no subclasses — sealedSubclasses reflection may not be working")

        for (leaf in leaves) {
            val descriptor = serializer(leaf.createType()).descriptor
            val serialName = descriptor.serialName
            assertTrue(
                !serialName.contains('.'),
                "${leaf.qualifiedName} has no explicit @SerialName — serializes as fallback '$serialName' instead of a stable short name",
            )
        }
    }

    @Test
    fun everyEffectSubtypeHasAnExplicitSerialName() = assertEveryLeafHasAnExplicitSerialName(Effect::class)

    @Test
    fun everyGameEventSubtypeHasAnExplicitSerialName() = assertEveryLeafHasAnExplicitSerialName(GameEvent::class)

    @Test
    fun everyModifierSubtypeHasAnExplicitSerialName() = assertEveryLeafHasAnExplicitSerialName(Modifier::class)

    @Test
    fun everyExpirySubtypeHasAnExplicitSerialName() = assertEveryLeafHasAnExplicitSerialName(Expiry::class)
}
