package slackjson.message

import com.squareup.moshi.*

interface BaseMessage {
    val ts: String
}

interface BaseUserMessage : BaseMessage {
    val user: String
}

object BaseMessageCustomAdapter {
    private val keys = JsonReader.Options.of("type", "subtype")

    @FromJson
    fun fromJson(reader: JsonReader, textMessageAdapter: JsonAdapter<TextMessage>, channelMessageAdapter: JsonAdapter<ChannelMessage>): BaseMessage? {
        // Read type/subtype with peeked reader
        val peekedReader = reader.peekJson()

        var type: String? = null
        var subtype: String? = null

        peekedReader.beginObject()
        while (peekedReader.hasNext()) {
            when (peekedReader.selectName(keys)) {
                0 -> type = peekedReader.nextString()
                1 -> subtype = peekedReader.nextString()
                -1 -> peekedReader.skipValue()
            }

            if (type != null && subtype != null) {
                break
            }
        }
        // Don't call endObject on the peeked reader

        // Parse message into actual type
        if (type != "message") {
            throw JsonDataException("Message type was not 'message', but was '$type'")
        }

        // TODO extend this
        return when (MessageType.lookup(subtype)) {
            Other.STANDARD_MESSAGE -> textMessageAdapter.fromJson(reader)
            is ChannelType -> channelMessageAdapter.fromJson(reader)
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(baseMessage: BaseMessage) : String {
        throw JsonEncodingException("Encoding to Json not supported")
    }
}