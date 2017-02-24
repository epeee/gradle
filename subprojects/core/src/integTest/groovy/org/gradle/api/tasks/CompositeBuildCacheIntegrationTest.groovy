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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.LocalBuildCacheFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

@Unroll
class CompositeBuildCacheIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {

    private TestFile localCache = file('local-cache')
    private TestFile remoteCache = file('remote-cache')
    private TestFile inputFile = file('input.txt')
    private TestFile hiddenInputFile = file('hidden.txt')
    private TestFile outputFile = file('build/output.txt')
    private String cacheableTask = ':cacheableTask'
    private boolean pushToLocal = true
    private boolean pushToRemote = false

    def setup() {
        inputFile.text = 'This is the input'
        hiddenInputFile.text = 'And this is not'
        setupProject()
    }

    def 'push to local'() {
        pushToLocal()

        when:
        withBuildCache().succeeds cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        populatedCache(localCache)
        emptyCache(remoteCache)

        when:
        pullOnly()
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        skippedTasks.contains(cacheableTask)
        populatedCache(localCache)
        emptyCache(remoteCache)

    }

    def 'push to remote'() {
        pushToRemote()

        when:
        withBuildCache().succeeds cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        populatedCache(remoteCache)
        emptyCache(localCache)

        when:
        pullOnly()
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        skippedTasks.contains(cacheableTask)
        populatedCache(remoteCache)
        emptyCache(localCache)

    }

    def 'pull from local first'() {
        pushToRemote()
        hiddenInputFile.text = 'remote'

        when:
        withBuildCache().succeeds cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        populatedCache(remoteCache)
        emptyCache(localCache)

        when:
        settingsFile.text = """
            buildCache {
                local {
                    directory = '${localCache.absoluteFile.toURI()}'
                }
            }
        """
        hiddenInputFile.text = 'local'
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        executedAndNotSkipped(cacheableTask)
        populatedCache(localCache)
        populatedCache(remoteCache)

        when:
        pullOnly()
        hiddenInputFile.text = 'remote'
        withBuildCache().succeeds 'clean', cacheableTask

        then:
        populatedCache(localCache)
        populatedCache(remoteCache)
        outputFile.text == inputFile.text + 'local'
    }

    def 'push is disabled to the remote cache by default'() {
        settingsFile.text = """
            buildCache {        
                local {
                    directory = '${localCache.absoluteFile.toURI()}'                    
                }
                remote(LocalBuildCache) {
                    directory = '${remoteCache.absoluteFile.toURI()}'
                }
            }            
        """.stripIndent()

        when:
        withBuildCache().succeeds cacheableTask

        then:
        populatedCache(localCache)
        emptyCache(remoteCache)
    }

    def 'configuring pushing to remote and local yields a reasonable error'() {
        pushToRemote = true
        pushToLocal = true
        settingsFile.text = cacheConfiguration()

        when:
        withBuildCache().fails cacheableTask

        then:
        failure.assertHasCause('It is only allowed to push to a remote or a local build cache, not to both. Disable push for one of the caches')
    }

    void pulledFrom(cacheDir) {
        assert skippedTasks.contains(cacheableTask)
        assert listCacheFiles(cacheDir).size() == 1
    }

    private boolean populatedCache(TestFile cache) {
        listCacheFiles(cache).size() == 1
    }

    boolean emptyCache(cacheDir) {
        listCacheFiles(cacheDir).empty
    }

    void pushToRemote() {
        pushToLocal = false
        pushToRemote = true
        settingsFile.text = cacheConfiguration()
    }

    void pushToLocal() {
        pushToLocal = true
        pushToRemote = false
        settingsFile.text = cacheConfiguration()
    }

    void pullOnly() {
        pushToLocal = false
        pushToRemote = false
        settingsFile.text = cacheConfiguration()
    }

    private void setupProject() {
        settingsFile.text = cacheConfiguration()

        buildScript """           
            apply plugin: 'base'

            import org.gradle.api.*
            import org.gradle.api.tasks.*

            task cacheableTask(type: MyTask) {
                inputFile = file('input.txt')
                outputFile = file('build/output.txt')
                hiddenInput = file('hidden.txt')
            }
            
            @CacheableTask
            class MyTask extends DefaultTask {

                @OutputFile File outputFile
                
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile
                File hiddenInput

                @TaskAction void doSomething() {
                    outputFile.text = inputFile.text + hiddenInput.text
                }
            }
        """.stripIndent()
    }

    private String cacheConfiguration() {
        """
            buildCache {
                local {
                    directory = '${localCache.absoluteFile.toURI()}' 
                    push = ${pushToLocal}
                }
                remote(LocalBuildCache) {
                    directory = '${remoteCache.absoluteFile.toURI()}'
                    push = ${pushToRemote}
                }
            }
        """.stripIndent()
    }
}
