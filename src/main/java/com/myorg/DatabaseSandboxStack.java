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
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.iam.*;

public class DatabaseSandboxStack extends Stack {
    
    public DatabaseSandboxStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = createVpc(); //nacls, securitygroups
        SubnetGroup subnetGroup = createSubnetGroup(vpc);
        SecurityGroup lambdaSecurityGroup = createLambdaSecurityGroup(vpc);
        SecurityGroup dbSecurityGroup = createDbSecurityGroup(vpc, lambdaSecurityGroup);
        IClusterEngine clusterEngine = defineClusterEngine();
        ParameterGroup parameterGroup = createParameterGroup(clusterEngine);
        Key key = createKey();
        DatabaseCluster databaseCluster = createDatabaseCluster(vpc, subnetGroup, dbSecurityGroup, parameterGroup, clusterEngine, key);
        Role lambdaExecutionRole = createLambdaExecutionRole();
        Function lambdaFunction = createLambdaFunction(vpc, lambdaExecutionRole, lambdaSecurityGroup);
        }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "vpc")
                .vpcName("DatabaseSandboxVpc")
                .maxAzs(2)
                .createInternetGateway(true)
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
    
    private SecurityGroup createDbSecurityGroup (IVpc vpc, ISecurityGroup lambdaSecurityGroup) {
        SecurityGroup securityGroup =  SecurityGroup.Builder.create(this, "db-security-group")
            .securityGroupName("DatabaseSecurityGroup")
            .allowAllOutbound(false)
            .description("Security group for database cluster.")
            .vpc(vpc)
            .build();

        securityGroup.addIngressRule(lambdaSecurityGroup, Port.tcp(5432), "Allow PostgreSQL traffic.");
        
        return securityGroup;
    }

    private SecurityGroup createLambdaSecurityGroup (IVpc vpc) {
        return SecurityGroup.Builder.create(this, "lambda-security-group")
            .securityGroupName("LambdaSecurityGroup")
            .allowAllOutbound(true)
            .description("Security group for lambda function.")
            .vpc(vpc)
            .build();
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
                .writer(ClusterInstance.serverlessV2("writer",
                        ServerlessV2ClusterInstanceProps.builder()
                            .instanceIdentifier("writer-instance")
                            .publiclyAccessible(false)
                            .autoMinorVersionUpgrade(true)
                            .allowMajorVersionUpgrade(true)
                            .enablePerformanceInsights(false)
                            .build()))
                /*            
                .readers(List.of(ClusterInstance.serverlessV2("reader",
                            ServerlessV2ClusterInstanceProps.builder()
                                .instanceIdentifier("reader-instance")
                                .publiclyAccessible(false)
                                .autoMinorVersionUpgrade(true)
                                .allowMajorVersionUpgrade(true)
                                .enablePerformanceInsights(false)
                                .scaleWithWriter(true)
                                .build())))
                */ 
                .build();
    }

    private Role createLambdaExecutionRole() {
        return Role.Builder.create(this, "execution-role")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonRDSFullAccess"), 
                    ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole")))
                .build();
    }

    private Function createLambdaFunction(IVpc vpc, IRole lambdaExecutionRole, ISecurityGroup securityGroup) {
        return Function.Builder.create(this, "lambda-function")
                .runtime(Runtime.PYTHON_3_11)
                .code(Code.fromAsset("functions"))
                .handler("execute_sql_statement.lambda_handler")
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .securityGroups(List.of(securityGroup))
                .allowPublicSubnet(true)
                .role(lambdaExecutionRole)
                .build();
    }
}