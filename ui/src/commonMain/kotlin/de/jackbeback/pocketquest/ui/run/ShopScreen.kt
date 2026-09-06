package de.jackbeback.pocketquest.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.ShopPool
import de.jackbeback.pocketquest.core.run.BuyResult
import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.run.RunState
import de.jackbeback.pocketquest.core.run.SellResult
import de.jackbeback.pocketquest.core.run.buy
import de.jackbeback.pocketquest.core.run.offerShopVisit
import de.jackbeback.pocketquest.core.run.resolveShopNode
import de.jackbeback.pocketquest.core.run.sell
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.PAPER

private const val SHOP_ITEMS_PER_VISIT = 4

/**
 * docs/13-encounters-and-events.md's Shops section — offered stock is picked once per visit
 * (`remember(run.position)`), buy/sell mutate a local working [RunState] as the player shops, and
 * only "Leave Shop" hands the final state back to be persisted and marked visited.
 */
@Composable
fun ShopNodeScreen(run: RunState, cat: Catalog, node: GraphNode, pools: List<ShopPool>, onDone: (RunState) -> Unit) {
    val (shop, rngAfterShopPick) = remember(run.position) { resolveShopNode(run, node, pools, cat) }
    val (rngAfterOffer, offered) = remember(run.position) { offerShopVisit(shop, SHOP_ITEMS_PER_VISIT, rngAfterShopPick) }
    var working by remember(run.position) { mutableStateOf(run.copy(rng = rngAfterOffer)) }
    var message by remember(run.position) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText("Shop", style = TextStyle(color = INK, fontSize = 20.sp))
        BasicText("Gold: ${working.gold}", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
        Spacer(modifier = Modifier.size(12.dp))

        offered.forEach { entry ->
            Row(modifier = Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("${cat.itemDef(entry.item).name} — ${entry.price}g", modifier = Modifier.padding(end = 8.dp), style = TextStyle(color = INK, fontSize = 14.sp))
                InkButton(
                    "Buy",
                    onClick = {
                        when (val result = buy(working, entry, cat)) {
                            is BuyResult.Bought -> { working = result.run; message = null }
                            is BuyResult.Rejected -> message = result.reasons.joinToString(", ") { it.toString() }
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.size(16.dp))
        BasicText("Inventory", style = TextStyle(color = INK, fontSize = 16.sp))
        working.inventory.items.distinct().forEach { item ->
            Row(modifier = Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(cat.itemDef(item).name, modifier = Modifier.padding(end = 8.dp), style = TextStyle(color = INK, fontSize = 14.sp))
                InkButton(
                    "Sell",
                    onClick = {
                        when (val result = sell(working, item, cat)) {
                            is SellResult.Sold -> { working = result.run; message = null }
                            is SellResult.Rejected -> message = result.reasons.joinToString(", ") { it.toString() }
                        }
                    },
                )
            }
        }

        message?.let {
            Spacer(modifier = Modifier.size(8.dp))
            BasicText(it, style = TextStyle(color = INK_FAINT, fontSize = 12.sp))
        }

        Spacer(modifier = Modifier.size(16.dp))
        InkButton("Leave Shop", onClick = { onDone(working) })
    }
}
