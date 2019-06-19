package scripts

import slack.Settings
import slack.SlackWebApi
import slack.toCompleteFiles
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

fun main(args: Array<String>) {
    // Basic setup
    val token = args[0]
    val settings = Settings()
    val slack = SlackWebApi(token, settings)

    // TODO
    println(Instant.now().minusSeconds(86_400).epochSecond)

    val parsedFiles = slack.api.getFiles(startTime = Instant.now().minusSeconds(86_400).epochSecond)
    val completeFiles = parsedFiles.toCompleteFiles(slack)

    // slack.downloadFiles(Paths.get("files"))
}