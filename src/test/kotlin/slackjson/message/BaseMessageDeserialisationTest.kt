package slackjson.message

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class BaseMessageDeserialisationTest {
    private val adapter: JsonAdapter<BaseMessage> = Moshi.Builder()
            .add(BaseMessageCustomAdapter)
            .build()
            .adapter(BaseMessage::class.java)

    @Test
    fun textMessageSerialisation() {
        val input = readResource("basic-message.json")
        val parsed = adapter.fromJson(input)!! as TextMessage

        assertThat(parsed.ts).isEqualTo("1355517523.000005")
        assertThat(parsed.text).isEqualTo("Hello world")
    }

    @Test
    fun channelJoin() {
        val input = readResource("channel-join.json")
        val parsed = adapter.fromJson(input)!! as ChannelMessage

        assertThat(parsed.subtype).isEqualTo(ChannelType.CHANNEL_JOIN)
    }

    @Test
    @Suppress("SpellCheckingInspection")
    fun invalidType() {
        val input = readResource("invalid-type.json")
        assertThatThrownBy { adapter.fromJson(input) }
                .isInstanceOf(JsonDataException::class.java)
                .hasMessageContaining("Message type was not 'message', but was 'messagee'")
    }

    @Test
    fun unknownType() {
        val input = readResource("unknown-subtype.json")
        assertThat(adapter.fromJson(input)).isNull()
    }

    private fun readResource(resource: String) : String {
        val inputStream  = this::class.java.getResourceAsStream(resource)!!
        return inputStream.reader().readText()
    }
}