package com.nike.cerberus

import com.fieldju.commons.PropUtils._

import io.gatling.core.Predef._
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import scala.concurrent.duration._

/**
  * Simulation that hits the gateway healthcheck
  */
class GatewayHealthcheckSimulation extends Simulation {

  ///////////////////////////////////////////////////////////////////////////////
  //  LOAD REQUIRED PROPS, These can be set via ENV vars or System properties  //
  ///////////////////////////////////////////////////////////////////////////////
  private val cerberusBaseUrl: String = getRequiredProperty("CERBERUS_API_URL", "The base Cerberus API URL")
  private val peakUsers = getPropWithDefaultValue("PEAK_USERS", "200").toInt
  private val holdTimeAfterPeakInMinutes = getPropWithDefaultValue("HOLD_TIME_AFTER_PEAK_IN_MINUTES", "1").toInt
  private val rampUpTimeInMinutes = getPropWithDefaultValue("RAMP_UP_TIME_IN_MINUTES", "1").toInt

  before {
    println(
      s"""
        |
        |######################################################################
        |# Preparing to execute simulation, received the following parameters #
        |######################################################################
        |
        |   CERBERUS_API_URL: $cerberusBaseUrl
        |   PEAK_USERS: $peakUsers
        |   RAMP_UP_TIME_IN_MINUTES:  $rampUpTimeInMinutes
        |   HOLD_TIME_AFTER_PEAK_IN_MINUTES: $holdTimeAfterPeakInMinutes
        |
        |######################################################################
        |
      """.stripMargin)
  }

  val httpConf: HttpProtocolBuilder = http.baseURL(cerberusBaseUrl)
                                          .shareConnections
                                          .maxConnectionsPerHost(1000)

  val gateway_health_check: ChainBuilder = exec(
    http("gateway health check")
      .get("/sys/health")
      .check(status.is(200))
  ).exec(exitHereIfFailed)

  val scn: ScenarioBuilder =
    scenario("Gateway healthcheck")
    .exec(
      gateway_health_check
    )

  /**
    * Set up the scenario
    */
  setUp(
    scn.inject(
      rampUsers(peakUsers) over(rampUpTimeInMinutes minutes),
      constantUsersPerSec(peakUsers) during(holdTimeAfterPeakInMinutes minutes)
    )
  ).protocols(httpConf)

}
