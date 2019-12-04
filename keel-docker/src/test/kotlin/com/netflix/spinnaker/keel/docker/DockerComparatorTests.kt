/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.docker

import com.netflix.spinnaker.keel.docker.TagVersionStrategy.INCREASING
import com.netflix.spinnaker.keel.docker.TagVersionStrategy.SEMVER
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.failed
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class DockerComparatorTests : JUnit5Minutests {

  private val incrTags = listOf("1", "2", "3", "0")
  private val semVerTags = listOf("0.0.3", "0.1.3", "0.10.3", "0.4.1")
  private val semVerTagsWithV = listOf("v0.0.3", "v0.1.3", "v0.10.3", "v0.4.1")
  private val otherTags = listOf("master-h1.blah", "master-h2.blah", "master-h3.blah", "master-h0.blah")
  private val trickyOtherTags = listOf("3master-h1.blah", "1master-h2.blah", "2master-h3.blah", "5master-h0.blah")

  fun tests() = rootContext<Unit> {
    context("increasing tags") {
      test("comparing gets highest") {
        val sorted = DockerComparator.sort(incrTags, INCREASING, null)
        expectThat(sorted.first()).isEqualTo("3")
      }
    }

    context("semver tags") {
      test("leading v stripped") {
        val sorted = DockerComparator.sort(semVerTagsWithV, SEMVER, null)
        expectThat(sorted.first()).isEqualTo("v0.10.3")
      }

      test("plain numbers are ok") {
        val sorted = DockerComparator.sort(semVerTags, SEMVER, null)
        expectThat(sorted.first()).isEqualTo("0.10.3")
      }
    }

    context("tags with regex parsing needed") {
      test("finds highest by hversion") {
        val regex = """^master-h(\d+).*${'$'}"""
        val sorted = DockerComparator.sort(otherTags, INCREASING, regex)
        expectThat(sorted.first()).isEqualTo("master-h3.blah")
      }

      test("finds highest with confusing number in front") {
        val regex = """^\dmaster-h(\d+).*${'$'}"""
        val sorted = DockerComparator.sort(trickyOtherTags, INCREASING, regex)
        expectThat(sorted.first()).isEqualTo("2master-h3.blah")
      }

      test("ignores a bad tag") {
        val regex = """^\dmaster-h(\d+).*${'$'}"""
        val mixedTags = listOf("3master-h1.blah", "1master-h2.blah", "2master-h3.blah", "5master-h0.blah", "latest")
        val sorted = DockerComparator.sort(mixedTags, INCREASING, regex)
        expectThat(sorted.first()).isEqualTo("2master-h3.blah")
      }
    }

    context("regex parsing") {
      test("able to parse with capture group") {
        val tag = "master-h1.blah"
        val regex = """^master-h(\d+).*${'$'}"""
        val result = DockerComparator.parseTag(tag, regex)
        expectThat(result).isEqualTo("1")
      }

      test("too many captures throws exception") {
        val tag = "master-h1.blah"
        val regex = """^master-h(\d+)(.*)${'$'}"""
        expectCatching { DockerComparator.parseTag(tag, regex) }
          .failed()
          .isA<InvalidRegexException>()
      }

      test("no match returns null") {
        val tag = "v001"
        val regex = """^master-h(\d+).*${'$'}"""
        val result = DockerComparator.parseTag(tag, regex)
        expectThat(result).isNull()
      }
    }
  }
}
