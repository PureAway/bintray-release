package com.novoda.gradle.test


import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import static com.google.common.base.Preconditions.checkNotNull

class TestProject implements TestRule {

    private static final Action<GradleRunner> NONE = {}

    private enum ProjectType {
        JAVA, ANDROID
    }

    private final ProjectType project
    private final Project utils = ProjectBuilder.newInstance().build()
    private BuildFolder tempFolder
    private String buildScript
    private Action<GradleRunner> additionalRunnerConfig

    static TestProject newJavaProject(String buildScript = GradleScriptTemplates.forJavaProject(), Action<GradleRunner> additionalRunnerConfig = NONE) {
        return new TestProject(ProjectType.JAVA, buildScript, additionalRunnerConfig)
    }

    static TestProject newAndroidProject(String buildScript = GradleScriptTemplates.forAndroidProject(), Action<GradleRunner> additionalConfig = NONE) {
        return new TestProject(ProjectType.ANDROID, buildScript, additionalConfig)
    }

    private TestProject(ProjectType project, String buildScript, Action<GradleRunner> additionalRunnerConfig) {
        this.project = project
        this.buildScript = buildScript
        this.additionalRunnerConfig = additionalRunnerConfig
    }

    @Override
    Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            void evaluate() throws Throwable {
                def methodName = (description.methodName ? "/$description.methodName" : '')
                def projectPath = "${description.testClass.canonicalName}${methodName}/test"
                init(projectPath)
                base.evaluate()
            }

        }
    }

    void init(String projectPath) {
        if (tempFolder != null) {
            throw new IllegalStateException("The test project has already been initialised: $tempFolder.root.path.")
        }
        tempFolder = new BuildFolder("test-projects/$projectPath")
        createSourceCode()
        createAndroidManifest()
        createBuildScript()
        createSettingsScript()
    }

    File getProjectDir() {
        checkNotNull(tempFolder, 'The test project has not been initialised yet. Call init() or use it as a test rule.')
        tempFolder.root
    }

    private String createSourceCode() {
        new File(projectDir, "src/main/java/HelloWorld.java").with {
            getParentFile().mkdirs()
            text = "public class HelloWorld {}"
        }
    }

    private void createAndroidManifest() {
        if (project == ProjectType.ANDROID) {
            new File(projectDir, "/src/main/AndroidManifest.xml").with {
                getParentFile().mkdirs()
                text = "<manifest package=\"com.novoda.test\"/>"
            }
        }
    }

    private void createBuildScript() {
        new File(projectDir, 'build.gradle').with {
            text = buildScript
        }
    }

    private void createSettingsScript() {
        new File(projectDir, 'settings.gradle').with {
            text = "rootProject.name = 'test'"
        }
    }

    String getProjectType() {
        return project.name().toLowerCase()
    }

    GradleBuildResult execute(String... arguments) {
        def runner = GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(projectDir)
        additionalRunnerConfig.execute(runner)
        runner.withArguments(arguments)

        try {
            return new GradleBuildResult(runner.build(), true)
        } catch (UnexpectedBuildFailure e) {
            return new GradleBuildResult(e.buildResult, false)
        }
    }

    File buildDir() {
        return new File(projectDir, 'build')
    }

    File buildFile(String path) {
        return new File(buildDir(), path)
    }

    ConfigurableFileTree fileTree(String baseDir) {
        return utils.fileTree(new File(projectDir, baseDir))
    }

    FileTree zipTree(String zipPath) {
        return utils.zipTree(new File(projectDir, zipPath))
    }
}
