/*
 * Copyright 2017 Netflix, Inc.
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
 */
plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api("com.squareup.retrofit2:retrofit")
  api("com.squareup.retrofit2:converter-jackson")
  api("com.jakewharton.retrofit:retrofit2-kotlin-coroutines-adapter:0.9.2")

  implementation("com.netflix.spinnaker.kork:kork-web")
  implementation("com.netflix.spinnaker.kork:kork-security")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("com.squareup.okhttp3:logging-interceptor")
}
