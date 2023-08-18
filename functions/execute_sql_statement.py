import boto3
import json

rds_client = boto3.client("rds-data")

database_name = "DatabaseSandboxInstance"
db_cluster_arn = "arn:aws:rds:eu-west-1:972962482622:cluster:database-sandbox-cluster"
db_credentials_secrets_store_arn = "arn:aws:secretsmanager:eu-west-1:972962482622:secret:SandboxDatabaseSecret-PpIrzc"

def lambda_handler(event, context):
    return execute_statement("SELECT * FROM serverlessdemo.Customer")
    
def execute_statement(sql):
    return rds_client.execute_statement(
        secretArn = db_credentials_secrets_store_arn,
        database = database_name,
        resourceArn = db_cluster_arn,
        sql = sql
    )