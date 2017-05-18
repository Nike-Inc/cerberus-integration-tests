# Cerberus Gatling Performance Tests

[Gatling](http://gatling.io/) based performance tests

## Parameters

These properties can be supplied to the simulation via environment variables or system properties

Parameter | Description
--------- | ---------------
CERBERUS_API_URL | This controls the api that will be perfromance tested
CERBERUS_ACCOUNT_ID | The account id is needed when creating iam roles for the test and granting kms decrypt for the cerberus account
REGION | The region to auth with Cerberus and use KMS in
NUMBER_OF_SERVICES_FOR_SIMULATION | This is the number of SDBs with random data will be created for the simulation. Each simulated user will be fed one of these services randomly to be.
CREATE_IAM_ROLES | This defaults to false. Setting this to true creates a new iam role that will get deleted at the end of the simulation for each NUMBER_OF_SERVICES_FOR_SIMULATION WARNING: This creates a KMS key for each IAM role that does not get cleaned up automatically. Setting this to false makes each simulated service use the role that is running the tests.
PEAK_USERS | The peak number of simulated concurrent users for the test
RAMP_UP_TIME_IN_MINUTES | The amount of time to ramp down from peak users to 0 users.
HOLD_TIME_AFTER_PEAK_IN_MINUTES | The amount of minutes to hold the peak users for

## Running

### Local

You can use the following gradlew task

    ./gradlew clean runSimulation
    
### Somewhere else

The following gradle task can create a fat jar containing the tests

    ./gradlew clean gatlingCompileSimulationFatJar
    
You can trigger the tests via 

    java -jar PATH/TO/JAR io.gatling.app.Gatling --simulation SIMULATION NAME -rf PATH/TO/SAVE/REPORT
    
See the [Gatling docs](http://gatling.io/docs/current/) for more information