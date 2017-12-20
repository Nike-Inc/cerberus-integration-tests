package com.nike.cerberus.api

import com.fieldju.commons.PropUtils
import com.nike.cerberus.api.util.TestUtils
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpStatus
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import static com.nike.cerberus.api.CerberusApiActions.*
import static com.nike.cerberus.api.CerberusCompositeApiActions.getNEGATIVE_JSON_SCHEMA_ROOT_PATH
import static com.nike.cerberus.api.CerberusCompositeApiActions.getROOT_INTEGRATION_TEST_SDB_PATH
import static com.nike.cerberus.api.util.TestUtils.generateRandomSdbDescription
import static com.nike.cerberus.api.util.TestUtils.generateSdbJson

class ValidationErrorApiTests {

    private String accountId
    private String roleName
    private String region
    private String iamAuthToken

    private def testSdb

    private void loadRequiredEnvVars() {
        accountId = PropUtils.getRequiredProperty("TEST_ACCOUNT_ID",
                "The account id to use when authenticating with Cerberus using the IAM Auth endpoint")

        roleName = PropUtils.getRequiredProperty("TEST_ROLE_NAME",
                "The role name to use when authenticating with Cerberus using the IAM Auth endpoint")

        region = PropUtils.getRequiredProperty("TEST_REGION",
                "The region to use when authenticating with Cerberus using the IAM Auth endpoint")
    }

    @BeforeTest
    void beforeTest() {
        TestUtils.configureRestAssured()
        loadRequiredEnvVars()
        String iamPrincipalArn = "arn:aws:iam::${accountId}:role/${roleName}"
        def iamAuthData = retrieveIamAuthToken(iamPrincipalArn, region)
        iamAuthToken = iamAuthData."client_token"

        String sdbCategoryId = getCategoryMap(iamAuthToken).Applications
        String sdbDescription = generateRandomSdbDescription()
        String ownerRoleId = getRoleMap(iamAuthToken).owner
        def iamPrincipalPermissions = [["iam_principal_arn": iamPrincipalArn, "role_id": ownerRoleId]]

        testSdb = createSdbV2(iamAuthToken, TestUtils.generateRandomSdbName(), sdbDescription, sdbCategoryId, iamPrincipalArn, [], iamPrincipalPermissions)

        // regenerate token to get policy for new SDB
        iamAuthToken = retrieveIamAuthToken(iamPrincipalArn, region)."client_token"
    }

    @AfterTest
    void afterTest() {
        String testSdbId = testSdb.getString("id")
        deleteSdb(iamAuthToken, testSdbId, V2_SAFE_DEPOSIT_BOX_PATH)

        deleteAuthToken(iamAuthToken)
    }

    @Test
    void "test that list secrets returns 404 when there are no nodes under path"() {
        String sdbPath = testSdb.getString("path")
        sdbPath = StringUtils.substringBeforeLast(sdbPath, "/")

        def listSecretsUri = "$SECRETS_PATH/$sdbPath/?list=true"
        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/secret-not-found-error.json"

        // list secrets
        validateGETApiResponse(AUTH_TOKEN_HEADER_NAME, iamAuthToken, listSecretsUri, HttpStatus.SC_NOT_FOUND, schemaFilePath)
    }

    @Test
    void "test that a 404 is returned when list is called on a secret instead of a folder"() {
        String sdbPath = testSdb.getString("path")
        sdbPath = StringUtils.substringBeforeLast(sdbPath, "/")
        String secretName = UUID.randomUUID().toString()

        def newSecretPath = "$sdbPath/$secretName"
        def listSecretsUri = "$SECRETS_PATH/$ROOT_INTEGRATION_TEST_SDB_PATH/$secretName/?list=true"
        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/secret-not-found-error.json"

        // create secret to call list on
        createOrUpdateSecretNode([key: "key", value: "value"], newSecretPath, iamAuthToken)
        // list secrets
        validateGETApiResponse(AUTH_TOKEN_HEADER_NAME, iamAuthToken, listSecretsUri, HttpStatus.SC_NOT_FOUND, schemaFilePath)
        // delete secret
        deleteSecretNode(newSecretPath, iamAuthToken)
    }

    @Test
    void "test that a 404 is returned when reading a secret that does not exist"() {
        String sdbPath = testSdb.getString("path")
        sdbPath = StringUtils.substringBeforeLast(sdbPath, "/")
        String newSecretName = UUID.randomUUID().toString()

        def readSecretUri = "$SECRETS_PATH/$sdbPath/$newSecretName"
        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/secret-not-found-error.json"

        // read secret
        validateGETApiResponse(AUTH_TOKEN_HEADER_NAME, iamAuthToken, readSecretUri, HttpStatus.SC_NOT_FOUND, schemaFilePath)
    }

    @Test
    void "test that a 403 is returned when reading a secret from an SDB that does not exist"() {
        def readSecretUri = "$SECRETS_PATH/sdb-does-not-exist/random-secret-name"
        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/permission-denied-invalid-auth-token-error.json"

        // read secret
        validateGETApiResponse(AUTH_TOKEN_HEADER_NAME, iamAuthToken, readSecretUri, HttpStatus.SC_FORBIDDEN, schemaFilePath)
    }

    @Test
    void "test that a 400 is returned when creating an SDB with the same name"() {
        String alreadyExistingSdbName = testSdb.getString("name")
        String ownerRoleId = getRoleMap(iamAuthToken).owner
        String sdbCategoryId = getCategoryMap(iamAuthToken).Applications
        String sdbDescription = generateRandomSdbDescription()

        String iamPrincipalArn = "arn:aws:iam::${accountId}:role/${roleName}"
        def iamPrincipalPermissions = [["iam_principal_arn": iamPrincipalArn, "role_id": ownerRoleId]]
        def sdbObject = [
                category_id: sdbCategoryId,
                name: alreadyExistingSdbName,
                description: sdbDescription,
                owner: iamPrincipalArn,
                'user_group_permissions': [],
                'iam_role_permissions': iamPrincipalPermissions
        ]

        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/create-sdb-with-the-same-name.json"
        // update SDB
        validatePOSTApiResponse(iamAuthToken, V2_SAFE_DEPOSIT_BOX_PATH, HttpStatus.SC_BAD_REQUEST, schemaFilePath, sdbObject)
    }
}
