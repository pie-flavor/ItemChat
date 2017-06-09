package flavor.pie.itemchat

import flavor.pie.kludge.*
import org.spongepowered.api.data.key.Keys
import org.spongepowered.api.data.type.HandTypes
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.filter.cause.First
import org.spongepowered.api.event.message.MessageChannelEvent
import org.spongepowered.api.item.inventory.ItemStack
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.action.TextActions

@[Plugin(id = "itemchat", name = "ItemChat", version = "1.0.0", authors = arrayOf("pie_flavor"))]
class ItemChat {

    @[Listener]
    fun chat(e: MessageChannelEvent.Chat, @[First] p: Player) {
        val msg = e.formatter.body.format()
        val size = pattern.findAll(msg.toPlain()).toList().size
        if (size == 0) return
        if (size > 1) {
            p.sendMessage(!"You cannot use {item} more than once!")
            return
        }
        val item = p.getItemInHand(HandTypes.MAIN_HAND).unwrap() ?: p.getItemInHand(HandTypes.OFF_HAND).unwrap() ?: return
        e.formatter.setBody(msg.addItem(item))
    }

    val pattern = "\\{item( (.+?))?}".toRegex()
    fun Text.addItem(i: ItemStack): Text {
        val msg = if (children.isEmpty()) this else {
            toBuilder().removeAll().append(children.map { it.addItem(i) }).build()
        }
        val plain = toPlainSingle()
        val res = pattern.find(plain) ?: return msg
        val name = res.groups[2]
        val itemText = if (name == null) {
            val dispName = i[Keys.DISPLAY_NAME].unwrap()
            if (dispName == null) {
                (!"[" + i + "]").yellow()
            } else {
                (!"[" + dispName + "]").gold()
            }
        } else {
            "[${name.value}]".aqua()
        }.onHover(TextActions.showItem(i.createSnapshot()))
        val style = msg.style
        val hover = msg.hoverAction
        val click = msg.clickAction
        val shift = msg.shiftClickAction
        val (part1, part2) = plain.split(pattern)
        val fmtPart1 = part1.let { if (hover.isPresent) it.onHover(hover.get()) else !it }
                .let { if (click.isPresent) it.onClick(click.get()) else it }
                .let { if (shift.isPresent) it.onShiftClick(shift.get()) else it }
        val fmtPart2 = part2.let { if (hover.isPresent) it.onHover(hover.get()) else !it }
                .let { if (click.isPresent) it.onClick(click.get()) else it }
                .let { if (shift.isPresent) it.onShiftClick(shift.get()) else it }
        val builder = Text.builder() + fmtPart1 + itemText + fmtPart2
        return builder.style(style).build()
    }
}