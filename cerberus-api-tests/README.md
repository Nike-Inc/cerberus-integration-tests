# Cerberus API Tests

All the tests require that the following environment variable be set

Environment Variable | Description
-------------------- | ------------------
CERBERUS_API_URL     | The Cerberus API URL to Test

The tests also require that you manually set up an integration test SDB in the environment you are testing.
(TODO This can probably be automated away with TestNG before suite)

The SDB needs to be configured as follows

Field       | Value
----------- | -----------------------------------------------------------------
Name        | Cerberus Integration Tests SDB 
Category    | Applications
Owner       | some-group-you-belong-to
Description | This SDB is used for integration testing, do not delete this SDB

User Group Permissions

User Group                      | Role
------------------------------- | -------
some-group-your-auto-user-is-in | write

IAM Role Permissions

Acct ID | Role Name           | Role
------- | ------------------- | -----
1111111 | cerberus-api-tester | write

This sub module contains API tests that can validate the composed Cerberus API

### IAM Principal API Tests

This is a series of tests that validate that IAM authenticated Cerberus principals can interact with the Cerberus API 
in the manner that is expected

The following environment variables are required to run this test

Environment Variable | Description
TEST_ACCOUNT_ID      | The account id to use when authenticating with Cerberus using the IAM Auth endpoint
TEST_ROLE_NAME       | The role name to use when authenticating with Cerberus using the IAM Auth endpoint
TEST_REGION          | The region to use when authenticating with Cerberus using the IAM Auth endpoint

You can run this only these tests with the following command

    CERBERUS_API_URL=http://127.0.0.1:9000 \
    TEST_ACCOUNT_ID=11111111 \
    TEST_ROLE_NAME=cerberus-api-tester \
    TEST_REGION=us-west-2 \
    gradlew clean -Dtest.single=CerberusIamApiTests cerberus-api-tests:test
    
### User Principal API Tests

This is a series of tests that validate that user authenticated Cerberus principals can interact with the Cerberus API 
in the manner that is expected

The following environment variables are required to run this test

Environment Variable    | Description
----------------------- | ------------------------------------------------------------------
TEST_USER_EMAIL         | The email address for a test user for testing user based endpoints
TEST_USER_PASSWORD      | The password for a test user for testing user based endpoints
TEST_USER_OTP_SECRET    | The secret for the test users OTP MFA (OTP == Google auth)
TEST_USER_OTP_DEVICE_ID | The device id for the test users OTP MFA (OTP == Google auth)

You can run this only these tests with the following command

    CERBERUS_API_URL=http://127.0.0.1:9000 \
    TEST_USER_EMAIL=cerberus-automated-test-user@nike.com \
    TEST_USER_PASSWORD=${PASSWORD} \
    TEST_USER_OTP_SECRET=${OTP_SECRET} \
    TEST_USER_OTP_DEVICE_ID=111111 \
    gradlew clean -Dtest.single=CerberusIamApiTests cerberus-api-tests:test   