package de.jackbeback.pocketquest.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.RollBreakdown
import de.jackbeback.pocketquest.core.model.RollTerm
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.OK
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Minimal 3D vector math for [Icosahedron] — no rendering-engine dependency, just enough for a
 * hand-rolled software rasterizer over Compose's own [androidx.compose.ui.graphics.Path]/Canvas,
 * the same way every other visual in this game (Board's terrain/hatching, entity tokens) is drawn.
 */
private data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val l = length()
        return if (l == 0.0) this else Vec3(x / l, y / l, z / l)
    }
}

/**
 * A unit quaternion — orientation, not Euler angles, so slerping between two random orientations
 * never gimbal-locks or picks a visually "wrong way around" rotation path.
 */
private data class Quat(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(o: Quat) = Quat(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun rotate(v: Vec3): Vec3 {
        val qv = Quat(0.0, v.x, v.y, v.z)
        val r = this * qv * conjugate()
        return Vec3(r.x, r.y, r.z)
    }

    private fun conjugate() = Quat(w, -x, -y, -z)

    companion object {
        fun identity() = Quat(1.0, 0.0, 0.0, 0.0)

        fun fromAxisAngle(axis: Vec3, angle: Double): Quat {
            val a = axis.normalized()
            val s = sin(angle / 2)
            return Quat(cos(angle / 2), a.x * s, a.y * s, a.z * s)
        }

        /** Shoemake's uniform-random-rotation method — a naive random axis+angle biases toward the poles, this doesn't. */
        fun random(rng: Random): Quat {
            val u1 = rng.nextDouble(); val u2 = rng.nextDouble(); val u3 = rng.nextDouble()
            val s1 = sqrt(1 - u1); val s2 = sqrt(u1)
            return Quat(s1 * sin(2 * PI * u2), s1 * cos(2 * PI * u2), s2 * sin(2 * PI * u3), s2 * cos(2 * PI * u3))
        }

        fun slerp(a: Quat, b: Quat, t: Double): Quat {
            var bb = b
            var dot = a.w * bb.w + a.x * bb.x + a.y * bb.y + a.z * bb.z
            if (dot < 0) { bb = Quat(-bb.w, -bb.x, -bb.y, -bb.z); dot = -dot }
            if (dot > 0.9995) {
                val r = Quat(a.w + (bb.w - a.w) * t, a.x + (bb.x - a.x) * t, a.y + (bb.y - a.y) * t, a.z + (bb.z - a.z) * t)
                val l = sqrt(r.w * r.w + r.x * r.x + r.y * r.y + r.z * r.z)
                return Quat(r.w / l, r.x / l, r.y / l, r.z / l)
            }
            val theta0 = acos(dot)
            val theta = theta0 * t
            val sinTheta = sin(theta)
            val sinTheta0 = sin(theta0)
            val s0 = cos(theta) - dot * sinTheta / sinTheta0
            val s1 = sinTheta / sinTheta0
            return Quat(a.w * s0 + bb.w * s1, a.x * s0 + bb.x * s1, a.y * s0 + bb.y * s1, a.z * s0 + bb.z * s1)
        }
    }
}

private data class Face(val a: Int, val b: Int, val c: Int)

/**
 * A regular icosahedron via the golden-ratio-rectangle construction (three mutually orthogonal
 * golden rectangles' corners are the 12 vertices — standard, e.g. Wikipedia "Regular icosahedron").
 * Every face carries a stable 1..20 number ([faceNumbers]) so [DiceRoll] can land on a real face —
 * a real d20 rests with one face flat (its normal pointing straight at whoever's reading it), not
 * some arbitrary tumbled orientation with the result floating disconnected from the shape.
 */
private object Icosahedron {
    private val phi = (1.0 + sqrt(5.0)) / 2.0
    val vertices: List<Vec3> = listOf(
        Vec3(-1.0, phi, 0.0), Vec3(1.0, phi, 0.0), Vec3(-1.0, -phi, 0.0), Vec3(1.0, -phi, 0.0),
        Vec3(0.0, -1.0, phi), Vec3(0.0, 1.0, phi), Vec3(0.0, -1.0, -phi), Vec3(0.0, 1.0, -phi),
        Vec3(phi, 0.0, -1.0), Vec3(phi, 0.0, 1.0), Vec3(-phi, 0.0, -1.0), Vec3(-phi, 0.0, 1.0),
    ).map { it.normalized() }

    val faces: List<Face> = listOf(
        Face(0, 11, 5), Face(0, 5, 1), Face(0, 1, 7), Face(0, 7, 10), Face(0, 10, 11),
        Face(1, 5, 9), Face(5, 11, 4), Face(11, 10, 2), Face(10, 7, 6), Face(7, 1, 8),
        Face(3, 9, 4), Face(3, 4, 2), Face(3, 2, 6), Face(3, 6, 8), Face(3, 8, 9),
        Face(4, 9, 5), Face(2, 4, 11), Face(6, 2, 10), Face(8, 6, 7), Face(9, 8, 1),
    )

    val faceNormals: List<Vec3> = faces.map { faceNormal(it) }

    /**
     * Each face is paired with its geometric opposite (nearest-antipodal normal, found by search
     * rather than assumed from face-list order — the construction above isn't obviously indexed
     * that way) and the pair gets `n`/`21-n`, same opposite-faces-sum-to-21 parity a real d20 has.
     * Not a verified real-world d20 net, just a stylized game asset with the same nice property.
     */
    val faceNumbers: List<Int> by lazy {
        val opposite = IntArray(faceNormals.size) { i ->
            faceNormals.indices.minBy { j -> if (j == i) Double.MAX_VALUE else faceNormals[i].dot(faceNormals[j]) }
        }
        val numbers = IntArray(faceNormals.size)
        val assigned = BooleanArray(faceNormals.size)
        var next = 1
        for (i in faceNormals.indices) {
            if (assigned[i]) continue
            numbers[i] = next
            numbers[opposite[i]] = 21 - next
            assigned[i] = true
            assigned[opposite[i]] = true
            next++
        }
        numbers.toList()
    }

    fun faceNormal(f: Face): Vec3 {
        val a = vertices[f.a]; val b = vertices[f.b]; val c = vertices[f.c]
        return (b - a).cross(c - a).normalized()
    }

    /** The rotation that puts face [index]'s normal pointing straight at the camera (+Z — see the backface-cull check in [DiceRoll], which keeps normal.z > 0 as "facing the viewer"). */
    fun orientationShowing(index: Int): Quat {
        val n = faceNormals[index]
        val target = Vec3(0.0, 0.0, 1.0)
        val axis = n.cross(target)
        val angle = acos(n.dot(target).coerceIn(-1.0, 1.0))
        return if (axis.length() < 1e-6) Quat.identity() else Quat.fromAxisAngle(axis, angle)
    }
}

/** Not private — Director.kt's `showDiceRoll` beat needs the same duration to know how long to hold the overlay before removing it. */
internal const val TUMBLE_MS = 900
private val LIGHT_DIR = Vec3(0.4, 0.6, 1.0).normalized()

private fun lerp(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * tt, a.green + (b.green - a.green) * tt, a.blue + (b.blue - a.blue) * tt)
}

