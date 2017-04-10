package com.nike.cerberus.api

import com.thedeanda.lorem.Lorem
import io.restassured.path.json.JsonPath
import org.apache.commons.lang3.RandomStringUtils
import org.jboss.aerogear.security.otp.Totp

import static org.junit.Assert.assertEquals
import static com.nike.cerberus.api.CerberusApiActions.*
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class CerberusCompositeApiActions {
    private CerberusCompositeApiActions() {}

    static final String ROOT_INTEGRATION_TEST_SDB_PATH = "app/cerberus-integration-tests-sdb"

    static void "create, read, update then delete a secret node"(String cerberusAuthToken) {
        def path = "${ROOT_INTEGRATION_TEST_SDB_PATH}/${UUID.randomUUID().toString()}"
        String value1 = 'value1'
        String value2 = 'value2'

        // Create the initial secret node
        createOrUpdateSecretNode([value: value1], path, cerberusAuthToken)
        // Read and verify that it was created
        def resp = readSecretNode(path, cerberusAuthToken)
        assertEquals(value1, resp?.'data'?.'value')
        // Update the secret node
        createOrUpdateSecretNode([value: value2], path, cerberusAuthToken)
        // Read that the node was updated
        def resp2 = readSecretNode(path, cerberusAuthToken)
        assertEquals(value2, resp2?.'data'?.'value')
        // Delete the node
        deleteSecretNode(path, cerberusAuthToken)
        // Verify that the node was deleted
        assertThatSecretNodeDoesNotExist(path, cerberusAuthToken)
    }

    static void "create, read, list, update and then delete a safe deposit box"(Map cerberusAuthPayloadData, String baseSdbApiPath) {
        String cerberusAuthToken = cerberusAuthPayloadData.'client_token'
        String groups = cerberusAuthPayloadData.metadata.groups
        def group = groups.split(/,/)[0]

        // Create a map of category ids to names'
        JsonPath getCategoriesResponse = getCategories(cerberusAuthToken)
        def catMap = [:]
        getCategoriesResponse.getList("").each { category ->
            catMap.put category.display_name, category.id
        }
        // Create a map of role ids to names
        JsonPath getRolesResponse = getRoles(cerberusAuthToken)
        def roleMap = [:]
        getRolesResponse.getList("").each { role ->
            roleMap.put role.name, role.id
        }

        String name = "${RandomStringUtils.randomAlphabetic(5,10)} ${RandomStringUtils.randomAlphabetic(5,10)}"
        String description = "${Lorem.getWords(50)}"
        String categoryId = catMap.Applications
        String owner = group
        def userGroupPermissions = [
                [
                        name: 'foo',
                        'role_id': roleMap.read
                ]
        ]
        def iamRolePermissions = [getIamRolePermission(baseSdbApiPath, "1111111111", "fake_role", roleMap.write)]

        def sdbId = createSdb(cerberusAuthToken, name, description, categoryId, owner, baseSdbApiPath, userGroupPermissions, iamRolePermissions)
        JsonPath sdb = readSdb(cerberusAuthToken, sdbId, baseSdbApiPath)

        // verify that the sdb we created contains the data we expect
        assertEquals(name, sdb.get('name'))
        assertEquals(description, sdb.get('description'))
        assertEquals(categoryId, sdb.get('category_id'))
        assertEquals(owner, sdb.get('owner'))
        assertEquals(userGroupPermissions.size(), sdb.getList('user_group_permissions').size())
        assertEquals(userGroupPermissions.get(0).name, sdb.getList('user_group_permissions').get(0).name)
        assertEquals(userGroupPermissions.get(0).'role_id', sdb.getList('user_group_permissions').get(0).'role_id')
        assertEquals(iamRolePermissions.size(), sdb.getList('iam_role_permissions').size())
        assertTrue(iamRolePermissionEquals(iamRolePermissions.get(0), sdb.getList('iam_role_permissions').get(0)))
        assertEquals(iamRolePermissions.get(0).'role_id', sdb.getList('iam_role_permissions').get(0).'role_id')

        // verify that the listing call contains our new SDB
        def sdbList = listSdbs(cerberusAuthToken)
        def foundNewSdb = false
        def listSdb

        sdbList.getList("").each { sdbMeta ->
            if (sdbMeta.id == sdbId) {
                foundNewSdb = true
                listSdb = sdbMeta
            }
        }
        assertTrue("Failed to find the newly created SDB in the list results", foundNewSdb)
        assertEquals(listSdb.name, sdb.get('name'))
        assertEquals(listSdb.id, sdb.get('id'))
        assertEquals(listSdb.path, sdb.get('path'))
        assertEquals(listSdb.'category_id', sdb.get('category_id'))

        // update the sdb
        description = "${Lorem.getWords(60)}"
        userGroupPermissions.add([
            name: 'bar',
            'role_id': roleMap.write
        ])
        iamRolePermissions.add(getIamRolePermission(baseSdbApiPath, "1111111111", "fake_role2", roleMap.read))
        updateSdb(cerberusAuthToken, sdbId, description, owner, baseSdbApiPath, userGroupPermissions, iamRolePermissions)
        JsonPath sdbUpdated = readSdb(cerberusAuthToken, sdbId, baseSdbApiPath)

        // verify that the sdbUpdated we created contains the data we expect
        assertEquals(name, sdbUpdated.get('name'))
        assertEquals(description, sdbUpdated.get('description'))
        assertEquals(categoryId, sdbUpdated.get('category_id'))
        assertEquals(owner, sdbUpdated.get('owner'))
        assertEquals(userGroupPermissions.size(), sdbUpdated.getList('user_group_permissions').size())
        for (def expectedPerm : userGroupPermissions) {
            def found = false
            for (def actualPerm : sdbUpdated.getList('user_group_permissions')) {
                if (expectedPerm.name == actualPerm.name) {
                    found = true
                    assertEquals(expectedPerm.'role_id', actualPerm.'role_id')
                }
            }
            assertTrue("The expected user permission was not found in the actual results", found)
        }

        assertEquals(iamRolePermissions.size(), sdbUpdated.getList('iam_role_permissions').size())
        for (def expectedPerm : iamRolePermissions) {
            def found = false
            for (def actualPerm : sdbUpdated.getList('iam_role_permissions')) {
                if (expectedPerm.'iam_role_name' == actualPerm.'iam_role_name' ||
                        expectedPerm.'iam_principal_arn' == actualPerm.'iam_principal_arn') {
                    found = true
                    assertTrue(iamRolePermissionEquals(expectedPerm, actualPerm))
                }
            }
            assertTrue("The expected user permission was not found in the actual results", found)
        }

        // delete the SDB
        deleteSdb(cerberusAuthToken, sdbId)

        // verify that the sdb is not longer in the list
        def updatedSdbList = listSdbs(cerberusAuthToken)
        def isSdbPresentInUpdatedList = false

        updatedSdbList.getList("").each { sdbMeta ->
            if (sdbMeta.id == sdbId) {
                isSdbPresentInUpdatedList = true
            }
        }
        assertFalse("The created sdb should not be in the sdb listing call after deleting it", isSdbPresentInUpdatedList)
    }

    static Map "login user with multi factor authentication (or skip mfa if not required) and return auth data"(
            String username, String password, String otpSecret, String deviceId) {

        JsonPath loginResp = loginUser(username, password)
        String status = loginResp.getString("status")
        if (status == "success") {
            return loginResp.getString("data.client_token")
        } else {
            def mfaResp = finishMfaUserAuth(
                    loginResp.getString("data.state_token"),
                    deviceId,
                    new Totp(otpSecret).now())

            return mfaResp.get('data.client_token')
        }
    }

    private static Map getIamRolePermission(String baseSdbApiPath, String accountId, String roleName, role) {
        if (baseSdbApiPath == V1_SAFE_DEPOSIT_BOX_PATH) {
            return [
                    "account_id": accountId,
                    "iam_role_name": roleName,
                    "role_id": role
            ]
        }
        if (baseSdbApiPath == V2_SAFE_DEPOSIT_BOX_PATH) {
            return [
                    "iam_principal_arn": (String) "arn:aws:iam::$accountId:role/$roleName",
                    "role_id": role
            ]
        }
        return null
    }

    private static boolean iamRolePermissionEquals(def iamRolePermission1, def iamRolePermission2) {
        if (iamRolePermission1.'account_id' == iamRolePermission2.'account_id' &&
                iamRolePermission1.'iam_role_name' == iamRolePermission2.'iam_role_name') {

            return true
        }
        if (iamRolePermission1.'iam_principal_arn' == iamRolePermission2.'iam_principal_arn') {

            return true
        }

        return false
    }
}
