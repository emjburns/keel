package com.netflix.spinnaker.keel.jackson.mixins

import com.fasterxml.jackson.annotation.JacksonInject
import com.netflix.spinnaker.keel.api.Locations

interface LocatableMixin<T : Locations<*>> {
  @get:JacksonInject("locations")
  val locations: T
}
