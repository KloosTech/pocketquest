package de.jackbeback.pocketquest.core.rules.serialization

import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Decision
import de.jackbeback.pocketquest.core.model.DecisionId
import de.jackbeback.pocketquest.core.model.DecisionRequest
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.LinkId
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.model.Stat
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.resolver.ReactedKey
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Layer 6 from docs/09-test-plan.md. Golden-snapshot / SnapshotMigrations
 * testing is deliberately out of scope (deferred to a future persistence
 * pass, doc06 — neither exists yet, and building migration infra with
 * nothing to migrate from would be pure scaffolding). This is the
 * round-trip half: GameState and — the actual process-death case doc06
 * cares about — a Resolver mid-decision, with a non-empty stack and a
 * pending DecisionRequest.
 */
class SerializationRoundTripTest {

    private val json = Json

    @Test
    fun gameStateRoundTripsThroughJson() {
        val s = scenario {
            map(12, 8)
            seed(99)
            archetype("wizard") {
                hp = 18; ac = 12; ap = 2; mana = 9
                abilities(str = 8, dex = 14, con = 12, int = 16, wis = 10, cha = 13)
                modifier(Modifier.Add(Stat.ArmorClass, 1))
            }
            statusDef("bless") { modifier(Modifier.Resist(DamageType.Fire, Resistance.Resistant)) }
            itemDef("ring") { modifier(Modifier.Add(Stat.MaxMana, 1)) }
            entity("lyra") {
                archetype("wizard"); at(2, 5); hp(15); ap(1); mana(6)
                equip(Slot.Ring1, "ring", enchantment = 1)
            }
            entity("goblin") { archetype("wizard"); at(3, 5); hp(7) }
            initiative("lyra", "goblin")
            status("lyra", "bless", stacks = 2, concentration = true, source = "lyra", expiry = Expiry.EndOfRound(4))
        }
        // Advance rng/version/id counters past their defaults so the round-trip actually exercises them.
        val state = s.state.copy(
            rng = s.state.rng.copy(calls = 7),
            version = 3,
            nextDecisionId = 5,
            nextLinkId = 2,
        )

        val encoded = json.encodeToString(GameState.serializer(), state)
        val decoded = json.decodeFromString(GameState.serializer(), encoded)
        assertEquals(state, decoded)
        assertEquals(state.byId, decoded.byId, "derived index must recompute identically")
        assertEquals(state.occupancy, decoded.occupancy)
    }

    @Test
    fun resolverRoundTripsWithANonEmptyStackAndAPendingDecision() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
        }
        val request = DecisionRequest(DecisionId(4))
        val queuedEffect = Effect.DealDamage(s.id("goblin"), 5, DamageType.Fire)
        val triggerEvent = GameEvent.MoveStepped(s.id("hero"), GridPos(0, 0), GridPos(1, 0))

        val resolver = Resolver(
            state = s.state,
            stack = listOf(queuedEffect),
            pending = request,
            answers = mapOf(DecisionId(1) to Decision(DecisionId(1), accept = false)),
            emitted = listOf(GameEvent.MoveStepped(s.id("hero"), GridPos(0, 0), GridPos(1, 0))),
            steps = 3,
            depth = 1,
            reactedTo = setOf(ReactedKey(s.id("goblin"), triggerEvent, s.state.version)),
        )

        val encoded = json.encodeToString(Resolver.serializer(), resolver)
        val decoded = json.decodeFromString(Resolver.serializer(), encoded)
        assertEquals(resolver, decoded)
        assertEquals(request, decoded.pending, "the mid-decision process-death case: pending must survive exactly")
        assertEquals(listOf(queuedEffect), decoded.stack)
    }

    @Test
    fun statusDurationVariantsAllRoundTrip() {
        val samples = listOf(
            Expiry.Permanent,
            Expiry.EndOfTurnOf(de.jackbeback.pocketquest.core.model.EntityId(1), 3),
            Expiry.StartOfTurnOf(de.jackbeback.pocketquest.core.model.EntityId(1), 3),
            Expiry.EndOfRound(3),
            Expiry.OnConcentrationLost,
        )
        for (expiry in samples) {
            val encoded = json.encodeToString(Expiry.serializer(), expiry)
            assertEquals(expiry, json.decodeFromString(Expiry.serializer(), encoded))
        }
    }

    @Test
    fun everyEffectPrimitiveRoundTrips() {
        val who = de.jackbeback.pocketquest.core.model.EntityId(1)
        val samples: List<Effect> = listOf(
            Effect.Ask(DecisionRequest(DecisionId(1))),
            Effect.DealDamage(who, 5, DamageType.Fire),
            Effect.MoveAlong(who, listOf(GridPos(1, 0))),
            Effect.SpendCost(who, ap = 1, mana = 1),
            Effect.ApplyStatus(who, StatusId("x"), expiry = Expiry.Permanent),
            Effect.RollAttack(who, who, 4, damage = DiceSpec(1, 6, 0), damageType = DamageType.Fire),
            Effect.RollSave(who, de.jackbeback.pocketquest.core.model.Ability.Con, 10),
            Effect.OfferReaction(GameEvent.Died(who), who, de.jackbeback.pocketquest.core.model.ActionId("x")),
            Effect.ResolveReaction(DecisionId(1), GameEvent.Died(who), who, de.jackbeback.pocketquest.core.model.ActionId("x")),
            Effect.EndTurn(who),
            Effect.StartConcentration(who, LinkId(1)),
            Effect.ConcentrationCheck(who, 10),
            Effect.Heal(who, 5),
            Effect.RemoveStatus(who, StatusId("x")),
            Effect.Composite(listOf(Effect.Heal(who, 1), Effect.DealDamage(who, 1, DamageType.Fire))),
        )
        for (effect in samples) {
            val encoded = json.encodeToString(Effect.serializer(), effect)
            assertEquals(effect, json.decodeFromString(Effect.serializer(), encoded))
        }
    }
}
