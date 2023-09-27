import json
import time
import base64
import boto3
import datetime;
kinesis_client = boto3.client('kinesis')

def lambda_handler(event, context):
    result_records=[]
    start_time = time.time() 
    for record in event['Records']:
        #Kinesis data is base64 encoded so decode here
        payload=base64.b64decode(record["kinesis"]["data"])
        b_value = json.loads(payload)       
        pk = str(datetime.datetime.now().timestamp())
        print ("pk is " + str(pk))
        result_event = json.dumps(b_value).encode('utf8')
        #print(result_event)        
        package_data = {'Data' :result_event,'PartitionKey':pk}
        result_records.append(package_data)
        
        
    response = kinesis_client.put_records(
        StreamName='processed_blockchain_kds',
        Records=result_records
    )
