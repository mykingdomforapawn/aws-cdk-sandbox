from dataclasses import dataclass
import psycopg2
from psycopg2.extras import RealDictCursor
import json
import os
import boto3
from botocore.exceptions import ClientError

host = os.environ.get("HOST")
secret_name = os.environ.get("SECRET_NAME")
database_name = os.environ.get("DATABASE_NAME")
region_name = "eu-west-1"

sql_statement = "SELECT usename FROM pg_user;"

def lambda_handler(event, context):
    # Retrieve credentials
    secret = get_secret()
    username = secret["username"]
    password = secret["password"]
    
    # Connect to database
    conn = psycopg2.connect(
        host = host,
        database = database_name,
        user = username,
        password = password)
    cur = conn.cursor(cursor_factory = RealDictCursor)
    
    # Execute sql and display response
    cur.execute(sql_statement)
    results = cur.fetchall()
    json_result = json.dumps(results)
    print(json_result)
    return json_result

def get_secret():
    # Create a Secrets Manager client
    session = boto3.session.Session()
    client = session.client(
        service_name='secretsmanager',
        region_name=region_name
    )

    # Retrieve secret
    try:
        get_secret_value_response = client.get_secret_value(
            SecretId=secret_name
        )
    except ClientError as e:
        raise e

    # Decrypts secret using the associated KMS key
    secret = get_secret_value_response['SecretString']
    
    return json.loads(secret)
