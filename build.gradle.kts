plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.2")
    compileOnly("com.formdev:flatlaf:3.7.1")
    implementation("com.formdev:flatlaf-extras:3.7.1")
    implementation("io.github.ollama4j:ollama4j:1.1.6")
    implementation("com.anthropic:anthropic-java:2.22.0")
    implementation("com.openai:openai-java:4.31.0")
    implementation("org.commonmark:commonmark:0.28.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.28.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.28.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.28.0")

    testImplementation("net.portswigger.burp.extensions:montoya-api:2026.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("-Djava.awt.headless=true")
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