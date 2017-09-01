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

import com.fieldju.commons.PropUtils.getPropWithDefaultValue
import com.nike.cerberus.api.CerberusApiActions

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

object VaultDataHelper {

  def writeRandomData(token: String, basePath: String): List[String] = {

    val paths = new ListBuffer[String]()

    ////////////////////////////
    // DATA GENERATION CONTROLS
    ////////////////////////////
    // controls the number of nodes (paths in the storage structure that point to maps of data) to create for a given simulated services SDB
    val minNodesToCreate: Int = getPropWithDefaultValue("minNodesToCreate", "1").toInt
    val maxNodesToCreate: Int = getPropWithDefaultValue("maxNodesToCreate", "1").toInt
    // controls the path suffix
    val minPathSuffixLength: Int = getPropWithDefaultValue("minPathSuffixLength", "7").toInt
    val maxPathSuffixLength: Int = getPropWithDefaultValue("maxPathSuffixLength", "8").toInt
    // how many k,v pairs at each node
    val minKeyValuePairsPerNode: Int = getPropWithDefaultValue("minKeyValuePairsPerNode", "7").toInt
    val maxKeyValuePairsPerNode: Int = getPropWithDefaultValue("maxKeyValuePairsPerNode", "8").toInt
    // key length
    val minKeyLength: Int = getPropWithDefaultValue("minKeyLength", "7").toInt
    val maxKeyLength: Int = getPropWithDefaultValue("maxKeyLength", "8").toInt
    // value length
    val minValueLength: Int = getPropWithDefaultValue("minValueLength", "49").toInt
    val maxValueLength: Int = getPropWithDefaultValue("maxValueLength", "50").toInt

    println(
      s"""
         |
         |######################################################################
         |# VaultDataHelper settings                                           #
         |######################################################################
         |
         |   minNodesToCreate: $minNodesToCreate
         |   maxNodesToCreate: $maxNodesToCreate
         |   minPathSuffixLength: $minPathSuffixLength
         |   maxPathSuffixLength: $maxPathSuffixLength
         |   minKeyValuePairsPerNode: $minKeyValuePairsPerNode
         |   maxKeyValuePairsPerNode: $maxKeyValuePairsPerNode
         |   minKeyLength: $minKeyLength
         |   maxKeyLength: $maxKeyLength
         |   minValueLength: $minValueLength
         |   maxValueLength: $maxValueLength
         |
         |######################################################################
         |
      """.stripMargin)

    val numberOfNodesToCreate = if (minNodesToCreate == maxNodesToCreate) {
      minNodesToCreate
    }
    else {
      scala.util.Random.nextInt(maxNodesToCreate - minNodesToCreate) + minNodesToCreate
    }
    for (_ <- 1 to numberOfNodesToCreate) {
      val pathSuffix = Random.alphanumeric.take(scala.util.Random.nextInt(maxPathSuffixLength - minPathSuffixLength) + minPathSuffixLength).mkString
      val numberOfKeyValuePairsToCreate = scala.util.Random.nextInt(maxKeyValuePairsPerNode - minKeyValuePairsPerNode) + minKeyValuePairsPerNode
      var data = mutable.HashMap[String, String]()
      for (_ <- 0 to numberOfKeyValuePairsToCreate) {
        val key: String = Random.alphanumeric.take(scala.util.Random.nextInt(maxKeyLength - minKeyLength) + minKeyLength).mkString
        val value: String = Random.alphanumeric.take(scala.util.Random.nextInt(maxValueLength - minValueLength) + minValueLength).mkString
        data += (key -> value)
      }

      val path = s"$basePath$pathSuffix"
      println(
        s"""
          |################################################################################
          |# Writing data to Vault on Path $path
          |################################################################################
        """.stripMargin
      )
      CerberusApiActions.writeSecretData(data.asJava, path, token)
      paths += path
    }

    //noinspection RemoveRedundantReturn
    return paths.toList
  }
}
