package utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.apache.logging.log4j.kotlin.Logging
import slackjson.*
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WebApi @Inject constructor(
        private val moshi: Moshi,
        @Named("SlackToken") token: String
) {
    companion object : Logging {
        // URLs
        private const val URL_CONVO_LIST = "https://slack.com/api/conversations.list"
        private const val URL_FILES_INFO = "https://slack.com/api/files.info"
        private const val URL_FILES_LIST = "https://slack.com/api/files.list"
        private const val URL_USERS_LIST = "https://slack.com/api/users.list"

        // Limits
        private const val CONVO_LIST_LIMIT = 100
        private const val FILE_LIST_LIMIT = 100
        private const val USERS_LIST_LIMIT = 100

        // Rate limit times to wait (in s)
        private const val RETRY_TIER_1 = 60
        private const val RETRY_TIER_2 = 3
        private const val RETRY_TIER_3 = 1
        private const val RETRY_TIER_4 = 1
    }

    private val http = Http(token)

    /**
     * Equivalent to Http.downloadFile, but manages token for us
     */
    fun downloadFile(url: String, saveLoc: Path, size: Long? = null, strategy: Http.ConflictStrategy = Http.ConflictStrategy.default()) : DownloadStatus {
        return http.downloadFile(url, saveLoc, size, strategy)
    }

    /**
     * Returns a map conversations (channels, groups, ims). Key is conversation id
     */
    fun getConversations() : Map<String, Conversation> {
        val convos = mutableMapOf<String, Conversation>()
        val params = mutableMapOf(
                "limit" to CONVO_LIST_LIMIT.toString(),
                "types" to "public_channel, private_channel, im",
                "cursor" to "")

        val adapter = moshi.adapter(ConversationListResponse::class.java)

        logger.info { "Retrieving conversations (channels)" }
        callCursorApi(
                URL_CONVO_LIST, adapter, params, RETRY_TIER_2
        ) { response ->
            // Add entries to map and output message
            response.channels.forEach {
                convos[it.id] = it
            }
            logger.debug { "Retrieved ${convos.size} conversations" }
        }

        logger.info { "Finished retrieving conversations (${convos.size} found)" }
        return convos.toMap()
    }

    /**
     * Returns a list of parsed files (which may be incomplete) in a time range
     */
    fun getFiles(startTime: Long? = null, endTime: Long? = null,
                 channel: String? = null, user: String? = null)
            : List<ParsedFile> {

        // Params setup
        val params = mutableMapOf(
                "page" to "1",
                "count" to FILE_LIST_LIMIT.toString()
        )
        startTime?.let { params["ts_from"] = it.toString() }
        endTime?.let { params["ts_to"] = it.toString() }
        channel?.let { params["channel"] = it }
        user?.let { params["user"] = it }

        val adapter = moshi.adapter(FileListResponse::class.java)

        // Get results
        logger.log(Log.HIGH) { "Retrieving list of files" }
        val files = mutableListOf<ParsedFile>()
        do {
            val response = (http.get(URL_FILES_LIST, adapter, params, RETRY_TIER_3) as Result.Success).value!!
            files.addAll(response.files)

            logger.info { "Retrieved ${files.size}/${response.paging.total} files (page ${response.paging.page}/${response.paging.pages})" }
        } while (response.updatePageParams(params))

        logger.log(Log.HIGH) { "Retrieved ${files.size} files" }
        return files
    }

    /**
     * Returns a file from id (files.info)
     * TODO make it a complete file
     */
    fun getFile(fileId: String) : ParsedFile {
        val params = mapOf(
                "file" to fileId)
        val adapter = moshi.adapter(OldFileResponse::class.java)
        val response = (http.get(URL_FILES_INFO, adapter, params, RETRY_TIER_4) as Result.Success).value!!

        return response.file
    }

    /**
     * Retrieves full list of users using Slack API
     * @return map of userid to user object
     */
    fun getUsers() : Map<String, User> {
        val userMap = mutableMapOf<String, User>()
        val params = mutableMapOf(
                "limit" to USERS_LIST_LIMIT.toString(),
                "cursor" to "")

        val adapter = moshi.adapter(UserListResponse::class.java)

        logger.info { "Retrieving user results" }
        callCursorApi(
                URL_USERS_LIST, adapter, params, RETRY_TIER_2
        ) { response ->
            // Add entries to map and output message
            response.members.forEach {
                userMap[it.id] = it
            }
            logger.debug { "Retrieved ${userMap.size} user results" }
        }

        logger.info { "Finished retrieving user results (${userMap.size} found)" }
        return userMap
    }

    /**
     * Calls an api method multiple times to go through all the results
     *
     * @param url Data for http.get
     * @param adapter Data for http.get
     * @param params Data for http.get
     * @param retry Data for http.get
     *
     * @param postRequest Function to be called with response after each individual API request
     */
    private fun <T : CursorResponse<*>> callCursorApi(
            // HTTP data
            url: String,
            adapter: JsonAdapter<T>,
            params: MutableMap<String, String>,
            retry: Int,

            // Processing data
            postRequest: (T) -> (Unit)) {

        do {
            // Get converted response
            val response = (http.get(url, adapter, params, retry) as Result.Success).value!!
            postRequest.invoke(response)

            // Check cursor
            if (!response.moreEntries()) {
                break
            } else {
                params["cursor"] = response.nextCursor()!!
            }
        } while (true)
    }
}