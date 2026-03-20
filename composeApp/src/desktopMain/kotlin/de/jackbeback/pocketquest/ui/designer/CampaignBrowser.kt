package de.jackbeback.pocketquest.ui.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.designer.io.CampaignFileIo
import de.jackbeback.pocketquest.designer.model.DesignerConfig
import de.jackbeback.pocketquest.ui.designer.panels.DField
import de.jackbeback.pocketquest.ui.designer.panels.FieldRow
import de.jackbeback.pocketquest.ui.designer.panels.PrimaryButton
import de.jackbeback.pocketquest.ui.designer.panels.SecondaryButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JFileChooser

@Composable
fun CampaignBrowser(
    config: DesignerConfig,
    onOpen: (File) -> Unit,
    onNew: (name: String, description: String) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    val campaigns = remember { CampaignFileIo.listCampaigns() }

    // Dim overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DC.Crust.copy(alpha = 0.75f))
            .then(if (onDismiss != null) Modifier.clickable { onDismiss() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Card — stop clicks from propagating to overlay
        Box(
            modifier = Modifier
                .size(680.dp, 440.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DC.Panel)
                .border(1.dp, DC.PanelBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DC.Mantle)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(DC.Mauve),
                            contentAlignment = Alignment.Center,
                        ) { Text("⚔", fontSize = 12.sp) }
                        Text("Campaign Browser", color = DC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (onDismiss != null) {
                        Text(
                            "✕",
                            color = DC.Subtext0,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onDismiss() }
                                .padding(6.dp),
                        )
                    }
                }

                HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // ── Left: recent campaigns ────────────────────────────────
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("RECENT CAMPAIGNS", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            SecondaryButton(
                                label = "Browse…",
                                onClick = {
                                    val chooser = JFileChooser(CampaignFileIo.defaultCampaignsRoot).apply {
                                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                        dialogTitle = "Open Campaign Folder"
                                    }
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        onOpen(chooser.selectedFile)
                                    }
                                },
                            )
                        }
                        HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

                        if (campaigns.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text("No campaigns yet", color = DC.Subtext0, fontSize = 13.sp)
                                    Text("Create one to get started →", color = DC.Overlay0, fontSize = 11.sp)
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                items(campaigns) { dir ->
                                    val campaignName = runCatching {
                                        CampaignFileIo.loadCampaign(dir).getOrThrow().name
                                    }.getOrElse { dir.name }
                                    val lastModDate = SimpleDateFormat("MMM d, yyyy").format(Date(dir.lastModified()))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpen(dir) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .width(3.dp)
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(DC.Mauve)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(campaignName, color = DC.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            Text(lastModDate, color = DC.Overlay0, fontSize = 10.sp)
                                            Text(
                                                dir.absolutePath.let { if (it.length > 42) "…${it.takeLast(39)}" else it },
                                                color = DC.Surface2,
                                                fontSize = 9.sp,
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = DC.PanelBorder.copy(alpha = 0.5f), thickness = 1.dp)
                                }
                            }
                        }
                    }

                    Box(Modifier.width(1.dp).fillMaxHeight().background(DC.PanelBorder))

                    // ── Right: new campaign form ──────────────────────────────
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("NEW CAMPAIGN", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

                        var newName by remember { mutableStateOf("") }
                        var newDesc by remember { mutableStateOf("") }

                        FieldRow("Name") {
                            DField(value = newName, onValueChange = { newName = it })
                        }
                        FieldRow("Description") {
                            DField(value = newDesc, singleLine = false, onValueChange = { newDesc = it })
                        }

                        Spacer(Modifier.weight(1f))

                        PrimaryButton(
                            label = "Create Campaign",
                            onClick = {
                                if (newName.isNotBlank()) onNew(newName.trim(), newDesc.trim())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
