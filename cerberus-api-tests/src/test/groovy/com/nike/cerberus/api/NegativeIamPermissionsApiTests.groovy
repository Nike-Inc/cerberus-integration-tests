package com.nike.cerberus.api

import com.fieldju.commons.PropUtils
import com.nike.cerberus.api.util.TestUtils
import com.thedeanda.lorem.Lorem
import io.restassured.path.json.JsonPath
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpStatus
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import static com.nike.cerberus.api.CerberusApiActions.*
import static com.nike.cerberus.api.CerberusCompositeApiActions.getNEGATIVE_JSON_SCHEMA_ROOT_PATH
import static com.nike.cerberus.api.util.TestUtils.generateRandomSdbDescription
import static com.nike.cerberus.api.util.TestUtils.generateSdbJson

class NegativeIamPermissionsApiTests {

    private static final String PERMISSION_DENIED_JSON_SCHEMA = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/permission-denied-invalid-auth-token-error.json"

    private String accountId
    private String roleName
    private String region
    private String iamAuthToken

    private String username
    private String password
    private String otpDeviceId
    private String otpSecret
    private String[] userGroups
    private String userAuthToken
    private Map userAuthData

    private Map roleMap

    private def iamPrincipalReadOnlySdb
    private def iamPrincipalWriteOnlySdb

    private void loadRequiredEnvVars() {
        accountId = PropUtils.getRequiredProperty("TEST_ACCOUNT_ID",
                "The account id to use when authenticating with Cerberus using the IAM Auth endpoint")

        roleName = PropUtils.getRequiredProperty("TEST_ROLE_NAME",
                "The role name to use when authenticating with Cerberus using the IAM Auth endpoint")

        region = PropUtils.getRequiredProperty("TEST_REGION",
                "The region to use when authenticating with Cerberus using the IAM Auth endpoint")

        username = PropUtils.getRequiredProperty("TEST_USER_EMAIL",
                "The email address for a test user for testing user based endpoints")

        password = PropUtils.getRequiredProperty("TEST_USER_PASSWORD",
                "The password for a test user for testing user based endpoints")

        // todo: make this optional
        otpSecret = PropUtils.getRequiredProperty("TEST_USER_OTP_SECRET",
                "The secret for the test users OTP MFA (OTP == Google auth)")

        otpDeviceId = PropUtils.getRequiredProperty("TEST_USER_OTP_DEVICE_ID",
                "The device id for the test users OTP MFA (OTP == Google auth)")
    }

    @BeforeTest
    void beforeTest() {
        TestUtils.configureRestAssured()
        loadRequiredEnvVars()
        userAuthData = retrieveUserAuthToken(username, password, otpSecret, otpDeviceId)
        userAuthToken = userAuthData."client_token"
        userGroups = userAuthData.metadata.groups.split(/,/)
        String userGroupOfTestUser = userGroups[0]

        String iamPrincipalArn = "arn:aws:iam::${accountId}:role/${roleName}"
        def iamAuthData = retrieveIamAuthToken(iamPrincipalArn, region)
        iamAuthToken = iamAuthData."client_token"

        String sdbCategoryId = getCategoryMap(userAuthToken).Applications
        String sdbDescription = generateRandomSdbDescription()

        roleMap = getRoleMap(userAuthToken)
        def readOnlyIamPrincipalPermissions = [["iam_principal_arn": iamPrincipalArn, "role_id": roleMap.read]]
        def writeOnlyIamPrincipalPermissions = [["iam_principal_arn": iamPrincipalArn, "role_id": roleMap.read]]
        iamPrincipalReadOnlySdb = createSdbV2(userAuthToken, TestUtils.generateRandomSdbName(), sdbDescription, sdbCategoryId, userGroupOfTestUser, [], readOnlyIamPrincipalPermissions)
        iamPrincipalWriteOnlySdb = createSdbV2(userAuthToken, TestUtils.generateRandomSdbName(), sdbDescription, sdbCategoryId, userGroupOfTestUser, [], writeOnlyIamPrincipalPermissions)
    }

    @AfterTest
    void afterTest() {
        String readOnlyIamPrincipalSdbId = iamPrincipalReadOnlySdb.getString("id")
        deleteSdb(userAuthToken, readOnlyIamPrincipalSdbId, V2_SAFE_DEPOSIT_BOX_PATH)

        String writeOnlyIamPrincipalSdbId = iamPrincipalWriteOnlySdb.getString("id")
        deleteSdb(userAuthToken, writeOnlyIamPrincipalSdbId, V2_SAFE_DEPOSIT_BOX_PATH)

        logoutUser(userAuthToken)
        deleteAuthToken(iamAuthToken)
    }

    @Test
    void "test that a read IAM principal cannot edit permissions"() {
        def sdbId = iamPrincipalReadOnlySdb.getString("id")
        def roleMap = getRoleMap(userAuthToken)
        def fake_arn = "arn:aws:iam::0011001100:user/obviously-fake-test-user"

        def newIamPrincipalPermissions = [["iam_principal_arn": fake_arn, "role_id": roleMap.owner]]
        def updateSdbJson = generateSdbJson(
                iamPrincipalReadOnlySdb.getString("description"),
                iamPrincipalReadOnlySdb.getString("owner"),
                iamPrincipalReadOnlySdb.get("user_group_permissions"),
                newIamPrincipalPermissions)
        def updateSdbRequestUri = "$V2_SAFE_DEPOSIT_BOX_PATH/$sdbId"
        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/ownership-required-permissions-error.json"

        // update SDB
        validatePUTApiResponse(iamAuthToken, updateSdbRequestUri, HttpStatus.SC_FORBIDDEN, schemaFilePath, updateSdbJson)
    }

