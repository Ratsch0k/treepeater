plugins {
    id("java")
}

repositories {
    mavenCentral()
}

// Mockito's agent has to be loaded up front: self-attaching to a running VM is no longer permitted
// from JDK 21 onwards, and is refused outright on JDK 25.
val mockitoAgent: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.2")
    compileOnly("com.formdev:flatlaf:3.7.1")
    implementation("com.formdev:flatlaf-extras:3.7.1")
    implementation("io.github.ollama4j:ollama4j:1.1.6")
    implementation("com.anthropic:anthropic-java:2.27.0")
    implementation("com.openai:openai-java:4.52.0")
    implementation("org.commonmark:commonmark:0.28.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.28.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.28.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.28.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.15")
    // Serves the local REST and MCP endpoints. Burp's runtime is a trimmed jlink image without the
    // jdk.httpserver module, so com.sun.net.httpserver is unavailable there despite compiling fine.
    implementation("org.eclipse.jetty:jetty-server:12.0.37")

    testImplementation("net.portswigger.burp.extensions:montoya-api:2026.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core:5.23.0")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("-Djava.awt.headless=true")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-javaagent:${mockitoAgent.asPath}")
    })
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.encoding = "UTF-8"
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().filter { it.isDirectory })
    from(configurations.runtimeClasspath.get().filterNot { it.isDirectory }.map { zipTree(it) })
}