/**
 * A tumbling icosahedron settling with [result]'s actual face flat toward the viewer — like a real
 * d20 landing face-up — with the number drawn centered inside that same triangle, not floating
 * disconnected from the shape. [trigger] is a fresh value (e.g. [DiceRollOverlay.id]) each roll, so
 * re-rolling the same number still restarts the animation instead of no-op'ing on equal state. The
 * starting orientation and tumble axis/speed are still randomized per roll (seeded off [trigger]),
 * so consecutive rolls — even identical results — never look like the same repeated clip; only the
 * final resting orientation is constrained (to the correct face, at a random spin around it).
 */
@Composable
fun DiceRoll(result: Int, trigger: Any, world: VisualWorld, modifier: Modifier = Modifier, numberSize: TextUnit = 28.sp) {
    val progress = remember { Animatable(0f) }
    var startQuat by remember { mutableStateOf(Quat.random(Random.Default)) }
    var endQuat by remember { mutableStateOf(Quat.random(Random.Default)) }
    var spinAxis by remember { mutableStateOf(Vec3(0.0, 1.0, 0.0)) }
    var spinTurns by remember { mutableStateOf(3.0) }
    var resultFace by remember { mutableStateOf(0) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(trigger) {
        val rng = Random(trigger.hashCode() xor result)
        resultFace = Icosahedron.faceNumbers.indexOf(result).coerceAtLeast(0)
        startQuat = Quat.random(rng)
        // Facing the camera (orientationShowing) is the physical constraint; an extra random spin
        // around that same view axis is layered on top purely for variety — it doesn't change which
        // face ends up toward the viewer, only how it's rotated once it gets there.
        endQuat = Quat.fromAxisAngle(Vec3(0.0, 0.0, 1.0), rng.nextDouble(0.0, 2 * PI)) * Icosahedron.orientationShowing(resultFace)
        spinAxis = Vec3(rng.nextDouble(-1.0, 1.0), rng.nextDouble(-1.0, 1.0), rng.nextDouble(-1.0, 1.0)).normalized()
        spinTurns = 2.5 + rng.nextDouble() * 2.0
        progress.snapTo(0f)
        // Overshoot-then-settle easing — decelerates hard at the end, reads as "physical" without
        // any actual physics simulation (a well-worn trick: solve backward from a plausible-looking
        // settle curve instead of simulating rigid-body dynamics for a cosmetic flourish).
        progress.animateTo(1f, tween(world.scaled(TUMBLE_MS), easing = CubicBezierEasing(0.15f, 0.85f, 0.25f, 1f)))
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = progress.value.toDouble()
            // Extra spin decays to zero by t=1 (scaled by (1-t)) so the die ends exactly at endQuat,
            // not endQuat-plus-whatever-spin-was-left.
            val extraSpin = Quat.fromAxisAngle(spinAxis, 2 * PI * spinTurns * (1 - t))
            val base = Quat.slerp(startQuat, endQuat, t)
            val orientation = extraSpin * base

            val scale = size.minDimension * 0.42f
            val center = Offset(size.width / 2f, size.height / 2f)
            fun project(v: Vec3) = Offset(center.x + (v.x * scale).toFloat(), center.y - (v.y * scale).toFloat())

            data class Projected(val avgZ: Double, val path: Path, val shade: Float)

            val projected = Icosahedron.faces.indices.mapNotNull { i ->
                val face = Icosahedron.faces[i]
                val verts = listOf(face.a, face.b, face.c).map { orientation.rotate(Icosahedron.vertices[it]) }
                val normal = orientation.rotate(Icosahedron.faceNormals[i])
                if (normal.z <= 0.05) return@mapNotNull null // backface cull: camera looks down -Z
                val avgZ = verts.sumOf { it.z } / 3.0
                val brightness = (0.35 + 0.65 * normal.dot(LIGHT_DIR).coerceIn(0.0, 1.0)).toFloat()
                val pts = verts.map { project(it) }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    close()
                }
                Projected(avgZ, path, brightness)
            }.sortedBy { it.avgZ }

            projected.forEach { p ->
                drawPath(p.path, color = lerp(PAPER, INK, 1f - p.shade))
                drawPath(p.path, color = INK, style = Stroke(width = 2.5f))
            }

            // Fades in only over the tumble's last 30% — the number appearing mid-spin would read
            // as wrong/premature, since the shape doesn't visually show it yet at that point. By
            // then the result face is already (by construction of endQuat) at or very near
            // front-facing, so its live projected centroid is where the number belongs.
            val numberAlpha = ((t - 0.7) / 0.3).coerceIn(0.0, 1.0).toFloat()
            if (numberAlpha > 0f) {
                val face = Icosahedron.faces[resultFace]
                val centroid = listOf(face.a, face.b, face.c)
                    .map { project(orientation.rotate(Icosahedron.vertices[it])) }
                    .reduce { a, b -> a + b } / 3f
                val layout = textMeasurer.measure(text = "$result", style = TextStyle(fontSize = numberSize))
                drawText(layout, color = INK, topLeft = centroid - Offset(layout.size.width / 2f, layout.size.height / 2f), alpha = numberAlpha)
            }
        }
    }
}

