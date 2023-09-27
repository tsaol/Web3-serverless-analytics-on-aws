# Welcome to AWS Web3 serverless analysis workshop!

## Getting Started
For a detailed walkthrough of this workshop, see [here](https://github.com/tsaol/Web3-serverless-analytics-on-aws)

## Prerequisites
To perform this workshop, you’ll need the following:
   * [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html)
   * [Maven](https://docs.aws.amazon.com/neptune/latest/userguide/iam-auth-connect-prerq.html)
   * [Java](https://aws.amazon.com/corretto/?filtered-posts.sort-by=item.additionalFields.createdDate&filtered-posts.sort-order=desc)

## Deploy this project
cdk deploy --all --parameters EC2Stack:ImageID="ami-0123456789"

## Useful commands

 * `mvn package`     compile and run tests
 * `cdk ls`          list all stacks in the app
 * `cdk synth`       emits the synthesized CloudFormation template
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk docs`        open CDK documentation





