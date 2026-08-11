plugins {
    java
    id("org.springframework.boot")
    id("com.google.protobuf")
}

description = "gRPC server exposing payment status lookups for orders"

val grpcVersion = "1.65.1"
val protobufVersion = "3.25.3"
val grpcSpringBootStarterVersion = "3.1.0.RELEASE"

dependencies {
    // Spring Boot starter provides basic Spring functionality (no embedded web server here)
    implementation("org.springframework.boot:spring-boot-starter")
    // gRPC server starter integrates net.devh gRPC server with Spring Boot
    implementation("net.devh:grpc-server-spring-boot-starter:$grpcSpringBootStarterVersion")

    // Protobuf-generated message classes used at runtime
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    // gRPC stub APIs used by server code to implement RPCs
    implementation("io.grpc:grpc-stub:$grpcVersion")
    // gRPC helper services (reflection, health checks, etc.)
    implementation("io.grpc:grpc-services:$grpcVersion")
    // Netty transport (shaded) for gRPC network layer at runtime
    runtimeOnly("io.grpc:grpc-netty-shaded:$grpcVersion")
    // Annotations API required only at compile time
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    // JSON parsing for loading fixture data
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Spring test utilities
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

sourceSets {
    main {
        proto {
            srcDir("../proto")
        }
    }
}

protobuf {
    // Protobuf compiler (protoc) artifact used to generate Java sources from .proto files
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        // gRPC plugin for protoc that generates gRPC Java code (services and stubs)
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        // Configure all proto generation tasks to run the grpc plugin so both
        // protobuf message classes and gRPC stubs are generated into the
        // project's source set.
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}
