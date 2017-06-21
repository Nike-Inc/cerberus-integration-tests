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

import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.fieldju.commons.PropUtils.{getPropWithDefaultValue, getRequiredProperty}
import com.nike.cerberus.api.util.TestUtils
import com.nike.cerberus.api.CerberusApiActions
import io.gatling.core.Predef._
import io.gatling.core.structure._
import io.gatling.http.Predef.{http, jsonPath, status}
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.Random

class VaultDirectSimulation extends Simulation {

  // Sessions keys
  private val PATH = "path"

  private val generatedData = ArrayBuffer[Map[String, String]]()
  private var iam: AmazonIdentityManagementClient = _
  private var currentIamPrincipalArn: String = _

  ///////////////////////////////////////////////////////////////////////////////
  //  LOAD REQUIRED PROPS, These can be set via ENV vars or System properties  //
  ///////////////////////////////////////////////////////////////////////////////
  private val vaultAddr: String = getRequiredProperty("VAULT_ADDR", "The base Cerberus API URL")
  private val vaultToken: String = getRequiredProperty("VAULT_TOKEN")
  private val peakUsers = getPropWithDefaultValue("PEAK_USERS", "1").toInt
  private val holdTimeAfterPeakInMinutes = getPropWithDefaultValue("HOLD_TIME_AFTER_PEAK_IN_MINUTES", "1").toInt
  private val rampUpTimeInMinutes = getPropWithDefaultValue("RAMP_UP_TIME_IN_MINUTES", "1").toInt
  private val numberOfVaultNodesToCreate = getPropWithDefaultValue("NUMBER_OF_VAULT_NODES_TO_CREATE", "10").toInt
  private val numberOfRandomReadsPerAuth = getPropWithDefaultValue("NUMBER_OF_RANDOM_READS", "99").toInt
  private val tokenTtl = getPropWithDefaultValue("ORPHAN_TOKEN_TTL", "10m")

  before {
    println(
      s"""
         |
         |######################################################################
         |# Preparing to execute simulation, received the following parameters #
         |######################################################################
         |
         |   VAULT_ADDER: $vaultAddr
         |   NUMBER_OF_VAULT_NODES_TO_CREATE: $numberOfVaultNodesToCreate
         |   NUMBER_OF_RANDOM_READS: $numberOfRandomReadsPerAuth
         |   PEAK_USERS: $peakUsers
         |   RAMP_UP_TIME_IN_MINUTES:  $rampUpTimeInMinutes
         |   HOLD_TIME_AFTER_PEAK_IN_MINUTES: $holdTimeAfterPeakInMinutes
         |
         |######################################################################
         |
      """.stripMargin)

    System.setProperty("CERBERUS_API_URL", vaultAddr)
    TestUtils.configureRestAssured()

    for (_ <- 1 to numberOfVaultNodesToCreate) {
      try {
        val paths = VaultDataHelper.writeRandomData(vaultToken, s"vault-perf/${Random.alphanumeric.take(20).mkString}/")
        paths.foreach(path => {
          generatedData += Map(PATH -> path)
          println(s"Adding path $path to paths list")
        })
      } catch {
        case t: Throwable =>
          println(s"Failed to create Vault data")
          t.printStackTrace()
      }
    }
  }

  val httpConf: HttpProtocolBuilder = http.baseURL(vaultAddr)

  val scn: ScenarioBuilder =
    scenario("Iam principal authenticates and then reads secrets")
      .exec(
        http("create an orphan token")
          .post("/v1/auth/token/create-orphan")
          .header("X-Vault-Token", vaultToken)
          .queryParam("ttl", tokenTtl)
          .check(status.is(200))
          .check(
            jsonPath("$.auth.client_token").find.saveAs("auth_token")
          )
      ).exec(exitHereIfFailed)
      .repeat(numberOfRandomReadsPerAuth) {
        feed(generatedData.random)
        .exec(
          http("read node from vault")
            .get("/v1/secret/${path}")
            .header("X-Vault-Token", "${auth_token}")
            .check(status.is(200))
        ).exec(exitHereIfFailed)
      }

  /**
    * Set up the scenario
    */
  setUp(
    scn.inject(
      rampUsers(peakUsers) over(rampUpTimeInMinutes minutes),
      constantUsersPerSec(peakUsers) during(holdTimeAfterPeakInMinutes minutes)
    ).throttle(
      reachRps(peakUsers) in(rampUpTimeInMinutes minutes),
      holdFor(holdTimeAfterPeakInMinutes minutes)
    )
  ).protocols(httpConf)

  after {
    println(
      """
        |################################################################################
        |#                                                                              #
        |#                  Deleting generated performance data                         #
        |#                                                                              #
        |################################################################################
      """.stripMargin)

    for (data <- generatedData) {
      try {
        val path = data(PATH)
        println(s"Deleting path $path")
        CerberusApiActions.deleteSecretNode(path, vaultToken)
      } catch {
        case t: Throwable =>
          println(s"Failed to delete Vault data")
          t.printStackTrace()
      }
    }
  }
}
