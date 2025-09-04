import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask
plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("java")
    id("com.netflix.dgs.codegen") version "6.3.0"
}

group = "com.mabawa.triviacrave"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

val flywayPostgresVersion = "10.21.0"
val jjwtVersion = "0.12.6"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // GraphQL DGS
    implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter:9.0.2")

    // Database + migration
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayPostgresVersion")
    runtimeOnly("org.postgresql:postgresql")

    // Redis (future)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Email
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // TSID for IDs
    implementation("io.hypersistence:hypersistence-tsid:2.1.1")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")

}


tasks.withType<Test> {
    useJUnitPlatform()
}

sourceSets {
    named("main") {
        java {
            srcDir("${layout.buildDirectory.get().asFile}/generated/dgs")
        }
    }
}

tasks.named<GenerateJavaTask>("generateJava") {
    schemaPaths = mutableListOf("src/main/resources/schema")
    packageName = "com.mabawa.triviacrave.generated.graphql"
    typeMapping = mutableMapOf(
        "ID" to "java.lang.String",
        "Long" to "java.lang.Long",
        "LocalDateTime" to "java.time.LocalDateTime",
        "TSID" to "java.lang.Long"
    )
    generateClient = false
}

