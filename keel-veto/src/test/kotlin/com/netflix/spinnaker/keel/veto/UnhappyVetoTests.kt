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
package com.netflix.spinnaker.keel.veto

import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNHAPPY
import com.netflix.spinnaker.keel.persistence.memory.InMemoryUnhappyVetoRepository
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.time.Duration

class UnhappyVetoTests : JUnit5Minutests {
  val r = resource()

  internal class Fixture {
    val clock = MutableClock()
    val unhappyRepository = InMemoryUnhappyVetoRepository(clock)
    val resourceRepository: ResourceRepository = mockk()
    val subject = UnhappyVeto(resourceRepository, unhappyRepository)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after {
      unhappyRepository.flush()
    }

    context("resource is happy") {
      before {
        every { resourceRepository.getStatus(r.id) } returns ResourceStatus.HAPPY
      }

      test("happy resources aren't vetoed") {
        expectThat(subject.check(r).allowed).isEqualTo(true)
      }
    }

    context("resource is unhappy") {
      before {
        every { resourceRepository.getStatus(r.id) } returns UNHAPPY
      }

      test("unhappy resources are vetoed") {
        expectThat(subject.check(r).allowed).isEqualTo(false)
      }

      test("resources are checked once every wait time") {
        unhappyRepository.markUnhappy(r.id, r.spec.application)

        val response1 = subject.check(r)
        clock.incrementBy(Duration.ofMinutes(11))
        val response2 = subject.check(r)
        clock.incrementBy(Duration.ofMinutes(3))
        val response3 = subject.check(r)

        expect {
          that(response1.allowed).isFalse()
          that(response2.allowed).isTrue()
          that(response3.allowed).isFalse()
        }
      }

      test("a happy resource should no longer be skipped ") {
        val response1 = subject.check(r) // unhappy, so vetoed
        clock.incrementBy(Duration.ofMinutes(11))

        // returnsMany seems to not work for enums, so this is a workaround.
        every { resourceRepository.getStatus(r.id) } returns ResourceStatus.HAPPY

        val response2 = subject.check(r) // rechecked, and it's happy now
        expect {
          that(response1.allowed).isEqualTo(false)
          that(response2.allowed).isEqualTo(true)
        }
      }
    }
  }
}