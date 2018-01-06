package flavor.pie.itemchat

import flavor.pie.kludge.*
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.api.data.DataContainer
import org.spongepowered.api.data.DataQuery
import org.spongepowered.api.data.DataView
import org.spongepowered.api.data.key.Keys
import org.spongepowered.api.data.persistence.DataTranslators
import org.spongepowered.api.data.type.HandTypes
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.filter.cause.First
import org.spongepowered.api.event.game.state.GamePostInitializationEvent
import org.spongepowered.api.event.message.MessageChannelEvent
import org.spongepowered.api.item.ItemTypes
import org.spongepowered.api.item.inventory.ItemStack
import org.spongepowered.api.item.inventory.ItemStackSnapshot
import org.spongepowered.api.item.inventory.entity.Hotbar
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.service.permission.PermissionService
import org.spongepowered.api.service.permission.SubjectData
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.action.TextActions
import org.spongepowered.api.text.format.TextColors
import org.spongepowered.api.util.Tristate
import java.io.BufferedReader
import java.io.StringReader

@[Plugin(id = "itemchat", name = "ItemChat", version = "1.2.1", authors = ["pie_flavor"])]
class ItemChat {
    object Queries {
        val unsafeDamage = DataQuery.of("UnsafeDamage")!!
        val count = DataQuery.of("Count")!!
        val contentVersion = DataQuery.of("ContentVersion")!!
        val unsafeData = DataQuery.of("UnsafeData")!!
        val itemType = DataQuery.of("ItemType")!!
        val damage = DataQuery.of("Damage")!!
        val id = DataQuery.of("id")!!
        val tag = DataQuery.of("tag")!!
    }

    object Permissions {
        val arbitraryNbt = "itemchat.arbitrary_nbt"
    }

    @[Listener]
    fun postInit(e: GamePostInitializationEvent) {
        val svc: PermissionService by UncheckedService
        svc.defaults.transientSubjectData.setPermission(SubjectData.GLOBAL_CONTEXT, Permissions.arbitraryNbt, Tristate.TRUE)
    }

    @[Listener]
    fun chat(e: MessageChannelEvent.Chat, @[First] p: Player) {
        val msg = e.formatter.body.format()
        val size = pattern.findAll(msg.toPlain()).toList().size
        if (size == 0) return
        if (size > 1) {
            p.sendMessage(!"You cannot use {item} more than once!")
            return
        }
        e.formatter.setBody(msg.addItem(p) ?: return)
    }

