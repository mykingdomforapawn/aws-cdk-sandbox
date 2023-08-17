package com.myorg;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.kms.*;
import software.constructs.Construct;
import software.constructs.IConstruct;
import software.amazon.awscdk.services.rds.*;

public class DatabaseSandboxStack extends Stack {
    
    public DatabaseSandboxStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = createVpc(); //nacls, securitygroups
        SubnetGroup subnetGroup = createSubnetGroup(vpc);
        SecurityGroup securityGroup = createSecurityGroup(vpc);
        IClusterEngine clusterEngine = defineClusterEngine();
        ParameterGroup parameterGroup = createParameterGroup(clusterEngine);
        Key key = createKey();
        DatabaseCluster databaseCluster = createDatabaseCluster(vpc, subnetGroup, securityGroup, parameterGroup, clusterEngine, key);
        }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "vpc")
                .vpcName("DatabaseSandboxVpc")
                .maxAzs(2)
                .createInternetGateway(false)
                .natGateways(0)
                .restrictDefaultSecurityGroup(true)
                .subnetConfiguration(
                    List.of(
                        SubnetConfiguration.builder()
                        .name("Public")
                        .subnetType(SubnetType.PUBLIC)
                        .cidrMask(24)
                        .build(),
                        SubnetConfiguration.builder()
                        .name("PrivateEgress")
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .cidrMask(24)
                        .build()))
                .build();
    }
     
    private SubnetGroup createSubnetGroup (IVpc vpc) {
        return SubnetGroup.Builder.create(this, "subnet-group")
                .description("Subnet group for database cluster.")
                .vpc(vpc)
                .removalPolicy(RemovalPolicy.DESTROY)
                .subnetGroupName("DatabaseSandboxSubnetGroup")
                .vpcSubnets(SubnetSelection.builder()
                        .onePerAz(true)
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .build())
                .build();
    }
    
    private SecurityGroup createSecurityGroup(IVpc vpc){
        SecurityGroup securityGroup =  SecurityGroup.Builder.create(this, "security-group")
            .securityGroupName("DatabaseSandboxSecurityGroup")
            .allowAllOutbound(false)
            .description("Security group for database cluster.")
            .vpc(vpc)
            .build();

        securityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(22), "Allow SSH access.");
        securityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "Allow HTTP access.");
        
        return securityGroup;
    }

    private Key createKey() {
        return Key.Builder.create(this, "key")
                .enableKeyRotation(true)
                .enabled(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private IClusterEngine defineClusterEngine() {
        return DatabaseClusterEngine
            .auroraPostgres(AuroraPostgresClusterEngineProps.builder()
                .version(AuroraPostgresEngineVersion.VER_15_3)
                .build());
    }

    private ParameterGroup createParameterGroup(IClusterEngine clusterEngine) {
        return ParameterGroup.Builder.create(this, "parameter-group")
            .engine(clusterEngine)
            .parameters(Map.ofEntries(
            Map.entry("lc_messages", "en_US.UTF-8"),
            Map.entry("lc_monetary", "en_US.UTF-8"),
            Map.entry("lc_numeric", "en_US.UTF-8"),
            Map.entry("lc_time", "en_US.UTF-8")))
            .build();
    }
 
    private DatabaseCluster createDatabaseCluster(IVpc vpc, ISubnetGroup subnetGroup, ISecurityGroup securityGroup, IParameterGroup parameterGroup, IClusterEngine clusterEngine, IKey key) {
        return DatabaseCluster.Builder.create(this, "database-cluster")
                .clusterIdentifier("database-sandbox-cluster")
                .engine(clusterEngine)
                .vpc(vpc)
                .subnetGroup(subnetGroup)
                .securityGroups(List.of(securityGroup))
                .defaultDatabaseName("DatabaseSandboxInstance")
                .credentials(Credentials.fromUsername("SandboxDatabaseAdmin",
                    CredentialsFromUsernameOptions.builder()
                    .secretName("SandboxDatabaseSecret")
                    .encryptionKey(key)    
                    .build()))
                .storageEncrypted(true)    
                .storageEncryptionKey(key)
                .parameterGroup(parameterGroup)
                .deletionProtection(false)
                .backup(BackupProps.builder()
                    .preferredWindow("00:00-02:00")
                    .retention(Duration.days(1))
                    .build())
                .iamAuthentication(true)
                .instanceUpdateBehaviour(InstanceUpdateBehaviour.BULK)
                .monitoringInterval(Duration.seconds(60))
                .preferredMaintenanceWindow("Tue:12:00-Tue:13:00")
                .removalPolicy(RemovalPolicy.DESTROY) 
                .serverlessV2MinCapacity(0.5)
                .serverlessV2MaxCapacity(2)
                .storageType(DBClusterStorageType.AURORA_IOPT1)
                .writer(ClusterInstance.serverlessV2("writer-instance",
                        ServerlessV2ClusterInstanceProps.builder()
                            .instanceIdentifier("writer_instance")
                            .publiclyAccessible(true)
                            .autoMinorVersionUpgrade(true)
                            .allowMajorVersionUpgrade(true)
                            .enablePerformanceInsights(false)
                            .build()))
                .readers(List.of(ClusterInstance.serverlessV2("reader-instance",
                            ServerlessV2ClusterInstanceProps.builder()
                                .instanceIdentifier("reader-instance")
                                .publiclyAccessible(true)
                                .autoMinorVersionUpgrade(true)
                                .allowMajorVersionUpgrade(true)
                                .enablePerformanceInsights(false)
                                .scaleWithWriter(true)
                                .build())))
                .build();
    }
}
            
            
            
            
            
            