    @Test
    void "test that a read IAM principal cannot update the SDB owner"() {
        def sdbId = iamPrincipalReadOnlySdb.getString("id")
        def newOwner = "new-owner-group"

        def updateSdbJson = generateSdbJson(
                iamPrincipalReadOnlySdb.getString("description"),
                newOwner,
                iamPrincipalReadOnlySdb.get("user_group_permissions"),
                iamPrincipalReadOnlySdb.get("iam_principal_permissions"))
        def updateSdbRequestUri = "$V2_SAFE_DEPOSIT_BOX_PATH/$sdbId"
        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/ownership-required-permissions-error.json"

        // update SDB
        validatePUTApiResponse(iamAuthToken, updateSdbRequestUri, HttpStatus.SC_FORBIDDEN, schemaFilePath, updateSdbJson)
    }

    @Test
    void "test that a read IAM principal cannot write a secret"() {
        String sdbPath = iamPrincipalReadOnlySdb.getString("path")
        sdbPath = StringUtils.substringBeforeLast(sdbPath, "/")

        def writeSecretRequestUri = "$SECRETS_PATH/$sdbPath/${UUID.randomUUID().toString()}"

        // create secret
        validatePOSTApiResponse(iamAuthToken, writeSecretRequestUri, HttpStatus.SC_FORBIDDEN, PERMISSION_DENIED_JSON_SCHEMA, [value: 'value'])
    }

    @Test
    void "test that a read IAM principal cannot delete the SDB"() {
        def sdbId = iamPrincipalReadOnlySdb.getString("id")
        def deleteSdbRequestUri = "$SECRETS_PATH/$sdbId"

        validateDELETEApiResponse(iamAuthToken, deleteSdbRequestUri, HttpStatus.SC_FORBIDDEN, PERMISSION_DENIED_JSON_SCHEMA)
    }

    @Test
    void "test that a write IAM principal cannot edit permissions"() {
        def sdbId = iamPrincipalWriteOnlySdb.getString("id")
        def roleMap = getRoleMap(userAuthToken)
        def fake_arn = "arn:aws:iam::0011001100:user/obviously-fake-test-user"

        def newIamPrincipalPermissions = [["iam_principal_arn": fake_arn, "role_id": roleMap.owner]]
        def updateSdbJson = generateSdbJson(
                iamPrincipalWriteOnlySdb.getString("description"),
                iamPrincipalWriteOnlySdb.getString("owner"),
                iamPrincipalWriteOnlySdb.get("user_group_permissions"),
                newIamPrincipalPermissions)
        def updateSdbRequestUri = "$V2_SAFE_DEPOSIT_BOX_PATH/$sdbId"
        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/ownership-required-permissions-error.json"

        // update SDB
        validatePUTApiResponse(iamAuthToken, updateSdbRequestUri, HttpStatus.SC_FORBIDDEN, schemaFilePath, updateSdbJson)
    }

    @Test
    void "test that a write IAM principal cannot update the SDB owner"() {
        def sdbId = iamPrincipalWriteOnlySdb.getString("id")
        def newOwner = "new-owner-group"

        def updateSdbJson = generateSdbJson(
                iamPrincipalWriteOnlySdb.getString("description"),
                newOwner,
                iamPrincipalWriteOnlySdb.get("user_group_permissions"),
                iamPrincipalWriteOnlySdb.get("iam_principal_permissions"))
        def updateSdbRequestUri = "$V2_SAFE_DEPOSIT_BOX_PATH/$sdbId"
        String schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/ownership-required-permissions-error.json"

        // update SDB
        validatePUTApiResponse(iamAuthToken, updateSdbRequestUri, HttpStatus.SC_FORBIDDEN, schemaFilePath, updateSdbJson)
    }

    @Test
    void "test that a write IAM principal cannot delete the SDB"() {
        def sdbId = iamPrincipalWriteOnlySdb.getString("id")
        def deleteSdbRequestUri = "$SECRETS_PATH/$sdbId"

        validateDELETEApiResponse(iamAuthToken, deleteSdbRequestUri, HttpStatus.SC_FORBIDDEN, PERMISSION_DENIED_JSON_SCHEMA)
        System.out.println("After write tries to delete SDB")
    }

    @Test
    void "test that a write IAM principal cannot call refresh endpoint"() {
        validateGETApiResponse(
                AUTH_TOKEN_HEADER_NAME,
                iamAuthToken,
                "v2/auth/user/refresh",
                HttpStatus.SC_FORBIDDEN,
                "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/requested-resource-for-user-principals-only.json")
    }

    private static Map getRoleMap(String cerberusAuthToken) {
        // Create a map of role ids to names
        JsonPath getRolesResponse = getRoles(cerberusAuthToken)
        def roleMap = [:]
        getRolesResponse.getList("").each { role ->
            roleMap.put role.name, role.id
        }

        return roleMap
    }

    private static Map getCategoryMap(String cerberusAuthToken) {
        // Create a map of category ids to names'
        JsonPath getCategoriesResponse = getCategories(cerberusAuthToken)
        def catMap = [:]
        getCategoriesResponse.getList("").each { category ->
            catMap.put category.display_name, category.id
        }

        return catMap
    }
}
