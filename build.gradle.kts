import dev.feedforward.markdownto.DownParser

buildscript {
  repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
  }

  dependencies {
    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
    classpath "com.github.AlexPl292:mark-down-to-slack:1.1.2"
  }
}

plugins {
  id "org.jetbrains.intellij" version "0.7.2"
  id "io.gitlab.arturbosch.detekt" version "1.15.0"
  id "org.jetbrains.changelog" version "1.1.2"
  // ktlint linter - read more: https://github.com/JLLeitschuh/ktlint-gradle
  id "org.jlleitschuh.gradle.ktlint" version "10.0.0"
}

apply plugin: 'java'
apply plugin: 'kotlin'

sourceCompatibility = javaVersion
targetCompatibility = javaVersion

tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }

sourceSets {
  main {
    java.srcDir 'src'
    resources.srcDir 'resources'
  }
  test {
    java.srcDir 'test'
  }
}

intellij {
  version ideaVersion
  pluginName 'IdeaVim'
  updateSinceUntilBuild false
  downloadSources Boolean.valueOf(downloadIdeaSources)
  instrumentCode Boolean.valueOf(instrumentPluginCode)
  intellijRepo = "https://www.jetbrains.com/intellij-repository"
  plugins = ['java']

  downloadRobotServerPlugin.version = "0.10.0"

  publishPlugin {
    channels publishChannels.split(',')
    username publishUsername
    token publishToken
  }
}

runIdeForUiTests {
  systemProperty "robot-server.port", "8082"
}

runPluginVerifier {
  ideVersions = ["IC-2020.2.3", "IC-2020.3.2"]
  downloadDirectory = "${project.buildDir}/pluginVerifier/ides"
  teamCityOutputFormat = true
}

repositories {
  mavenCentral()
  jcenter()
  maven { url = "https://jetbrains.bintray.com/intellij-third-party-dependencies" }
}

dependencies {
  compileOnly "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion"
  compileOnly "org.jetbrains:annotations:20.1.0"

  // https://mvnrepository.com/artifact/com.ensarsarajcic.neovim.java/neovim-api
  testImplementation("com.ensarsarajcic.neovim.java:neovim-api:0.2.3")
  testImplementation 'com.ensarsarajcic.neovim.java:core-rpc:0.2.3'

  testImplementation("com.intellij.remoterobot:remote-robot:0.10.3")
  testImplementation("com.intellij.remoterobot:remote-fixtures:1.1.18")
}

compileKotlin {
  kotlinOptions {
    jvmTarget = javaVersion
  }
//  kotlinOptions.allWarningsAsErrors = true
}
compileTestKotlin {
  kotlinOptions {
    jvmTarget = javaVersion
  }
//  kotlinOptions.allWarningsAsErrors = true
}

detekt {
  config = files("${rootProject.projectDir}/.detekt/config.yaml")
  baseline = file("${rootProject.projectDir}/.detekt/baseline.xml")
  input = files("src")

  buildUponDefaultConfig = true

  reports {
    html.enabled = false
    xml.enabled = false
    txt.enabled = false
  }
}

tasks.detekt.jvmTarget = javaVersion

task testWithNeovim(type: Test) {
  group = "verification"
  systemProperty "ideavim.nvim.test", 'true'
  exclude '/ui/**'
}

test {
  exclude '**/propertybased/**'
  exclude '/ui/**'
}

task testPropertyBased(type: Test) {
  group = "verification"
  include '**/propertybased/**'
}

task testUi(type: Test) {
  group = "verification"
  include '/ui/**'
}

changelog {
  groups = ["Features:", "Changes:", "Deprecations:", "Fixes:", "Merged PRs:"]
  itemPrefix = "*"
  path = "${project.projectDir}/CHANGES.md"
  unreleasedTerm = "To Be Released"
  headerParserRegex = /0\.\d{2}(.\d+)?/
//  header = { "${project.version}" }
//  version = "0.60"
}

task getUnreleasedChangelog() {
  group = "changelog"
  doLast {
    def log = changelog.getUnreleased().toHTML()
    println log
  }
}

tasks.register("slackEapNotification") {
  doLast {
    if (!slackUrl) return
    def post = new URL(slackUrl).openConnection()
    def changeLog = changelog.getUnreleased().toText()
    def slackDown = new DownParser(changeLog, true).toSlack().toString()
    def message = """
      {
          "text": "New version of IdeaVim",
          "blocks": [
                  {
                    "type": "section",
                    "text": {
                      "type": "mrkdwn",
                      "text": "IdeaVim EAP $version has been released\\n$slackDown"
                    }
                  }
          ]
      }
   """
    post.setRequestMethod("POST")
    post.setDoOutput(true)
    post.setRequestProperty("Content-Type", "application/json")
    post.getOutputStream().write(message.getBytes("UTF-8"))
    def postRC = post.getResponseCode()
    println(postRC)
    if (postRC == 200) {
      println(post.getInputStream().getText())
    }
  }
}

ktlint {
  // I don't know how to disable it for java.utils.* only
  disabledRules = ["no-wildcard-imports"]
}

gradle.projectsEvaluated {
  tasks.withType(JavaCompile) {
    options.compilerArgs << "-Werror" << "-Xlint:deprecation"
  }
}
