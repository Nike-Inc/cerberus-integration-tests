# Health Check Lambda for Cerberus

This is a quick and dirty Node Lambda to run an end-to-end test of the general health of a production 
[Cerberus](http://engineering.nike.com/cerberus/) environment. It checks that an EC2 instance or in
this case a Lambda can [authenticate](http://engineering.nike.com/cerberus/docs/architecture/authentication)
with Cerberus which will exercise [CMS](https://github.com/Nike-Inc/cerberus-management-service) and its RDS DB. It then uses that auth
token to read from the healthcheck Safe Deposit Box (SDB) which will exercise and test that Vault and Consul are up and running.

To learn more about Cerberus, please see the [Cerberus website](http://engineering.nike.com/cerberus/).

## Building / Deploying

1. Install the deps


    npm install

2. Package the assets    
    
    
    aws --region us-east-1 cloudformation package \
    --template-file application.yaml \
    --output-template-file deploy.yaml \
    --s3-bucket [S3 BUCKET IN REGION YOU WANT TO USE]

   
3. The package command only packages the node code, we need to manually upload the swagger config


    aws --region us-east-1 s3 cp ./swagger.yaml s3://[S3 BUCKET IN REGION YOU WANT TO USE]/health-check-swagger.yaml

4. The above package command will output something like the following


    aws cloudformation deploy --template-file /Users/jfiel2/development/projects/nike-oss-github/cerberus-healthcheck-lambda/deploy.yaml --stack-name <YOUR STACK NAME>    
    
Copy it and add the cerberus url stack param as well as the region you want to deploy and run it in and add the IAM capabilities
    
    aws --region us-east-1 \
    cloudformation deploy \
    --capabilities CAPABILITY_IAM
    --template-file /Users/jfiel2/development/projects/nike-oss-github/cerberus-healthcheck-lambda/deploy.yaml \
    --stack-name test-cerberus-healthcheck \
    --parameter-overrides CerberusUrl=https://test.foo.com

4. Use the AWS CLI to get the health check url for integrating into your monitoring solution its a stack output ApiUrl

    
    
    
5. Wire up the url into your monitoring solution


    Your on your own here
