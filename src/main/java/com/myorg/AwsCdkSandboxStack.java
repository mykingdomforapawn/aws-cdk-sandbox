package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.Bucket;

public class AwsCdkSandboxStack extends Stack {
    public AwsCdkSandboxStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AwsCdkSandboxStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here
        Bucket.Builder.create(this, "MyFirstBucket")
            .versioned(true).build();
    }
}
