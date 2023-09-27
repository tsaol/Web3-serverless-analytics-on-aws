package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import java.util.List;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.CfnOutput;

public class VpcStack extends Stack {
    
    private final Vpc vpc;
    
    public VpcStack(final Construct scope, final String id) {
        this(scope, id, null);
    }    
    
    public VpcStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);    
        
        vpc = Vpc.Builder.create(this, "Web3Vpc")
                .subnetConfiguration(List.of(SubnetConfiguration.builder()
                    .name("Public")
                    .cidrMask(24)
                    .subnetType(SubnetType.PUBLIC)
                    .build()))
                .vpcName("Web3VPC")    
                .build();  
        //CfnOutput.Builder.create(this, "VpcId").value(vpc.getVpcId()).build();
    }
    
    public Vpc getVpc() {
        return vpc;
    }

    
}