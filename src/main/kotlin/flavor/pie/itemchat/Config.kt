package flavor.pie.itemchat

import com.google.common.reflect.TypeToken
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable

@ConfigSerializable
class Config {
    companion object {
        val type: TypeToken<Config> = TypeToken.of(Config::class.java)
    }

    @Setting var version: Int = 0
    @Setting("max-expander-size-chat") var maxExpanderSizeChat = 50
}