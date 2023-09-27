package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.kinesis.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.eventsources.*;
import java.util.*;

public class StreamingIngestionStack extends Stack {
    
    public StreamingIngestionStack(final Construct scope, final String id) {
        this(scope, id, null);
    }    
    
    public StreamingIngestionStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);   
        
        final Stream rawStream = Stream.Builder.create(this, "raw_blockchain_kds")
                .streamName("raw_blockchain_kds")
                .build();
                
        final Stream processedSteram = Stream.Builder.create(this, "processed_blockchain_kds")
                .streamName("processed_blockchain_kds")
                .build();     
        
        // assign kds write permissions and log premissions to lambda function    
        final Role lambdaRole = Role.Builder.create(this, "LambdaStreamingRole")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .roleName("LambdaStreamingRole")
                .build();
        lambdaRole.addToPolicy(PolicyStatement.Builder.create()
                .actions(List.of("kinesis:PutRecords"))
                .effect(Effect.ALLOW)
                .resources(List.of("*"))
                .build());        
        lambdaRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
        
        
        // Defines a lambda resource
        final Function kdsFunction = Function.Builder.create(this, "KDSFunction")
                .role(lambdaRole)
                .runtime(software.amazon.awscdk.services.lambda.Runtime.PYTHON_3_8)    // execution environment
                .code(Code.fromAsset("lambda"))  // code loaded from the "lambda" directory
                .handler("stream-data-processing-function.lambda_handler")        // file is "stream-data-processing-function", function is "lambda_handler"
                .build();        

        
        kdsFunction.addEventSource(KinesisEventSource.Builder.create(rawStream)
                .startingPosition(StartingPosition.LATEST)
                .build());
        
    }
}