/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.3/userguide/building_java_projects.html in the Gradle documentation.
 */

// Note that there is lots of "../" below, because our files are not organized the way gradle expects them.

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application

    // sets version property by calling git-describe
    id("io.github.ngyewch.git-describe") version "0.2.0"

    // Make classes like TestUtils usable by projects that include this project
    id("java-test-fixtures")

    jacoco
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

configurations {
    testFixturesApi {
        extendsFrom(implementation.get())
    }
}


dependencies {

    // I'm not sure why this is here -- maybe by accident?
    //implementation("com.google.guava:guava:32.1.1-jre")

    implementation("org.biojava:biojava-alignment:7.0.2"){
        exclude(group = "openchart", module ="openchart") //this doesn't exist in maven and wasn't included in the checked in jar
    }

    implementation("commons-io:commons-io:2.17.0")
    implementation("org.biojava:biojava-core:7.0.2")
    implementation("org.la4j:la4j:0.6.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.yaml:snakeyaml:2.2")

    implementation("com.github.broadinstitute:picard:3.3.0"){
        attributes{
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        }
    }

    testFixturesImplementation("org.testng:testng:7.5.1")

    // Use TestNG framework, also requires calling test.useTestNG() below
    testImplementation("org.testng:testng:7.5.1")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    // Define the main class for the application.
    mainClass.set("org.broadinstitute.dropseqrna.cmdline.DropSeqMain")
}

tasks.withType<Test> {
    // Use TestNG for unit tests.
    useTestNG()
    // so test resources can be found
    workingDir("..")
    if (System.getenv("TMPDIR") != null) {
        systemProperty("java.io.tmpdir", System.getenv("TMPDIR"))
    }
}

tasks.test {
    finalizedBy("jacocoTestReport")
}
tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required = true
    }
}

// Conform to our non-standard directory structure
sourceSets {
    main {
        java {
            setSrcDirs(listOf("../src/java"))
        }
    }

    test {
        java {
            setSrcDirs(listOf("../src/tests/java"))
        }
    }

    testFixtures {
        java {
            setSrcDirs(listOf("../src/testFixtures/java"))
        }
    }
}
var manifest_version = version
// Set via -P on command line.  I would really lke to be able to execute
// git log within gradle, but I can't figure out how to do that.
if (project.properties["manifest_version"] != null) {
    manifest_version = project.properties["manifest_version"] as String
}

tasks.jar {
    archiveFileName = "dropseq.jar"
    manifest {
        attributes(
                "Implementation-Title" to "Drop-seq tools",
                "Implementation-Version" to manifest_version, // set via git-describe plugin or -P gradle command-line option
                "Implementation-Vendor" to "Broad Institute",
                "Main-Class" to "org.broadinstitute.dropseqrna.cmdline.DropSeqMain",
                "Class-Path" to sourceSets.main.get().runtimeClasspath.filter { file -> file.name.endsWith(".jar") }.joinToString(" ") { file -> file.name }
        )
    }
}

// Run a script to generate wrappers for all our command-line programs
tasks.register<Exec>("generateWrappers") {
    commandLine("../src/build/make_wrapper_scripts.sh",
            "-t", "../src/build/public_clp_template.sh",
            "-d", "build/tmp/wrappers",
            "-c", sourceSets.main.get().runtimeClasspath.asPath,
            "--", "--list-commands")
    dependsOn("classes")
}

distributions {
    main {
        contents {
            from("../src/scripts")
            from("../doc/Drop-seq_Alignment_Cookbook.pdf")
	    from("../doc/Census-seq_Computational_Protcools.pdf")
            from("../doc/Donor_Assignment_Computational_Cookbook.pdf")
            from("build/tmp/wrappers")
        }
    }
}

tasks.named("distZip").configure {
    dependsOn("generateWrappers")
}
