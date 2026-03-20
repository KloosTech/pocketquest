package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ui.designer.DC

// ── Containers ────────────────────────────────────────────────────────────────

@Composable
fun EditorSectionTitle(text: String, color: Color = DC.Primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun EditorCard(
    borderColor: Color = DC.PanelBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DC.Panel)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun CardLabel(text: String) {
    Text(text, color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
}

// ── Fields ────────────────────────────────────────────────────────────────────

@Composable
fun FieldRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = DC.Subtext0, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

/** Text input field styled for the designer. */
@Composable
fun DField(
    value: String,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = TextStyle(color = if (enabled) DC.Text else DC.Overlay0, fontSize = 13.sp),
        cursorBrush = SolidColor(DC.Primary),
        maxLines = if (singleLine) 1 else 4,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(if (enabled) DC.InputBg else DC.Crust)
            .border(1.dp, DC.InputBorder, RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

/** Integer input field. Silently ignores non-numeric input. */
@Composable
fun IntField(value: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.toIntOrNull()?.let { onValueChange(it) }
        },
        singleLine = true,
        textStyle = TextStyle(color = DC.Text, fontSize = 13.sp),
        cursorBrush = SolidColor(DC.Primary),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(DC.InputBg)
            .border(1.dp, DC.InputBorder, RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

/** Float input field. */
@Composable
fun FloatField(value: Float, onValueChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.toFloatOrNull()?.let { onValueChange(it) }
        },
        singleLine = true,
        textStyle = TextStyle(color = DC.Text, fontSize = 13.sp),
        cursorBrush = SolidColor(DC.Primary),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(DC.InputBg)
            .border(1.dp, DC.InputBorder, RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

/** Simple dropdown (popover menu on click). */
@Composable
fun DropdownField(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(DC.InputBg)
                .border(1.dp, DC.InputBorder, RoundedCornerShape(5.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected, color = DC.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("▾", color = DC.Overlay0, fontSize = 12.sp)
        }
        if (expanded) {
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DC.Surface0)
                    .border(1.dp, DC.InputBorder, RoundedCornerShape(6.dp))
            ) {
                Column {
                    options.forEach { opt ->
                        Text(
                            opt,
                            color = if (opt == selected) DC.Primary else DC.Text,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(opt)
                                    expanded = false
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Toggle checkbox styled as a pill. */
@Composable
fun CheckboxField(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .clickable { onCheckedChange(!checked) }
            .background(if (checked) DC.Primary.copy(alpha = 0.15f) else DC.InputBg)
            .border(1.dp, if (checked) DC.Primary.copy(alpha = 0.4f) else DC.InputBorder, RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) DC.Primary else DC.Surface1)
                .border(1.dp, if (checked) DC.Primary else DC.Overlay0, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = DC.Crust, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = DC.Subtext1, fontSize = 12.sp)
    }
}

// ── Buttons ───────────────────────────────────────────────────────────────────

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DC.Primary)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = DC.Crust, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DC.Surface0)
            .border(1.dp, DC.Surface1, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = DC.Subtext1, fontSize = 13.sp)
    }
}

@Composable
fun DangerButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DC.Error.copy(alpha = 0.12f))
            .border(1.dp, DC.Error.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = DC.Error, fontSize = 12.sp)
    }
}

@Composable
fun ActionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DC.Surface0)
            .border(1.dp, DC.Surface1, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(label, color = DC.Subtext1, fontSize = 11.sp)
    }
}

@Composable
fun SuccessChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DC.Green.copy(alpha = 0.15f))
            .border(1.dp, DC.Green.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(label, color = DC.Green, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Status bar ────────────────────────────────────────────────────────────────

@Composable
fun StatusBar(
    message: String,
    isError: Boolean,
    isDirty: Boolean,
    filePath: String?,
    campaignName: String?,
    campaignLastSavedMs: Long,
    campaignDirty: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DC.Mantle)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isError) DC.Error else if (isDirty) DC.Warning else DC.Success)
        )
        Text(
            message,
            color = if (isError) DC.Error else DC.Subtext0,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        if (campaignName != null) {
            Text("Campaign: $campaignName", color = DC.Mauve, fontSize = 10.sp)
        }
        if (isDirty) Text("● Unsaved changes", color = DC.Warning, fontSize = 10.sp)
        if (filePath != null) {
            Text(
                filePath.let { if (it.length > 55) "…${it.takeLast(52)}" else it },
                color = DC.Overlay0,
                fontSize = 10.sp,
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
fun EmptyEditorPlaceholder(message: String, hint: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(DC.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⚔", fontSize = 40.sp, color = DC.Surface1)
            Text(message, color = DC.Subtext0, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = DC.Overlay0, fontSize = 12.sp)
        }
    }
}
