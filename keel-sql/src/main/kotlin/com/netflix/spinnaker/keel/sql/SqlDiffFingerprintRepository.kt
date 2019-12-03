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
package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DIFF_FINGERPRINT
import org.jooq.DSLContext
import java.time.Clock

class SqlDiffFingerprintRepository(
  private val jooq: DSLContext,
  private val clock: Clock
) : DiffFingerprintRepository {
  override fun store(resourceId: ResourceId, diff: ResourceDiff<*>) {
    val hash = diff.generateHash()
    jooq
      .select(DIFF_FINGERPRINT.COUNT, DIFF_FINGERPRINT.FIRST_DETECTION_TIME)
      .from(DIFF_FINGERPRINT)
      .where(DIFF_FINGERPRINT.RESOURCE_ID.eq(resourceId.toString()))
      .and(DIFF_FINGERPRINT.HASH.eq(hash))
      .forUpdate()
      .fetchOne()
      ?.let { (count, firstDetectionTime) ->
        jooq.insertInto(DIFF_FINGERPRINT)
          .set(DIFF_FINGERPRINT.RESOURCE_ID, resourceId.toString())
          .set(DIFF_FINGERPRINT.COUNT, count + 1)
          .set(DIFF_FINGERPRINT.HASH, hash)
          .set(DIFF_FINGERPRINT.FIRST_DETECTION_TIME, firstDetectionTime)
          .onDuplicateKeyUpdate()
          .set(DIFF_FINGERPRINT.COUNT, count + 1)
          .execute()
        return
      }

    jooq.insertInto(DIFF_FINGERPRINT)
      .set(DIFF_FINGERPRINT.RESOURCE_ID, resourceId.toString())
      .set(DIFF_FINGERPRINT.HASH, hash)
      .set(DIFF_FINGERPRINT.COUNT, 1)
      .set(DIFF_FINGERPRINT.FIRST_DETECTION_TIME, clock.instant().toEpochMilli())
      .onDuplicateKeyUpdate()
      .set(DIFF_FINGERPRINT.HASH, hash)
      .set(DIFF_FINGERPRINT.COUNT, 1)
      .set(DIFF_FINGERPRINT.FIRST_DETECTION_TIME, clock.instant().toEpochMilli())
      .execute()
  }

  override fun diffCount(resourceId: ResourceId): Int {
    val count = jooq
      .select(DIFF_FINGERPRINT.COUNT)
      .from(DIFF_FINGERPRINT)
      .where(DIFF_FINGERPRINT.RESOURCE_ID.eq(resourceId.toString()))
      .fetchOne()
      ?.let { (count) ->
        count
      }

    return count ?: 0
  }
}