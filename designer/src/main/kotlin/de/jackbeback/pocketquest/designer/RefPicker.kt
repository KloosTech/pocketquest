package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.SlotKey
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkTextField

private enum class RefKind { Caster, EachTarget, Slot }

private fun Ref.kind(): RefKind = when (this) {
    is Ref.Caster -> RefKind.Caster
    is Ref.EachTarget -> RefKind.EachTarget
    is Ref.Slot -> RefKind.Slot
}

/**
 * Every [EffectTemplate][de.jackbeback.pocketquest.core.model.EffectTemplate] field that names
 * "who/what" this effect acts on/with is a [Ref] — one small picker reused everywhere one of those
 * fields appears, rather than re-authoring the same 3-way dropdown per effect kind.
 */
@Composable
fun RefPicker(ref: Ref, onChange: (Ref) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        InkSelect(
            selected = ref.kind(),
            options = RefKind.entries,
            label = { it.name },
            onSelect = { kind ->
                onChange(
                    when (kind) {
                        RefKind.Caster -> Ref.Caster
                        RefKind.EachTarget -> Ref.EachTarget
                        RefKind.Slot -> Ref.Slot((ref as? Ref.Slot)?.key ?: SlotKey(""))
                    },
                )
            },
        )
        if (ref is Ref.Slot) {
            InkTextField(
                ref.key.raw,
                onValueChange = { onChange(Ref.Slot(SlotKey(it))) },
                modifier = Modifier.width(120.dp),
            )
        }
    }
}
