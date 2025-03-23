package ua.mcausc78.rnsucks.summaries

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.api.GatewayAPI
import com.aliucord.entities.Plugin
import com.aliucord.utils.SerializedName
import com.aliucord.wrappers.ChannelWrapper.Companion.name
import com.discord.stores.StoreStream

data class Summary(
    val id: Long,
    @SerializedName("end_id")
    val endId: Long,
    val count: Int,
    @SerializedName("message_ids")
    val messageIds: List<Long>,
    val people: List<Long>,
    val source: Int,
    @SerializedName("start_id")
    val startId: Long,
    @SerializedName("summ_short")
    val summShort: String,
    val topic: String,
    val type: Int,
    val unsafe: Boolean,
)

data class ConversationSummaryUpdate(
    @SerializedName("channel_id")
    val channelId: Long,
    @SerializedName("guild_id")
    val guildId: Long,
    val summaries: List<Summary>,
)

@AliucordPlugin(requiresRestart = false)
class SummariesPlugin : Plugin() {
    private val summaries: MutableMap<Long, MutableList<Summary>> = mutableMapOf()

    override fun start(context: Context) {
        GatewayAPI.onEvent<ConversationSummaryUpdate>("CONVERSATION_SUMMARY_UPDATE") { event ->
            val channelId = event.channelId
            val guildId = event.guildId

            val channel = StoreStream.getChannels().getChannel(channelId)
            if (channel == null) {
                logger.warn("CONVERSATION_SUMMARY_UPDATE referencing an unknown channel ID: $channelId. Updating the cache anyway.")
            }

            val guild = StoreStream.getGuilds().getGuild(guildId)

            val channelName = channel?.name ?: "?"
            val guildName = guild?.name ?: "?"

            if (event.summaries.isEmpty()) {
                logger.verbose("No summaries were added in $channelName (ID: $channelId) at $guildName (ID: $guildId)")
            } else {
                logger.verbose("Summaries updated in $channelName (ID: $channelId) at $guildName (ID: $guildId)")
            }

            val summaries = this.summaries[channelId] ?: mutableListOf()
            val summaryIds = summaries.map { it.id }

            for (summary in event.summaries) {
                val index = summaryIds.indexOf(summary.id)
                if (index == -1) {
                    // new summary
                    summaries.add(summary)
                } else {
                    // update summary
                    summaries[index] = summary
                }
            }

            summaries.sortBy { it.id }
            this.summaries[channelId] = summaries
        }

        commands.registerCommand("summaries", "Shows current summaries") { ctx ->
            val summaries = this.summaries[ctx.currentChannel.id]
            if (summaries.isNullOrEmpty()) {
                return@registerCommand CommandsAPI.CommandResult(
                    "No summaries in channel currently.",
                    null,
                    false
                )
            }
            val result = summaries.joinToString(separator = "\n") { summary ->
                "- **${summary.topic}**: ${summary.summShort} (${summary.messageIds.size} messages and ${summary.people.size} people involved)"
            }
            CommandsAPI.CommandResult(result, null, false)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }
}
