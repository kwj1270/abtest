plugins {
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    implementation("commons-codec:commons-codec:1.23.0")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
