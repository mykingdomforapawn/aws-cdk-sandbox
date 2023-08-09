package com.myorg;

import software.constructs.Construct;

import java.util.List;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;

public class NetworkSandboxStack extends Stack {

    public NetworkSandboxStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = createVpc();
        NetworkAcl networkAcl = createNetworkAcl(vpc);
        addNetworkAclEntries(networkAcl);
        addVpcEndpoints(vpc);
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "NetworkSandboxVpc")
            .vpcName("NetworkSandboxVpc")
            .maxAzs(2)
            .createInternetGateway(true)
            .natGateways(0)
            .restrictDefaultSecurityGroup(true)
            .subnetConfiguration(
                List.of(
                    SubnetConfiguration.builder()
                    .name("PublicSubnet")
                    .subnetType(SubnetType.PUBLIC)
                    .cidrMask(24)
                    .build(),
                    SubnetConfiguration.builder()
                    .name("PrivateSubnet")
                    .subnetType(SubnetType.PRIVATE_ISOLATED)
                    .cidrMask(24)
                    .build()))
            .build();
    }

    private NetworkAcl createNetworkAcl(Vpc vpc) {
        return NetworkAcl.Builder.create(this, "NetworkSandboxNACL")
                .networkAclName("NetworkSandboxNACL")
                .vpc(vpc)
                .subnetSelection(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .build();
    }

    private void addNetworkAclEntries(NetworkAcl networkAcl) {
        // Add inbound NACL entries
        networkAcl.addEntry("InboundAllowHTTPS", CommonNetworkAclEntryOptions.builder()
            .cidr(AclCidr.anyIpv4())
            .traffic(AclTraffic.tcpPort(443))
            .direction(TrafficDirection.INGRESS)
            .ruleAction(Action.ALLOW)
            .ruleNumber(100)
            .build());

        // Add outbound NACL entries
        networkAcl.addEntry("OutboundAllowHTTPS", CommonNetworkAclEntryOptions.builder()
            .cidr(AclCidr.anyIpv4())
            .traffic(AclTraffic.tcpPort(443))
            .direction(TrafficDirection.EGRESS)
            .ruleAction(Action.ALLOW)
            .ruleNumber(100)
            .build());
    }

    private void addVpcEndpoints(Vpc vpc) {
        // Create a security group for the VPC endpoints
        SecurityGroup securityGroup = SecurityGroup.Builder.create(this, "NetworkSandboxSecurityGroup")
                .securityGroupName("NetworkSandboxSecurityGroup")
                .vpc(vpc)
                .allowAllOutbound(false)
                .description("Security group for VPC endpoints.")
                .build();

        // Add an interface VPC endpoint for AWS KMS
        vpc.addInterfaceEndpoint("KMSEndpoint", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.KMS)
                .subnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_ISOLATED)
                        .build())
                .securityGroups(List.of(securityGroup))
                .build());

        // Add a gateway VPC endpoint for Amazon S3
        vpc.addGatewayEndpoint("S3Gateway", GatewayVpcEndpointOptions.builder()
                .service(GatewayVpcEndpointAwsService.S3)
                .build());
    }
} 
