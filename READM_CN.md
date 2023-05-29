# **Web3** 在AWS上无服务分析

* [English version](./README.md)

## **目标**
这是一个使用以太坊分析的Workshop，将带您了解如何使用 aws 无服务器服务来分析区块链数据。 本次Workshop提供以下内容：
* 在 AWS EC2 上托管一个以太坊全节点。
* 使用来自以太坊全节点的 Lambda 处理数据，然后写入 kinesis。
* 从运动学到数据仓库 RedShift 的流式数据摄取。
* 存储在 RedShift 中的以太坊交易数据的可视化分析。

## **AWS 服务包括**

* EC2 Graviton
* Kinesis Data Stream
* RedShift Serverless
* QuickSight
* Lambda

## 架构图

![](./assets/architecture.jpg)

## 步骤
1. 在 EC2 上设置以太坊全节点，用于同步以太坊主网数据。
2. 使用 Ethereum ETL 将 bock 数据提取到 kinesis。
3. 使用 lambda 处理数据并将处理后的消息传递给另一个kinesis。
4. 使用流式摄取从运动数据流摄取数据到 RedShift Serverless。
5. 使用 QuickSight 查询数据。


## 1. 获取以太坊区块数据
2022 年 9 月，以太坊从工作量证明 (PoW) 到权益证明 (PoS)。为了部署完整节点，我们需要执行客户端和共识客户端。


    * **Instance Type** : m6g.2xlarge
    * **OS**: Ubuntu 20 TSL
    * **Geth** : v1.11.6 stable-ea9e62ca-linux-arm64
    * **Lighthouse** : lighthouse-v4.1.0-aarch64-linux-gnu


1.1 **execution client**: **Geth**

安装 Geth on Ubuntu

```bash
sudo add-apt-repository ppa:ethereum/ethereum
sudo apt-get update -y
sudo apt-get upgrade -y
sudo apt-get install ethereum -y
```
开始 Geth 进程
    
```bash
/usr/bin/geth --authrpc.addr localhost --authrpc.port 8551 --authrpc.vhosts localhost --authrpc.jwtsecret /tmp/jwtsecret --syncmode snap --http --http.api personal,eth,net,web3,txpool --http.corsdomain *
```

   1.2 **consensus client:lighthouse**

   ```bash
   cd ~ curl -LO https://github.com/sigp/lighthouse/releases/download/v4.0.1/lighthouse-v4.0.1-x86_64-unknown-linux-gnu.tar.gz 
   tar -xvf lighthouse-v4.0.1-x86_64-unknown-linux-gnu.tar.gz`
   ```
启动 Lighthouse进程

```bash
lighthouse bn --network mainnet --execution-endpoint http://localhost:8551 --execution-jwt /tmp/jwtsecret --checkpoint-sync-url=https://mainnet.checkpoint.sigp.io  --disable-deposit-contract-sync
```

   1.3 **和Geth交互**
   ```bash
    geth attach <datadir>/geth.ipc
   ```

   1.4 **检查  eth.syncing 状态**
   ```bash
   eth.syncing
   ```
1.5 **eth.syncing**

   When the synchronization is complete, the terminal can query the last blocknumber
   ```bash
   > eth.blockNumber
   ```
   
   
  

## **2. 抽取区块链数据到Kinesis**
2.1 创建 Kinesis Data Streaming 
* Create Kinesis 
  * blockchain-kinesis-t
  * blockchain-kinesis
2.2 通过Lambda处理数据(python 3.8)
``` python
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
        StreamName='blockchain-kinesis',
        Records=result_records
    )
```
**安装 Ethereum ETL**

`sudo apt install python3-pip`

通过EthereumETL抽取数据到Kinesis
* 2.2 Ethereum ETL 
    * Extract data to kiniesis
    ```
    ethereumetl stream  -e block,transaction,token_transfer  --start-block 17277219 \
    --provider-uri file:///home/ubuntu/.ethereum/geth.ipc \
    --output=kinesis://blockchain-kinesis-t
    ```
* 2.2 Query the data entered into kinesis
![](./assets/kinesis-1.jpg)


## 3. 摄入数据到RedShift
3.1   创建物化视图用于 streaming ingestion
Create an external schema to map the data from Kinesis Data Streams to an Amazon Redshift :
```
CREATE EXTERNAL SCHEMA kdsblockchain
FROM KINESIS
IAM_ROLE 'arn:aws:iam::0123456789:role/blockchain-ana-redshift-role'
```

Create the materialized view for data ingestion
```
CREATE MATERIALIZED VIEW blocks_view AUTO REFRESH YES AS
SELECT approximate_arrival_timestamp,
refresh_time,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'type')::TEXT as type,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'number')::BIGINT as number,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'hash')::TEXT as hash,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'parent_hash')::TEXT as parent_hash,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'nonce')::TEXT as nonce,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'sha3_uncles')::TEXT as sha3_uncles,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'logs_bloom')::TEXT as logs_bloom,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'transactions_root')::TEXT as transactions_root,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'state_root')::TEXT as state_root,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'receipts_root')::TEXT as receipts_root,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'miner')::TEXT as miner,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'difficulty')::NUMERIC(38) as difficulty,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'total_difficulty')::NUMERIC(38) as total_difficulty,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'size')::BIGINT as size,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'extra_data')::TEXT as extra_data,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'gas_limit')::BIGINT as gas_limit,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'gas_used')::BIGINT as gas_used,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'timestamp')::INT as timestamp,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'transaction_count')::BIGINT as transaction_count,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'item_id')::TEXT as item_id,
JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'item_timestamp')::TEXT as item_timestamp
FROM kdsblockchain."blockchain-kinesis" where JSON_EXTRACT_PATH_TEXT(FROM_VARBYTE(kinesis_data, 'utf-8'),'type') in ('block');
```
通过Redshfit Stream Ingestion摄取数据到RedShift Serverless

3.3 通过 Redshfit Query editor 查询数据
![](./assets/redshift-data.jpg)



## 4. 通过QuickSight查询数据

![](./assets/quicksight.jpg)

1. Current block height.
2. Popular erc20 addresses in time window.
3. Blocks per minute and gas consumption.
4. top 10 transfer out address.
5. top 10 transfer in address .


