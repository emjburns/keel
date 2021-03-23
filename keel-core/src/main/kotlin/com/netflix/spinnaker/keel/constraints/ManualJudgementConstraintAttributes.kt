package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes

data class ManualJudgementConstraintAttributes(
  val slackDetails: List<SlackMessageDetail> = emptyList()
) : ConstraintStateAttributes("manual-judgement")

data class SlackMessageDetail(
  val timestamp: String,
  val channel: String,
  val message: Map<String, Any?> // store the message info as a map so we can keep the slack dependency to one module.
)
