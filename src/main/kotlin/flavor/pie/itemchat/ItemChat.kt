@file:Suppress("UNUSED_PARAMETER")
package flavor.pie.itemchat

import com.google.inject.Inject
import flavor.pie.kludge.*
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import ninja.leaping.configurate.loader.ConfigurationLoader
import org.bstats.sponge.MetricsLite2
import org.intellij.lang.annotations.Language
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.data.DataContainer
import org.spongepowered.api.data.DataQuery
import org.spongepowered.api.data.DataView
import org.spongepowered.api.data.key.Keys
import org.spongepowered.api.data.persistence.DataFormats
import org.spongepowered.api.data.persistence.DataTranslators
import org.spongepowered.api.data.type.HandTypes
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.command.SendCommandEvent
import org.spongepowered.api.event.filter.cause.First
import org.spongepowered.api.event.game.state.GamePostInitializationEvent
import org.spongepowered.api.event.game.state.GamePreInitializationEvent
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
import java.nio.file.Files
import java.nio.file.Path

typealias Expander = (ItemStack) -> Text?
internal lateinit var config: Config

@Plugin(id = "itemchat", name = "ItemChat", version = "1.4.2-SNAPSHOT", authors = ["pie_flavor"], description = "Allows you to display your item in chat.")
class ItemChat @Inject constructor(@DefaultConfig(sharedRoot = true) val path: Path,
                                   @DefaultConfig(sharedRoot = true) val loader: ConfigurationLoader<CommentedConfigurationNode>,
                                   val metrics: MetricsLite2) {

    val expanders: MutableMap<String, Expander> = mutableMapOf(
            "nbt" to { item -> toNBT(item)?.let { DataFormats.JSON.write(it).text() } },
            "nbt+tag" to { item -> toNBT(item, true)?.let { DataFormats.JSON.write(it).text() } },
            "/give" to { item -> "${item.type.id} ${item.quantity} ${getData(item)} ${toNBT(item)?.let { DataFormats.JSON.write(it) } ?: return@to null}".text() },
            "/summon" to { item -> "{Age:-32768,Health:5,Item:${toNBT(item, true)?.let { DataFormats.JSON.write(it) } ?: return@to null }}".text() },
            "name" to { item -> item[Keys.DISPLAY_NAME].unwrap() },
            "id" to { item -> item.type.id.text() },
            "type" to { item -> item.type.text() },
            "count" to { item -> item.quantity.toString().text() },
            "data" to { item -> getData(item).toString().text() }
    )
    companion object {
        @Language("RegExp")
        const val pattern = """\{item(@(?<expander>[^ ]+))?( \$(?<slot>[1-9]))?(?<book> #!(?<bookslot>[1-9])?)?( (?<name>[^({].+?))?( (\((?<nbtp>\{.*?})\)|(?<nbt>\{.*}(?!\)))))?}"""
    }

    object Queries {
        val unsafeDamage: DataQuery = DataQuery.of("UnsafeDamage")
        val count: DataQuery = DataQuery.of("Count")
        val contentVersion: DataQuery = DataQuery.of("ContentVersion")
        val unsafeData: DataQuery = DataQuery.of("UnsafeData")
        val itemType: DataQuery = DataQuery.of("ItemType")
        val damage: DataQuery = DataQuery.of("Damage")
        val id: DataQuery = DataQuery.of("id")
        val tag: DataQuery = DataQuery.of("tag")
    }

    object Permissions {
        const val arbitraryNbt = "itemchat.arbitrary_nbt"
        const val bypassExpanderSize = "itemchat.bypass_expander_size"
    }

    @Listener
    fun preInit(e: GamePreInitializationEvent) {
        if (!Files.exists(path)) {
            AssetManager.getAsset(this, "default.conf").get().copyToFile(path)
        }
        config = loader.load().getValue(Config.type)!!
    }

    @Listener
    fun postInit(e: GamePostInitializationEvent) {
        val svc: PermissionService by UncheckedService
        svc.defaults.transientSubjectData.setPermission(SubjectData.GLOBAL_CONTEXT, Permissions.arbitraryNbt, Tristate.TRUE)
    }

    @Listener
    fun chat(e: MessageChannelEvent.Chat, @First p: Player) {
        val msg = e.formatter.body.format()
        val size = regex.findAll(msg.toPlain()).toList().size
        if (size > 1) {
            p.sendMessage(!"You cannot use {item} more than once!")
        } else if (size == 1) {
            e.formatter.setBody(msg.addItem(p, true)?.unescape() ?: return)
        } else {
            val unescaped = msg.unescape()
            if (unescaped != msg) {
                e.formatter.setBody(unescaped)
            }
        }
    }

    @Listener
    fun command(e: SendCommandEvent, @First p: Player) {
        var args: Text = e.arguments.text()
        val size = regex.findAll(e.arguments).toList().size
        if (size > 1) {
            p.sendMessage(!"You cannot use {item} more than once!")
        } else if (size == 1) {
            val withItem = args.addItem(p, false)
            if (withItem != null) {
                args = withItem
            }
        }
        e.arguments = args.unescape().toPlain()
    }


    val regex = """(?<!\$)$pattern""".toRegex()
    val escaped = """\$+$pattern""".toRegex()
    fun Text.addItem(p: Player, isChat: Boolean): Text? {
        val msg = if (children.isEmpty()) this else {
            toBuilder().removeAll().append(children.map { it.addItem(p, isChat) ?: it }).build()
        }
        val plain = toPlainSingle()
        val res = regex.find(plain) ?: return msg
        val name = res.groups["name"]
        val nbt = res.groups["nbt"] ?: res.groups["nbtp"]
        val slot = res.groups["slot"]
        val book = res.groups["book"]
        val bookslot = res.groups["bookslot"]
        val expander = res.groups["expander"]
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
                            bookslot.value.toInt()
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
                if (num !in 1..9) {
                    p.sendMessage(!"Invalid index!")
                    return null
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
        val itemText = if (expander == null) {
            if (name == null) {
                val dispName = i[Keys.DISPLAY_NAME].unwrap()
                if (dispName == null) {
                    (!"[" + i + "]").color(color1)
                } else {
                    (!"[" + dispName + "]").color(color2)
                }
            } else {
                "[${name.value}]".color(color3)
            }.onHover(TextActions.showItem(i.createSnapshot()))
        } else {
            val ext = expanders.getOrDefault(expander.value, null)
            if (ext == null) {
                p.sendMessage(!"Invalid expander ${expander.value}!")
                return null
            }
            (ext(i) ?: return null).also {
                if (isChat && !p.hasPermission(Permissions.bypassExpanderSize) && it.toPlain().length > config.maxExpanderSizeChat) {
                    p.sendMessage(!"Expanded item too long!")
                    return null
                }
            }
        }
        val style = msg.style
        val hover = msg.hoverAction
        val click = msg.clickAction
        val shift = msg.shiftClickAction
        val (part1, part2) = plain.split(regex)
        val fmtPart1 = part1.let { if (hover.isPresent) it.onHover(hover.get()) else !it }
                .let { if (click.isPresent) it.onClick(click.get()) else it }
                .let { if (shift.isPresent) it.onShiftClick(shift.get()) else it }
        val fmtPart2 = part2.let { if (hover.isPresent) it.onHover(hover.get()) else !it }
                .let { if (click.isPresent) it.onClick(click.get()) else it }
                .let { if (shift.isPresent) it.onShiftClick(shift.get()) else it }
        val builder = Text.builder() + fmtPart1 + itemText + fmtPart2
        return builder.style(style).build()
    }

    fun Text.unescape(): Text {
        var text: Text = this
        for (res in escaped.findAll(toPlain())) {
            text = text.replace(res.value, !res.value.substring(1))
        }
        return text
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
            Queries.unsafeData.let { if (it !in sData) sData.createView(it) }
            Queries.itemType.let { if (it !in sData) sData[it] = ItemTypes.STONE.id }
            Queries.count.let { if (it !in sData) sData[it] = 1 }
            return sData.getSerializable(DataQuery.of(), ItemStackSnapshot::class.java).unwrap()?.createStack()
        } catch (_: Exception) {
            return null
        }
    }

    fun toNBT(stack: ItemStack, tagKey: Boolean = false): DataContainer? {
        try {
            val data = stack.toContainer()
            val sData = DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED)
            for (key in data.getKeys(false)) {
                when (key) {
                    Queries.unsafeDamage -> sData[Queries.damage] = data.getInt(key).get()
                    Queries.count -> sData[Queries.count] = data.getInt(key).get()
                    Queries.itemType -> sData[Queries.id] = data.getString(key).get()
                    Queries.unsafeData -> for (tag in data.getView(key).get().getKeys(false)) {
                        sData[if (tagKey) Queries.tag.then(tag) else tag] = data[key.then(tag)].get()
                    }
                }
                Queries.id.let { if (it !in sData) sData[it] = ItemTypes.STONE.id }
                Queries.damage.let { if (it !in sData) sData[it] = 0 }
                Queries.count.let { if (it !in sData) sData[it] = 1 }
                if (tagKey) {
                    Queries.tag.let { if (it !in sData) sData.createView(it) }
                }
            }
            return sData
        } catch (_: Exception) {
            return null
        }
    }

    fun getData(stack: ItemStack): Int {
        return stack.toContainer().getInt(Queries.unsafeDamage).orElse(0)
    }
}