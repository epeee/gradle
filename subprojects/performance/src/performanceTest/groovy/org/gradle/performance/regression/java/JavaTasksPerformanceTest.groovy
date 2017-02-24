/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

class JavaTasksPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll
    def "tasks on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${memory}", "-Xmx${memory}"]
        runner.tasksToRun = ['tasks']
        runner.targetVersions = ["3.5-20170223000042+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                  | memory
        "largeMonolithicJavaProject" | "768m"
        "largeJavaMultiProject"      | "768m"
    }

    @Unroll
    def "tasks --all on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${memory}", "-Xmx${memory}"]
        runner.tasksToRun = ['tasks', '--all']
        runner.targetVersions = ["3.5-20170223000042+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                  | memory
        "largeMonolithicJavaProject" | "768m"
        "largeJavaMultiProject"      | "768m"
    }
}
