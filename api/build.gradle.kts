plugins {
    id("io.github.goooler.shadow") version "8.1.8"
    id("maven-publish")
}

repositories {
    maven("https://jitpack.io/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation(project(":common"))
    // Adventure
    implementation("net.kyori:adventure-api:${rootProject.properties["adventure_bundle_version"]}")
    compileOnly("net.kyori:adventure-text-minimessage:${rootProject.properties["adventure_bundle_version"]}")
    compileOnly("net.kyori:adventure-text-serializer-gson:${rootProject.properties["adventure_bundle_version"]}")
    // YAML
    implementation(files("libs/boosted-yaml-${rootProject.properties["boosted_yaml_version"]}.jar"))
    // Cache
    compileOnly("com.github.ben-manes.caffeine:caffeine:${rootProject.properties["caffeine_version"]}")
    // Netty
    compileOnly("io.netty:netty-all:4.1.113.Final")
    // GSON
    compileOnly("com.google.code.gson:gson:${rootProject.properties["gson_version"]}")
    // Fast util
    compileOnly("it.unimi.dsi:fastutil:${rootProject.properties["fastutil_version"]}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
    dependsOn(tasks.clean)
}

tasks {
    shadowJar {
//        archiveClassifier.set("")
        archiveFileName = "custom-nameplates-${rootProject.properties["project_version"]}.jar"
        relocate ("net.kyori", "net.momirealms.customnameplates.libraries")
        relocate("dev.dejvokep", "net.momirealms.customnameplates.libraries")
    }
    javadoc {
        options {
            encoding = "UTF-8"
        }
        options.quiet()
    }
}

publishing {
    repositories {
        maven {
            url = uri("https://repo.momirealms.net/releases")
            credentials(PasswordCredentials::class) {
                username = System.getenv("REPO_USERNAME")
                password = System.getenv("REPO_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "net.momirealms"
            artifactId = "custom-nameplates"
            version = rootProject.properties["project_version"].toString()
            from(components["java"])
            pom {
                name = "CustomNameplates API"
                url = "https://momirealms.net"
            }
        }
    }
}
