package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.redshiftserverless.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.customresources.*;
import java.util.*;
import software.amazon.awscdk.services.secretsmanager.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

public class RedshiftServerlessStack extends Stack {
    
    public RedshiftServerlessStack(final Construct scope, final String id) {
        this(scope, id, null, null);
    }    
    
    public RedshiftServerlessStack(final Construct scope, final String id, final StackProps props, Vpc vpc) {
        super(scope, id, props);  
        
        final String REDSHIFT_NAMESPACE_NAME = "blockchain-namespace";
        final String REDSHIFT_WORKGROUP_NAME = "blockchain-workgroup";
        final String ADMIN_USER_NAME = "web3workshop";
        final String DB_NAME = "dev";
        
        final SecurityGroup sg1 = SecurityGroup.Builder.create(this, "Quicksight-SG")
                .vpc(vpc)
                .allowAllOutbound(true)
                .securityGroupName("Quicksight-SG")
                .build();
        final SecurityGroup sg2 = SecurityGroup.Builder.create(this, "Redshift-SG")
                .vpc(vpc)
                .allowAllOutbound(true)
                .securityGroupName("Redshift-SG")
                .build();
        sg2.addIngressRule(Peer.securityGroupId(sg1.getSecurityGroupId()), Port.tcp(5439), "allow quicksight access on port 5439");
        
        
        Secret secret = null;
        try{
            secret = Secret.Builder.create(this, "RedshiftSecret")
                    .generateSecretString(SecretStringGenerator.builder()
                            .secretStringTemplate(new ObjectMapper().writeValueAsString(Map.of("username", ADMIN_USER_NAME)))
                            .generateStringKey("password")
                            .excludeCharacters("!\"'()*+,-./:;<=>?@[\\]^_`{|}~")
                            .build())
                    .build();
        } catch(JsonProcessingException e) {
            e.printStackTrace();
        }
        
        
        final Role redshiftStreamingRole = Role.Builder.create(this, "RedshiftStreamingRole")
                .assumedBy(new CompositePrincipal(new ServicePrincipal("redshift.amazonaws.com"), new ServicePrincipal("redshift-serverless.amazonaws.com")))
                .roleName("RedshiftStreamingRole")
                .build();
        redshiftStreamingRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonKinesisReadOnlyAccess"));
        redshiftStreamingRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonRedshiftQueryEditorV2FullAccess"));
         

        
        final CfnNamespace namespace = CfnNamespace.Builder.create(this, "RedshiftServerlessNamespace")
                .namespaceName(REDSHIFT_NAMESPACE_NAME)
                .adminUsername(ADMIN_USER_NAME)
                .adminUserPassword(secret.secretValueFromJson("password").unsafeUnwrap())
                .dbName(DB_NAME)
                .defaultIamRoleArn(redshiftStreamingRole.getRoleArn())
                .iamRoles(List.of(redshiftStreamingRole.getRoleArn()))
                .build();  

        final CfnWorkgroup workgroup = CfnWorkgroup.Builder.create(this, "RedshiftServerlessWorkgroup")
                .workgroupName(REDSHIFT_WORKGROUP_NAME)
                .baseCapacity(8)
                .namespaceName(namespace.getNamespaceName())
                .securityGroupIds(List.of(sg2.getSecurityGroupId()))
                .subnetIds(vpc.selectSubnets().getSubnetIds())
                .build();  
        workgroup.getNode().addDependency(namespace);        
        
        
        final String  sql_statement = "CREATE EXTERNAL SCHEMA kds FROM KINESIS IAM_ROLE default;\n" +  
                            "CREATE MATERIALIZED VIEW block_view AUTO REFRESH YES AS " +
                            "SELECT approximate_arrival_timestamp,"+
                            "refresh_time,"+
                            "partition_key,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'type\')::text as type,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'number\')::bigint as number,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'hash\')::text as hash,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'parent_hash\')::text as parent_hash,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'nonce\')::text as nonce,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'sha3_uncles\')::text as sha3_uncles,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'logs_bloom\')::text as logs_bloom,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'transactions_root\')::text as transactions_root,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'state_root\')::text as state_root,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'receipts_root\')::text as receipts_root,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'miner\')::text as miner,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'difficulty\')::numeric(38) as difficulty,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'total_difficulty\')::text as total_difficulty,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'size\')::bigint as size,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'extra_data\')::text as extra_data,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'gas_limit\')::bigint as gas_limit,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'gas_used\')::bigint as gas_used,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'timestamp\')::int as timestamp,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'transaction_count\')::bigint as transaction_count,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'item_id\')::text as item_id,"+
                            "json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'item_timestamp\')::text as item_timestamp "+
                            "FROM kds.\"processed_blockchain_kds\" WHERE json_extract_path_text(from_varbyte(kinesis_data, \'utf-8\'),\'type\') in (\'block\');";  
       
       
        final PolicyStatement statement1 = PolicyStatement.Builder.create()
                        .actions(List.of("redshift:GetClusterCredentials","redshift-serverless:GetCredentials","redshift-data:ExecuteStatement"))
                        .effect(Effect.ALLOW)
                        .resources(List.of("*"))
                        .build();
        final PolicyStatement statement2 = PolicyStatement.Builder.create()
                        .actions(List.of("secretsmanager:GetSecretValue"))
                        .effect(Effect.ALLOW)
                        .resources(List.of(secret.getSecretArn()))
                        .build();   

       final AwsCustomResource awsCustom = AwsCustomResource.Builder.create(this, "aws-custom-execute-sql")
                .onCreate(AwsSdkCall.builder()
                        .service("RedshiftData")
                        .action("executeStatement")
                        .parameters(Map.of(
                            "Database","dev",
                            "SecretArn", secret.getSecretArn(),
                            "Sql", sql_statement, 
                            "WithEvent", true, 
                            "WorkgroupName", REDSHIFT_WORKGROUP_NAME))
                        .physicalResourceId(PhysicalResourceId.of("physicalResourceRedshiftServerlessSQLStatement"))
                        .build())
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(statement1, statement2)))
                .build();      
        awsCustom.getNode().addDependency(workgroup); 
        

    }
    

    
}