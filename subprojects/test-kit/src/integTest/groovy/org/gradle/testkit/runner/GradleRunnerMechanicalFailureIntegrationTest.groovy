/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testkit.runner

import org.gradle.api.GradleException
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.testkit.runner.fixtures.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.InspectsExecutedTasks
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.tooling.GradleConnectionException

import static org.gradle.util.TextUtil.normaliseLineSeparators

class GradleRunnerMechanicalFailureIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "treats invalid argument as build failure and throws if not expected"() {
        given:
        buildScript helloWorldTask()

        when:
        runner('helloWorld', '--unknown').build()

        then:
        thrown UnexpectedBuildFailure
    }

    def "treats invalid argument as build failure and does not throw if expected"() {
        given:
        buildScript helloWorldTask()

        when:
        runner('helloWorld', '--unknown').buildAndFail()

        then:
        noExceptionThrown()
    }

    @InspectsBuildOutput
    @InspectsExecutedTasks
    def "invalid argument build failure includes diagnostic output when not expected"() {
        given:
        buildFile << helloWorldTask()

        when:
        runner('helloWorld', '--unknown').build()

        then:
        def t = thrown UnexpectedBuildFailure
        t.message.contains("Unknown command-line option '--unknown'.")
        t.message.contains('Problem configuring task :helloWorld from command line.')
        def result = t.buildResult
        result.output.contains('BUILD FAILED')
        result.output.contains("Unknown command-line option '--unknown'.")
        result.output.contains("Problem configuring task :helloWorld from command line.")
        result.tasks.empty
    }

    @InspectsBuildOutput
    @InspectsExecutedTasks
    def "invalid argument build failure includes diagnostic output when expected"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld', '--unknown').buildAndFail()

        then:
        result.output.contains('BUILD FAILED')
        result.output.contains("Unknown command-line option '--unknown'.")
        result.output.contains("Problem configuring task :helloWorld from command line.")
    }

    def "build fails if project directory does not exist"() {
        given:
        buildFile << helloWorldTask()

        when:
        runner()
            .withProjectDir(new File('some/path/that/does/not/exist'))
            .build()

        then:
        thrown UnexpectedBuildFailure
    }

    @InspectsBuildOutput
    @InspectsExecutedTasks
    def "build fails if project directory does not exist and provides diagnostic information"() {
        given:
        buildScript helloWorldTask()
        def nonExistentWorkingDir = new File('some/path/that/does/not/exist')

        when:
        runner()
            .withProjectDir(nonExistentWorkingDir)
            .build()

        then:
        def t = thrown(UnexpectedBuildFailure)
        t.message.contains("Project directory '$nonExistentWorkingDir.absolutePath' does not exist.")
        !t.message.contains(':helloWorld')
        def result = t.buildResult
        result.output.contains('BUILD FAILED')
        result.output.contains("Project directory '$nonExistentWorkingDir.absolutePath' does not exist.")
        result.tasks.empty
    }

    @NoDebug
    def "build execution with invalid JVM arguments"() {
        given:
        file('gradle.properties') << 'org.gradle.jvmargs=-unknown'
        buildFile << helloWorldTask()

        when:
        runner('helloWorld').build()

        then:
        def t = thrown IllegalStateException
        t.cause instanceof GradleConnectionException
        t.cause.cause.class.name == GradleException.name // not the same class because it's coming from the tooling client
        t.cause.cause.message.startsWith("Unable to start the daemon process.")
    }

    @NoDebug
    def "daemon dies during build execution"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                    Runtime.runtime.halt(0)
                    println 'Bye world!'
                }
            }
        """

        when:
        runner('helloWorld').build()

        then:
        def t = thrown IllegalStateException
        t.cause instanceof GradleConnectionException
        t.cause.cause.class.name == DaemonDisappearedException.name // not the same class because it's coming from the tooling client

        and:
        normaliseLineSeparators(t.message) == """An error occurred executing build with args 'helloWorld' in directory '$testDirectory.canonicalPath'. Output before error:
:helloWorld
Hello world!
""".toString()
    }
}