fun RollTerm.chipText(): String {
    val sign = if (flat >= 0) "+" else ""
    val diceText = dice?.let { "+${it.count}d${it.sides}" } ?: ""
    return "$sign$flat$diceText"
}

/** One labeled chip in [RollCard]'s modifier row — "+3" over "Str" in small caps, matching BG3's icon+label+value chip without needing icon art. Not private: `ui.run.EventScreen` reuses it for the pre-roll preview row (docs/22). */
@Composable
fun ModifierChip(term: RollTerm) {
    Column(
        modifier = Modifier.padding(end = 6.dp).border(1.dp, INK_FAINT).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(term.chipText(), style = TextStyle(color = if (term.flat >= 0) OK else DANGER, fontSize = 14.sp))
        BasicText(term.label, style = TextStyle(color = INK_FAINT, fontSize = 9.sp))
    }
}

/**
 * docs/22-dice-roll-ui-and-ability-checks.md's full roll card — [DiceRollOverlay.title], a
 * DIFFICULTY CLASS/target banner, the tumbling die(s), and the modifier breakdown row underneath.
 * [overlay.otherResult] present means Advantage/Disadvantage: both dice render side by side, the one
 * that counted ([overlay.result]) at full opacity, the discarded one dimmed — matching BG3's own
 * dual-die display rather than only ever showing a single die. Used both for combat's fixed
 * HUD-centered overlay (`App.kt`) and the out-of-combat check screen (`EventScreen.kt`), so the
 * player sees one consistent roll presentation everywhere a d20 actually matters.
 */
