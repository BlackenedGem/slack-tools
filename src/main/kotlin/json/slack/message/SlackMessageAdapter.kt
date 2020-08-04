package json.slack.message

import com.squareup.moshi.*
import org.apache.logging.log4j.kotlin.Logging

object SlackMessageAdapter : Logging {
    private val keys = JsonReader.Options.of("type", "subtype")

    // TODO maybe better to lazily initialise the adapters?
    // Do some performance analysis on this when we have more adapters?
    @FromJson
    fun fromJson(
            reader: JsonReader,
            textMessageAdapter: JsonAdapter<TextMessage>,
            channelMessageAdapter: JsonAdapter<ChannelMessage>
    ): BaseMessage? {
        // Read type/subtype with peeked reader
        val peekedReader = reader.peekJson()

        var type: String? = null
        var subtypeStr: String? = null

        // TODO Use extension functions to consume object when kotlin supports non-local breaks
        peekedReader.beginObject()
        while (peekedReader.hasNext()) {
            when (peekedReader.selectName(keys)) {
                // It might be worth figuring out if we can make this use Options to improve performance?
                // The issue is mapping the various types/subtypes to numbers and retaining the switch lookup performance
                // For the type we could check if it's 1/0
                // For the subtype we'd have to figure out the available subtypes defined, and then use that for our map

                0 -> type = peekedReader.nextString()
                1 -> subtypeStr = peekedReader.nextString()
                -1 -> peekedReader.skipValue()
            }

            if (type != null && subtypeStr != null) {
                break
            }
        }
        // Don't call endObject on the peeked reader

        // Parse message into actual type
        if (type != "message") {
            throw JsonDataException("Message type was not 'message', but was '$type'")
        }

        val subtype = MessageType.lookup(subtypeStr)
        // TODO extend this
        val message = when (subtype) {
            Other.STANDARD_MESSAGE -> textMessageAdapter.fromJson(reader)
            is ChannelType -> channelMessageAdapter.fromJson(reader)
            else -> {
                // Since our list of subtypes is currently non-exhaustive then skip processing the message
                logger.debug { "Cannot process message subtype '${subtype}'" }
                reader.skipValue()
                return null
            }
        }!!
        message.subtype = subtype
        return message
    }

    @ToJson
    @Suppress("UNUSED_PARAMETER")
    fun toJson(baseMessage: BaseMessage) : String {
        throw UnsupportedOperationException("Serialisation of BaseMessage is not supported")
    }
}