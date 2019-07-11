@file:Suppress("MemberVisibilityCanBePrivate")

package slack
import scripts.TimeOptions
import slackjson.*
import utils.Http
import java.time.ZoneId

abstract class SlackData(val settings: Settings) {
    // Raw data
    abstract val conversations: Map<String, Conversation>
    abstract val users: Map<String, User>

    // Basic data retrieval methods
    fun conversationName(convoId: String?) = conversations[convoId]?.fullName(this) ?: "Unknown conversation"
    fun conversationType(convoId: String?) : ConversationTypes {
        if (convoId == null) {
            return ConversationTypes.UNKNOWN
        }

        val convo = conversations[convoId] ?: error("Unknown conversation ID '$convoId'")
        return convo.conversationType()
    }

    fun userUsername(userId: String?) = users[userId]?.username() ?: "Unknown user"
    fun userDisplayname(userId: String?) = users[userId]?.displayname() ?: "Unknown name"

    // Inference methods
    fun inferUserID(userString: String) : String? {
        // User ID first
        if (users.containsKey(userString)) {
            return userString
        }

        // Username if it exists
        users.asSequence().forEach {
            if (it.value.username() == userString) {
                return it.key
            }
        }

        // Displayname if it exists
        users.asSequence().forEach {
            if (it.value.displayname() == userString) {
                return it.key
            }
        }

        return null
    }

    fun inferChannelID(channelString: String) : String? {
        if (conversations.containsKey(channelString)) {
            return channelString
        }

        conversations.forEach {
            if (it.value.fullName(this) == channelString) {
                return it.key
            }
        }

        return null
    }
}

data class Settings(
        val useDisplayNamesForConversationNames: Boolean = true,
        val useDisplayNamesForFiles: Boolean = false,

        val fileConflictStrategy: Http.ConflictStrategy = Http.ConflictStrategy.default(),
        val ignoreDownloadedFiles: Boolean = true,

        val outTz: ZoneId = ZoneId.systemDefault()
) {
    constructor(timeOptions: TimeOptions.Options): this(outTz = timeOptions.outTz)
}
