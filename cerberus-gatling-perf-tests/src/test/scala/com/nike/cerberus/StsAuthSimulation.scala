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

import java.util.regex.Pattern

import com.amazonaws.AmazonClientException
import com.amazonaws.auth.policy.Statement.Effect
import com.amazonaws.auth.policy.actions.{KMSActions, SecurityTokenServiceActions}
import com.amazonaws.auth.policy.{Policy, Principal, Resource, Statement}
import com.amazonaws.regions.Regions
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model._
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.fieldju.commons.PropUtils._
import com.fieldju.commons.StringUtils
import com.nike.cerberus.CerberusGatlingApiActions._
import com.nike.cerberus.api.CerberusApiActions
import com.nike.cerberus.api.util.TestUtils
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.restassured.path.json.JsonPath

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.Breaks._
import scala.util.control.NonFatal

/**
  * Simulation that will create a bunch of SDBs with random secrets and IAM Principals will authenticate and read secrets
  */
class StsAuthSimulation extends Simulation {

  private val MAX_COOL_DOWN = 1 minutes

  ///////////////////////////////////////////////////////////////////////////////
  //  LOAD REQUIRED PROPS, These can be set via ENV vars or System properties  //
  ///////////////////////////////////////////////////////////////////////////////
  private val cerberusBaseUrl: String = getRequiredProperty("CERBERUS_API_URL", "The base Cerberus API URL")

  before {
    println(
      s"""
        |
        |######################################################################
        |# Preparing to execute simulation, received the following parameters #
        |######################################################################
        |
        |   CERBERUS_API_URL: $cerberusBaseUrl
        |
        |######################################################################
        |
      """.stripMargin)

    TestUtils.configureRestAssured()
  }

  val httpConf: HttpProtocolBuilder = http.baseURL(cerberusBaseUrl)
                                          .shareConnections
                                          .maxConnectionsPerHost(1000)

  val scn: ScenarioBuilder =
    scenario("Get current IAM principal identity")
    .exec(
      sts_auth
    )

  /**
    * Set up the scenario
    */
  setUp(
    scn.inject(
//      rampUsersPerSec(1) to peakUsers / numberOfLoadServers during(rampUpTimeInMinutes minutes),
      constantUsersPerSec(1) during(1 second)
    )
  )
    .protocols(httpConf)
    // Short Circuit Tests if they are running longer than expected.
//    .maxDuration((rampUpTimeInMinutes + holdTimeAfterPeakInMinutes minutes) + MAX_COOL_DOWN)

  /**
    * After the simulation is run, delete the randomly created data.
    */
  after {
    println(
      s"""
         |
         |######################################################################
         |# Test was ran with the following parameters                         #
         |######################################################################
         |
         |   CERBERUS_API_URL: $cerberusBaseUrl
         |
        |######################################################################
         |
      """.stripMargin)
  }
}
