/*
 * Copyright (c) 2017 Nike Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nike.cerberus

import com.fieldju.commons.PropUtils.getRequiredProperty
import io.gatling.core.Predef._
import io.gatling.core.structure._
import io.gatling.http.Predef.{http, status}
import io.gatling.http.protocol.HttpProtocolBuilder

/**
  * This is a dummy simulation that just hits the Vault health check which can be used
  * to verify basic connectivity before running real performance tests.
  */
class VaultDirectHealthCheckSimulation extends Simulation {

  ///////////////////////////////////////////////////////////////////////////////
  //  LOAD REQUIRED PROPS, These can be set via ENV vars or System properties  //
  ///////////////////////////////////////////////////////////////////////////////
  private val vaultAddr: String = getRequiredProperty("VAULT_ADDR", "The base Cerberus API URL")

  before {
    println(
      s"""
         |
         |######################################################################
         |# Preparing to execute simulation, received the following parameters #
         |######################################################################
         |
         |   VAULT_ADDER: $vaultAddr
         |
         |######################################################################
         |
      """.stripMargin)

  }

  val httpConf: HttpProtocolBuilder = http.baseURL(vaultAddr)

  val scn: ScenarioBuilder =
    scenario("VaultDirectHealthCheckSimulation: simply hit the Vault health check to verify connnectivity")
      .exec(
        http("vault health check")
          .get("/v1/sys/health?standbyok")
          .check(status.is(200))
      )

  /**
    * Set up the scenario
    */
  setUp(
    scn.inject(
      atOnceUsers(3)
    )
  ).protocols(httpConf)

}
