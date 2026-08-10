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
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("net.devh:grpc-server-spring-boot-starter:$grpcSpringBootStarterVersion")

    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")
    runtimeOnly("io.grpc:grpc-netty-shaded:$grpcVersion")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    implementation("com.fasterxml.jackson.core:jackson-databind")

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
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}
