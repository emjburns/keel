/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.intent.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.intent.AmazonSecurityGroupSpec
import com.netflix.spinnaker.keel.intent.ApplicationIntent
import com.netflix.spinnaker.keel.intent.BaseApplicationSpec
import com.netflix.spinnaker.keel.intent.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.intent.SecurityGroupIntent
import com.netflix.spinnaker.keel.intent.SecurityGroupSpec
import com.netflix.spinnaker.keel.intent.processor.converter.SecurityGroupConverter
import com.netflix.spinnaker.keel.tracing.TraceRepository
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import retrofit.RetrofitError
import retrofit.client.Response

object SecurityGroupIntentProcessorTest {

  val traceRepository = mock<TraceRepository>()
  val clouddriverCache = mock<CloudDriverCache>()
  val clouddriverService = mock<CloudDriverService>().apply {
    whenever(listNetworks()) doReturn mapOf(
      "aws" to setOf(
        Network("aws", "vpc-1", "vpcName", "test", "us-west-2"),
        Network("aws", "vpc-2", "vpcName", "prod", "us-west-2"),
        Network("aws", "vpc-3", "vpcName", "test", "us-east-1"),
        Network("aws", "vpc-4", "vpcName", "test", "eu-west-1"),
        Network("aws", "vpc-5", "otherName", "test", "us-west-2")
      )
    )
  }
  val objectMapper = ObjectMapper()
  val converter = SecurityGroupConverter(clouddriverCache, objectMapper)

  val subject = SecurityGroupIntentProcessor(traceRepository, clouddriverService, clouddriverCache, objectMapper, converter)

  @AfterEach
  fun cleanup() {
    reset(traceRepository, clouddriverService)
  }

  @Test
  fun `should support SecurityGroupIntents`() {
    subject.supports(ApplicationIntent(mock<BaseApplicationSpec>())) shouldMatch equalTo(false)
    subject.supports(SecurityGroupIntent(mock<SecurityGroupSpec>())) shouldMatch equalTo(true)
  }

  @Test
  fun `should upsert security group when missing`() {
    whenever(clouddriverService.getSecurityGroup(any(), any(), any(), any(), any())) doReturn null as SecurityGroup?

    whenever(clouddriverCache.networkBy(any(), any(), any())) doReturn
      Network(
        cloudProvider = "aws",
        id = "vpc-1",
        name = "vpcName",
        account = "test",
        region = "us-west-2"
      )

    val intent = SecurityGroupIntent(AmazonSecurityGroupSpec(
      application = "keel",
      name = "keel",
      cloudProvider = "aws",
      accountName = "test",
      regions = setOf("us-west-2"),
      inboundRules = emptySet(),
      outboundRules = emptySet(),
      vpcName = "vpcName",
      description = "app sg"
    ))

    val result = subject.converge(intent)

    result.orchestrations.size shouldMatch equalTo(1)
    result.orchestrations[0].name shouldMatch equalTo("Upsert security group")
    result.orchestrations[0].application shouldMatch equalTo("keel")
    result.orchestrations[0].job[0]["type"] shouldMatch equalTo<Any>("upsertSecurityGroup")
  }

  @Test
  fun `should upsert security group when present`() {
    whenever(clouddriverService.getSecurityGroup(any(), any(), any(), any(), any())) doReturn
      SecurityGroup(
        type = "aws",
        id = "sg-1234",
        name = "keel",
        description = "app sg",
        accountName = "test",
        region = "us-west-2",
        vpcId = "vpcName",
        inboundRules = emptyList(),
        moniker = Moniker("test")
      )
    whenever(clouddriverCache.networkBy(any(), any(), any())) doReturn
      Network(
        cloudProvider = "aws",
        id = "vpc-1",
        name = "vpcName",
        account = "test",
        region = "us-west-2"
      )

    val intent = SecurityGroupIntent(AmazonSecurityGroupSpec(
      application = "keel",
      name = "keel",
      cloudProvider = "aws",
      accountName = "test",
      regions = setOf("us-west-2"),
      inboundRules = emptySet(),
      outboundRules = emptySet(),
      vpcName = "vpcName",
      description = "app sg"
    ))

    val result = subject.converge(intent)

    result.orchestrations.size shouldMatch equalTo(1)
    result.orchestrations[0].name shouldMatch equalTo("Upsert security group")
    result.orchestrations[0].application shouldMatch equalTo("keel")
    result.orchestrations[0].job[0]["type"] shouldMatch equalTo<Any>("upsertSecurityGroup")
  }

  @Test
  fun `should skip operation if upstream groups are missing`() {
    val changeSummary = ChangeSummary("myIntentId")
    changeSummary.type = ChangeType.FAILED_PRECONDITIONS
    changeSummary.addMessage("Some upstream security groups are missing: [gate]")

    whenever(clouddriverService.getSecurityGroup("test", "aws", "gate", "us-west-2")) doThrow RetrofitError.httpError(
      "http://example.com",
      Response("http://example.com", 404, "Not Found", listOf(), null),
      null,
      null
    )
    whenever(clouddriverCache.networkBy(any(), any(), any())) doReturn
      Network(
        cloudProvider = "aws",
        id = "vpc-1",
        name = "vpcName",
        account = "test",
        region = "us-west-2"
      )

    val intent = SecurityGroupIntent(AmazonSecurityGroupSpec(
      application = "keel",
      name = "keel",
      cloudProvider = "aws",
      accountName = "test",
      regions = setOf("us-west-2"),
      inboundRules = setOf(ReferenceSecurityGroupRule(sortedSetOf(), "tcp", "gate")),
      outboundRules = emptySet(),
      vpcName = "vpcName",
      description = "app sg"
    ))

    val result = subject.converge(intent)

    result.orchestrations shouldMatch isEmpty
    result.changeSummary.toString() shouldEqual changeSummary.toString()
  }
}
