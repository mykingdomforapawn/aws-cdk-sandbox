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

        Vpc vpc = createVpc();
        SubnetGroup subnetGroup = createSubnetGroup(vpc);
        SecurityGroup vpcEpSecurityGroup = createVpcEpSecurityGroup(vpc);
        SecurityGroup lambdaSecurityGroup = createLambdaSecurityGroup(vpc);
        SecurityGroup dbSecurityGroup = createDbSecurityGroup(vpc);
        connectSecurityGroups(vpcEpSecurityGroup, lambdaSecurityGroup, dbSecurityGroup);
        createVpcEndpoints(vpc, vpcEpSecurityGroup);
        IClusterEngine clusterEngine = defineClusterEngine();
        ParameterGroup parameterGroup = createParameterGroup(clusterEngine);
        Role lambdaExecutionRole = createLambdaExecutionRole();
        Key key = createKey(lambdaExecutionRole);
        Credentials credentials = createCredentials(key);
        DatabaseCluster databaseCluster = createDatabaseCluster(vpc, subnetGroup, dbSecurityGroup, parameterGroup, clusterEngine, key, credentials);
        Function lambdaFunction = createLambdaFunction(vpc, lambdaExecutionRole, lambdaSecurityGroup, databaseCluster);
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

    private SecurityGroup createVpcEpSecurityGroup(IVpc vpc) {
        return SecurityGroup.Builder.create(this, "vpc-ep-security-group")
                .securityGroupName("VpcEpSecurityGroup")
                .vpc(vpc)
                .allowAllOutbound(false)
                .description("Security group for VPC endpoints.")
                .build();
    }

    private SecurityGroup createLambdaSecurityGroup (IVpc vpc) {
        return SecurityGroup.Builder.create(this, "lambda-security-group")
                .securityGroupName("LambdaSecurityGroup")
                .allowAllOutbound(true)
                .description("Security group for lambda function.")
                .vpc(vpc)
                .build();
    }

    private SecurityGroup createDbSecurityGroup (IVpc vpc) {
        return SecurityGroup.Builder.create(this, "db-security-group")
                .securityGroupName("DatabaseSecurityGroup")
                .allowAllOutbound(false)
                .description("Security group for database cluster.")
                .vpc(vpc)
                .build();
    }

    private void connectSecurityGroups(ISecurityGroup vpcEpSecurityGroup, ISecurityGroup lambdaSecurityGroup, ISecurityGroup dbSecurityGroup) {
        dbSecurityGroup.addIngressRule(lambdaSecurityGroup, Port.tcp(5432), "Allow PostgreSQL traffic.");
        vpcEpSecurityGroup.addIngressRule(lambdaSecurityGroup, Port.allTcp(), "Allow access to VPC endpoints.");
    }

    private void createVpcEndpoints(Vpc vpc, ISecurityGroup securityGroup) {
        vpc.addInterfaceEndpoint("kms-endpoint", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.KMS)
                .subnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .securityGroups(List.of(securityGroup))
                .build());

                vpc.addInterfaceEndpoint("sm-endpoint", InterfaceVpcEndpointOptions.builder()
                .service(InterfaceVpcEndpointAwsService.SECRETS_MANAGER)
                .subnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .securityGroups(List.of(securityGroup))
                .build());
    }

    private Key createKey(IRole lambdaExecutionRole) {
        PolicyDocument policyDocument = PolicyDocument.Builder.create()
         .statements(List.of(PolicyStatement.Builder.create()
                 .actions(List.of("kms:Decrypt"))
                 .principals(List.of(lambdaExecutionRole))
                 .resources(List.of("*"))
                 .effect(Effect.ALLOW)
                 .build()))
         .build();
        
        return Key.Builder.create(this, "key")
                .enableKeyRotation(true)
                .enabled(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .policy(policyDocument)
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

    private Credentials createCredentials(IKey key) {
        return Credentials.fromUsername("SandboxDatabaseAdmin",
                    CredentialsFromUsernameOptions.builder()
                    .secretName("SandboxDatabaseSecret")
                    .encryptionKey(key)    
                    .build());
    }
 
    private DatabaseCluster createDatabaseCluster(IVpc vpc, ISubnetGroup subnetGroup, ISecurityGroup securityGroup, IParameterGroup parameterGroup, IClusterEngine clusterEngine, IKey key, Credentials credentials) {
        return DatabaseCluster.Builder.create(this, "database-cluster")
                .clusterIdentifier("database-sandbox-cluster")
                .engine(clusterEngine)
                .vpc(vpc)
                .subnetGroup(subnetGroup)
                .securityGroups(List.of(securityGroup))
                .defaultDatabaseName("DatabaseSandboxInstance")
                .credentials(credentials)
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
                    ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"),
                    ManagedPolicy.fromAwsManagedPolicyName("SecretsManagerReadWrite"))
                    )
                .build();
    }

    private Function createLambdaFunction(IVpc vpc, IRole lambdaExecutionRole, ISecurityGroup securityGroup, DatabaseCluster databaseCluster) {
        return Function.Builder.create(this, "lambda-function")
                .runtime(Runtime.PYTHON_3_9)
                .code(Code.fromAsset("functions"))
                .handler("execute_sql_statement.lambda_handler")
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .securityGroups(List.of(securityGroup))
                .allowPublicSubnet(true)
                .role(lambdaExecutionRole)
                .environment(
                    Map.of(
                        "HOST", databaseCluster.getClusterEndpoint().getHostname(),
                        "DATABASE", "DatabaseSandboxInstance",
                        "SECRET_NAME", "SandboxDatabaseSecret"
                    ))
                .build();
    }
}