@Composable
fun RollCard(
    overlay: DiceRollOverlay,
    world: VisualWorld,
    modifier: Modifier = Modifier,
    sprites: Map<EntityId, ImageBitmap> = emptyMap(),
    colors: Map<EntityId, Color> = emptyMap(),
) {
    Column(
        modifier = modifier.background(PAPER_SHEET).border(1.dp, INK).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // "Who vs whom" — an attack roll's attacker/target, or a save's caster/target (source null,
        // e.g. a status's own tick, just skips this row rather than showing one blank portrait).
        val attackerId = overlay.attackerId
        val defenderId = overlay.defenderId
        if (attackerId != null && defenderId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollPortrait(attackerId, sprites, colors)
                Spacer(Modifier.size(6.dp))
                BasicText(if (overlay.title == "Attack Roll") "Attacks" else "vs", style = TextStyle(color = INK_FAINT, fontSize = 11.sp))
                Spacer(Modifier.size(6.dp))
                RollPortrait(defenderId, sprites, colors)
            }
            Spacer(Modifier.size(10.dp))
        }
        BasicText(overlay.title, style = TextStyle(color = INK, fontSize = 15.sp))
        Spacer(Modifier.size(8.dp))
        BasicText("DIFFICULTY CLASS", style = TextStyle(color = INK_FAINT, fontSize = 10.sp))
        BasicText("${overlay.target}", style = TextStyle(color = INK, fontSize = 22.sp))
        Spacer(Modifier.size(10.dp))
        if (overlay.otherResult != null) {
            // The discarded (dimmed) die is always overlay.otherResult and the counted (solid) one
            // always overlay.result — that's a guarantee from the roll itself (d20Detailed always
            // resolves Advantage to max/Disadvantage to min before either value reaches this card),
            // not something this composable re-derives by comparing the two numbers.
            Row(verticalAlignment = Alignment.CenterVertically) {
                DiceRoll(
                    result = overlay.otherResult, trigger = "${overlay.id}-other", world = world,
                    modifier = Modifier.size(110.dp).padding(end = 4.dp).alpha(0.4f), numberSize = 20.sp,
                )
                DiceRoll(result = overlay.result, trigger = overlay.id, world = world, modifier = Modifier.size(110.dp), numberSize = 20.sp)
            }
        } else {
            DiceRoll(result = overlay.result, trigger = overlay.id, world = world, modifier = Modifier.size(140.dp))
        }
        if (overlay.breakdown.terms.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            Row { overlay.breakdown.terms.forEach { ModifierChip(it) } }
        }
    }
}

/** Same sprite-or-colored-circle token TurnOrderStrip's turn-order row already uses (App.kt) — reused here for RollCard's "who vs whom" header. */
@Composable
private fun RollPortrait(id: EntityId, sprites: Map<EntityId, ImageBitmap>, colors: Map<EntityId, Color>) {
    val sprite = sprites[id]
    if (sprite != null) {
        Image(bitmap = sprite, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(CircleShape))
    } else {
        Box(Modifier.size(32.dp).background(colors[id] ?: Color.Gray, CircleShape))
    }
}
