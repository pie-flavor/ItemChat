# ItemChat

ItemChat is a Sponge plugin which allows you to display your
currently-held item in chat.

To use, simply write `{item}` as part of any chat message.
It will be replaced with the name of the item you are
holding, and if users hover over it, they can see the full
item tooltip. If the item has a custom display name, it will
be shown in chat instead of the item type.

You can also assign custom text to it. For instance, if I
were to type `{item Apple}`, it would output `[Apple]`
regardless of what the actual item name is. However, it will
be colored differently, so users without F3+H on will be able
to tell the difference.

As a bonus, not only does the tooltip appear to each player
in their native language, but so does the item name in chat
if custom text isn't used and the item doesn't have a custom
display name.

If you are not holding anything, then `{item}` will just
appear normally.