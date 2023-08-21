# Welcome to your CDK Java project!

This is a blank project for CDK development with Java.

The `cdk.json` file tells the CDK Toolkit how to execute your app.

It is a [Maven](https://maven.apache.org/) based project, so you can open this project with any Maven compatible Java IDE to build and run tests.

## Useful commands

 * `mvn package`     compile and run tests
 * `cdk ls`          list all stacks in the app
 * `cdk synth`       emits the synthesized CloudFormation template
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk docs`        open CDK documentation

Enjoy!

# erstmal iam policy
## cf stack mit der policie aufsetzen (bis jetzt nur create policie)
aws-cdk-sandbox % aws iam create-policy --policy-name CloudformationExecutionPolicy --policy-document file://.aws/CloudformationExecutionPolicy.json

# user identifizieren und arn raussuchen

#   bpptstrap
## bootstrap 
cdk bootstrap --cloudformation-execution-policies arn:aws:iam::972962482622:policy/CloudformationExecutionPolicy --trust arn:aws:iam::972962482622:user/ludwig_dev
    "Deploys the CDK Toolkit staging stack"

cdk synth "Synthesizes and prints the CloudFormation template for one or more specified stacks"
cdk diff "Compares the specified stack and its dependencies with the deployed stacks or a local CloudFormation template"
cdk cdk deploy StackName "Deploys one or more specified stacks"
cdk destroy StackName "Destroys one or more specified stacks"



##database stack
mitpgadmin verbinden auf db und test queries ausführen

lambda erstellen mit https://www.youtube.com/watch?v=W-tzoGYMfTA
    im vpc 
rolle erstelen mit rds full access
richtige lanbda bauen 
    psycog2 einbinden (aus externem repo)
    kurze erklärung, warum
https://www.youtube.com/watch?v=NGteAkN2WYc 
https://github.com/jkehler/awslambda-psycopg2

!! hier weiter
    - künstlich erstellte iam policy für kms ins cdk einfügen
    - alte policy löschen
    - lambda so schreiben, dass der secret string ordentlich verwendet wird
    - db zugriff verünftig bauen
    - projekt abschließen


## iam auth in neuem projekt ausprobieren
https://www.youtube.com/watch?v=kGTAcj_zI3o