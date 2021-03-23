package com.netflix.spinnaker.keel.slack.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.notifications.NotificationType
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.slack.SlackManualJudgmentUpdateNotification
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.kotlin_extension.block.withBlocks
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Updates manual judgement notifications that were sent when they
 * were judged from the api
 */
@Component
class ManualJudgementUpdateHandler(
  private val slackService: SlackService,
  private val repository: KeelRepository,
  private val mapper: ObjectMapper,
  private val clock: Clock
): SlackNotificationHandler<SlackManualJudgmentUpdateNotification> {
  override val supportedTypes = listOf(NotificationType.MANUAL_JUDGMENT_AWAIT, NotificationType.MANUAL_JUDGMENT_UPDATE)
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun sendMessage(notification: SlackManualJudgmentUpdateNotification, channel: String) {
    log.debug("Updating manual judgment await notification for application ${notification.application} sent at ${notification.timestamp}")

    //todo eb: refactor to be able to use from both update places.
    with(notification) {
      val originalCommitText = (message.blocks[1] as SectionBlock).text.toString()

      val updatedBlocks = withBlocks {
        header {
          when {
            status.passes() -> text("Manual judgement approved", emoji = true)
            status.failed() -> text("Manual judgement rejected", emoji = true)
          }
        }
        section {
          markdownText(originalCommitText)
          accessory {
            when {
              status.passes() -> image("https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/mj_was_approved.png", altText = "mj_approved")
              status.failed() -> image("https://raw.githubusercontent.com/spinnaker/spinnaker.github.io/master/assets/images/md_icons/mj_was_rejected.png", altText = "mj_rejected")
            }
          }
        }
      }
      val backuptext = fallbackText(user, status)

      val newFooterBlock = withBlocks {
        context {
          elements {
            markdownText(backuptext)
          }
        }
      }

      val originalBlocks = message.blocks
      //remove the first two blocks because we're replacing them
      originalBlocks.removeFirstOrNull()
      originalBlocks.removeFirstOrNull()
      originalBlocks.removeLast() // removes mj buttons
      val newBlocks = updatedBlocks + originalBlocks + newFooterBlock

      slackService.updateSlackMessage(notification.channel, timestamp, newBlocks, backuptext, application)
    }
  }

  //todo eb: user is probably wrong here, and we need to handle nullable
  fun fallbackText(user: String?, status: ConstraintStatus): String {
    val action = if (status.passes()) {
      "approved"
    } else {
      "rejected"
    }
    val emoji = if (status.passes()) {
      ":white_check_mark:"
    } else {
      ":x:"
    }
    return "@${user} hit " +
      "$emoji $action on <!date^${clock.instant().epochSecond}^{date_num} {time_secs}|fallback-text-include-PST>"
  }
}