    val pattern = """\{item( \$(?<slot>[1-9]))?(?<book> #!(?<bookslot>[1-9])?)?( (?<name>[^({].+?))?( (\((?<nbtp>\{.*?})\)|(?<nbt>\{.*}(?!\)))))?}""".toRegex()
    fun Text.addItem(p: Player): Text? {
        val msg = if (children.isEmpty()) this else {
            toBuilder().removeAll().append(children.map { it.addItem(p) ?: it }).build()
        }
        val plain = toPlainSingle()
        val res = pattern.find(plain) ?: return msg
        val name = res.groups["name"]
        val nbt = res.groups["nbt"] ?: res.groups["nbtp"]
        val slot = res.groups["slot"]
        val book = res.groups["book"]
        val bookslot = res.groups["bookslot"]
        var color1 = TextColors.YELLOW
        var color2 = TextColors.GOLD
        var color3 = TextColors.AQUA
        val i = if (nbt == null) {
            if (slot == null) {
                if (book == null) {
                    p.getItemInHand(HandTypes.MAIN_HAND).unwrap() ?: p.getItemInHand(HandTypes.OFF_HAND).unwrap() ?: return null
                } else {
                    if (!p.hasPermission(Permissions.arbitraryNbt)) {
                        p.sendMessage(!"You don't have permission to create NBT item text!")
                        return null
                    }
                    if (bookslot == null) {
                        val bookStack = p.getItemInHand(HandTypes.MAIN_HAND).unwrap() ?: p.getItemInHand(HandTypes.OFF_HAND).unwrap() ?: return null
                        val text = bookStack[Keys.BOOK_PAGES].unwrap()?.joinToString(separator = "", transform = Text::toPlain) ?: return null
                        parseItem(text) ?: return run {
                            p.sendMessage(!"Invalid NBT!")
                            null
                        }
                    } else {
                        color1 = TextColors.LIGHT_PURPLE
                        color2 = TextColors.DARK_PURPLE
                        color3 = TextColors.DARK_AQUA
                        val num = try {
                            book.value.toInt()
                        } catch (ex: NumberFormatException) {
                            p.sendMessage(!"Invalid index!")
                            return null
                        }
                        if (num !in 1..9) {
                            p.sendMessage(!"Invalid index!")
                            return null
                        }
                        val bookStack = p.inventory[Hotbar::class][num - 1]!!.peek().unwrap() ?: return null
                        val text = bookStack[Keys.BOOK_PAGES].unwrap()?.joinToString(separator = "", transform = Text::toPlain) ?: return null
                        parseItem(text) ?: return run {
                            p.sendMessage(!"Invalid NBT!")
                            null
                        }
                    }
                }
            } else {
                val num = try {
                    slot.value.toInt()
                } catch (ex: NumberFormatException) {
                    p.sendMessage(!"Invalid index!")
                    return null
                }
                when (num) {
                    !in 1..9 -> {
                        p.sendMessage(!"Invalid index!")
                        return null
                    }
                }
                p.inventory[Hotbar::class][num - 1]!!.peek().unwrap() ?: return null
            }
        } else {
            if (!p.hasPermission(Permissions.arbitraryNbt)) {
                p.sendMessage(!"You don't have permission to create NBT item text!")
                return null
            }
            color1 = TextColors.LIGHT_PURPLE
            color2 = TextColors.DARK_PURPLE
            color3 = TextColors.DARK_AQUA
            parseItem(nbt.value) ?: return run {
                p.sendMessage(!"Invalid NBT!")
                null
            }
        }
        val itemText = if (name == null) {
            val dispName = i[Keys.DISPLAY_NAME].unwrap()
            if (dispName == null) {
                (!"[" + i + "]").color(color1)
            } else {
                (!"[" + dispName + "]").color(color2)
            }
        } else {
            "[${name.value}]".color(color3)
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

    fun parseItem(str: String): ItemStack? {
        try {
            val node = HoconConfigurationLoader.builder().setSource { BufferedReader(StringReader(str)) }.build().load()
            val data = DataTranslators.CONFIGURATION_NODE.translate(node)
            val sData = DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED)
            sData[Queries.contentVersion] = 1
            for (key in data.getKeys(false)) {
                when (key) {
                    Queries.damage -> sData[Queries.unsafeDamage] = data.getInt(key).get()
                    Queries.count -> sData[Queries.count] = data.getInt(key).get()
                    Queries.id -> sData[Queries.itemType] = data.getString(key).get()
                    Queries.tag -> for (tag in data.getView(key).get().getKeys(false)) {
                        sData[Queries.unsafeData.then(tag)] = data[key.then(tag)].get()
                    }
                    else -> sData[Queries.unsafeData.then(key)] = data[key].get()
                }
            }
            Queries.unsafeDamage.let { if (it !in sData) sData[it] = 0 }
            Queries.unsafeData.let { if (it !in sData) sData[it] = DataContainer.createNew() }
            Queries.itemType.let { if (it !in sData) sData[it] = ItemTypes.STONE.id }
            Queries.count.let { if (it !in sData) sData[it] = 1 }
            return sData.getSerializable(DataQuery.of(), ItemStackSnapshot::class.java).unwrap()?.createStack()
        } catch (_: Exception) {
            return null
        }
    }
}