@file:Suppress("MemberVisibilityCanBePrivate")

package slack

import scripts.DownloadStats
import slackjson.*
import utils.Log
import utils.ensureFolderExists
import java.nio.file.Path

abstract class SlackData(val settings: Settings) {
    // Constants
    private val LOCATION_INTERVAL = 3000

    // Raw data
    abstract val conversations: Map<String, Conversation>
    abstract val filesParsed: List<ParsedFile>
    abstract val users: Map<String, User>

    // Responses may need further processing
    /**
     * Map of file id to complete files
     */
    val filesComplete by lazy {
        // Get files parsed first so we do things 'in order'
        val filesParsed = filesParsed

        Log.high("Locating upload location of files (this may take a while, especially if inference is disabled)")
        val startTime = System.currentTimeMillis()
        var nextOutputTime = startTime + LOCATION_INTERVAL

        // Iterate over objects, create map of file id to file objects
        val files = mutableMapOf<String, CompleteFile>()
        for ((index, obj) in filesParsed.withIndex()) {
            // Cast file and add to list
            val cf = CompleteFile(obj, true) // TODO fix this !FileCommand.noInfer)
            files[cf.id] = cf

            // Print out how many objects have been processed
            if (System.currentTimeMillis() > nextOutputTime) {
                Log.medium("Processed ${index + 1}/${files.size} files")
                nextOutputTime = System.currentTimeMillis() + LOCATION_INTERVAL
            }
        }

        // Output timed messages if took more than LOCATION_INTERVAL
        val timeTaken = System.currentTimeMillis() - startTime
        if (timeTaken > LOCATION_INTERVAL) {
            Log.high(String.format("Located the upload location of all files in %,.1f seconds", timeTaken.toFloat() / 1000))
        } else {
            Log.medium("Files located")
        }

        return@lazy files
    }

    /**
     * Map of conversation id --> list of files
     */
    val filesByConvo by lazy {
        val filesConvo = mutableMapOf<String?, MutableList<CompleteFile>>()

        filesComplete.values.forEach {
            val uploadLoc = it.uploadLoc // Key will be null if we don't know the convo
            filesConvo.getOrPut(uploadLoc) { mutableListOf() }
                    .add(it)
        }

        return@lazy filesConvo.toMap()
    }

    // Data retrieval methods
    fun getUsername(userId: String?) = users[userId]?.name ?: "Unknown user"
    fun getConversationName(convoId: String?) = conversations[convoId]?.getFullName(this) ?: "Unknown conversation"

    // Download methods
    fun downloadFiles(outDir: Path) {
        // Process conversations alphabetically
        Log.high("Downloading files")
        var downloadStats = DownloadStats()
        filesByConvo.keys.sortedBy { getConversationName(it) }.forEach { convoID ->
            val filesInConvo = filesByConvo[convoID]!!

            // Get location to put files in
            val convoName = getConversationName(convoID)
            val convoFolder = outDir.resolve(convoName)

            // Create folder if it doesn't exist
            ensureFolderExists(convoFolder)

            // Download files
            val channelStats = DownloadStats()
            Log.medium("Downloading ${filesInConvo.size} files from $convoName")
            filesInConvo.sortedBy { it.timestamp }.forEach { file ->
                channelStats.update(file.download(convoFolder, this))
            }

            channelStats.log(convoName)
            downloadStats += channelStats
        }

        downloadStats.log("slack")
    }
}

