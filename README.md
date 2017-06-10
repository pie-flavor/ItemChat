# ItemChat

ItemChat is a Sponge plugin which allows you to display your
currently-held item in chat.

To use, simply write `{item}` as part of any chat message.
It will be replaced with the name of the item you are
holding, and if users hover over it, they can see the full
item tooltip. If the item has a custom display name, it will
be shown in chat instead of the item type.

If you want to use a different slot than your hand, simply
type `$num` after `item`, where `num` is the slot number.
For instance, I could write `{item $3}` to get the third
hotbar slot.

You can also display items that don't actually exist! Simply
write the NBT tag after `item`, as you would in a `/give`
command. For example, 
`{item {id:"minecraft:redstone",display:{Name:"Weird Dust"}`
would display redstone with a custom name of `Weird Dust`.
Nonexistent items are colored differently from regular items.

If your nonexistent item NBT is too long to type in chat, you
can write it in a book. Add `#!` after `item` to pull the NBT
from the book you're holding. You can also write a slot
number after `#!` if you want, to use a book from that hotbar
slot. For example, `{item #!3}` would pull NBT from a book in
hotbar slot 3.

You can also assign custom text to it. For instance, if I
were to type `{item Apple}`, it would output `[Apple]`
regardless of what the actual item name is. However, it will
be colored differently, so users without F3+H on will be able
to tell the difference. This custom name goes after slot
numbers and before NBT tags.

As a bonus, not only does the tooltip appear to each player
in their native language, but so does the item name in chat
if custom text isn't used and the item doesn't have a custom
display name.

If you are not holding anything, or the `$` slot number does
not contain anything, or the `#!` slot number does not
contain a book, then the `{item}` message will just appear
normally.

## Changelog

1.0.0: Initial release.

1.1.0: Added hotbar slots, NBT, and books.