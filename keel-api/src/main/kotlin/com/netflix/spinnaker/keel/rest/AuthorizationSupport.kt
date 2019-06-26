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
package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class AuthorizationSupport(
  private val permissionEvaluator: FiatPermissionEvaluator,
  private val resourceRepository: ResourceRepository
) {
  fun userCanModifyResource(name: String): Boolean {
    try {
      val resource = resourceRepository.get(ResourceName(name), Any::class.java)
      return userCanModifySpec(resource.serviceAccount)
    } catch (e: NoSuchResourceException) {
      // todo eb: should we say "unauthorized" if the resource doesn't exist?
      // or, should we let someone see that it doesn't exist, because no action would happen?
      return false
    }
  }

  fun userCanModifySpec(serviceAccount: String): Boolean {
    val auth = SecurityContextHolder.getContext().authentication
    return userCanAccessServiceAccount(auth, serviceAccount)
  }

  fun userCanAccessServiceAccount(auth: Authentication, serviceAccount: String): Boolean =
    permissionEvaluator.hasPermission(auth, serviceAccount, "SERVICE_ACCOUNT", "ignored-svcAcct-auth")
}
