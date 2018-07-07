package io.jenkins.plugins.analysis.warnings;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

import hudson.model.Run;
import io.jenkins.plugins.analysis.core.views.JobAction;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import io.jenkins.plugins.analysis.core.model.AnalysisResult;
import static io.jenkins.plugins.analysis.core.model.Assertions.*;

import io.jenkins.plugins.analysis.core.steps.IssuesRecorder;
import io.jenkins.plugins.analysis.core.steps.ToolConfiguration;
import io.jenkins.plugins.analysis.core.testutil.IntegrationTest;
import io.jenkins.plugins.analysis.core.views.ResultAction;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

/**
 * Integration tests of the warnings plug-in in freestyle jobs. Tests the new recorder {@link IssuesRecorder}.
 *
 * @author Ullrich Hafner
 */
public class IssuesRecorderITest extends IntegrationTest {
    /**
     * Runs the Eclipse parser on an empty workspace: the build should report 0 issues and an error message.
     */
    @Test
    public void shouldCreateEmptyResult() {
        FreeStyleProject project = createJob();
        enableWarnings(project);

        AnalysisResult result = scheduleBuildAndAssertStatus(project, Result.SUCCESS);

        assertThat(result).hasTotalSize(0);
        assertThat(result).hasErrorMessages("No files found for pattern '**/*issues.txt'. Configuration error?");
    }

    /**
     * Runs the Eclipse parser on an output file that contains several issues: the build should report 8 issues.
     */
    @Test
    public void shouldCreateResultWithWarnings() {
        FreeStyleProject project = createJobWithWorkspaceFile("eclipse.txt");
        enableWarnings(project);

        AnalysisResult result = scheduleBuildAndAssertStatus(project, Result.SUCCESS);

        assertThat(result).hasTotalSize(8);
        assertThat(result).hasInfoMessages("Resolved module names for 8 issues",
                "Resolved package names of 4 affected files");
    }

    /**
     * Sets the UNSTABLE threshold to 8 and parse a file that contains exactly 8 warnings: the build should be
     * unstable.
     */
    @Test
    public void shouldCreateUnstableResult() {
        FreeStyleProject project = createJobWithWorkspaceFile("eclipse.txt");
        enableWarnings(project, publisher -> publisher.setUnstableTotalAll(7));

        AnalysisResult result = scheduleBuildAndAssertStatus(project, Result.UNSTABLE);

        assertThat(result).hasTotalSize(8);
        assertThat(result).hasOverallResult(Result.UNSTABLE);

        HtmlPage page = getWebPage(result);
        assertThat(page.getElementsByIdAndOrName("statistics")).hasSize(1);
    }

    private HtmlPage getWebPage(final AnalysisResult result) {
        try {
            WebClient webClient = j.createWebClient();
            webClient.setJavaScriptEnabled(false);
            return webClient.getPage(result.getOwner(), "eclipseResult");
        }
        catch (SAXException | IOException e) {
           throw new AssertionError(e);
        }
    }

    /**
     * Creates a new {@link FreeStyleProject freestyle job}. The job will get a generated name.
     *
     * @return the created job
     */
    private FreeStyleProject createJob() {
        try {
            return j.createFreeStyleProject();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Creates a new {@link FreeStyleProject freestyle job} and copies the specified resources to the workspace folder.
     * The job will get a generated name.
     *
     * @param fileNames
     *         the files to copy to the workspace
     *
     * @return the created job
     */
    private FreeStyleProject createJobWithWorkspaceFile(final String... fileNames) {
        FreeStyleProject job = createJob();
        copyFilesToWorkspace(job, fileNames);
        return job;
    }

    /**
     * Enables the warnings plugin for the specified job. I.e., it registers a new {@link IssuesRecorder } recorder for
     * the job.
     *
     * @param job
     *         the job to register the recorder for
     *
     * @return the created recorder
     */
    @CanIgnoreReturnValue
    private IssuesRecorder enableWarnings(final FreeStyleProject job) {
        IssuesRecorder publisher = new IssuesRecorder();
        publisher.setTools(Collections.singletonList(new ToolConfiguration("**/*issues.txt", new Eclipse())));
        job.getPublishersList().add(publisher);
        return publisher;
    }

    /**
     * Enables the warnings plugin for the specified job. I.e., it registers a new {@link IssuesRecorder } recorder for
     * the job.
     *
     * @param job
     *         the job to register the recorder for
     * @param configuration
     *         configuration of the recorder
     *
     * @return the created recorder
     */
    @CanIgnoreReturnValue
    private IssuesRecorder enableWarnings(final FreeStyleProject job, final Consumer<IssuesRecorder> configuration) {
        IssuesRecorder publisher = enableWarnings(job);
        configuration.accept(publisher);
        return publisher;
    }

    /**
     * Schedules a new build for the specified job and returns the created {@link AnalysisResult} after the build has
     * been finished.
     *
     * @param job
     *         the job to schedule
     * @param status
     *         the expected result for the build
     *
     * @return the created {@link ResultAction}
     */
    @SuppressWarnings({"illegalcatch", "OverlyBroadCatchBlock"})
    private AnalysisResult scheduleBuildAndAssertStatus(final FreeStyleProject job, final Result status) {
        try {
            FreeStyleBuild build = j.assertBuildStatus(status, job.scheduleBuild2(0));

            ResultAction action = build.getAction(ResultAction.class);

            assertThat(action).isNotNull();

            return action.getResult();
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
    }




    // JobActionTests -------------------------------------------------------------------------------------

    @Test
    public void shouldNotReturnJobActionWithoutBuild() {
        FreeStyleProject project = createJobWithWorkspaceFile("eclipse.txt");
        enableWarningsWithFilePattern(project);

        JobAction jobAction = project.getAction(JobAction.class);
        assertThat(jobAction).isNull();
    }

    @Test
    public void shouldShowDisplayNameAndTrendName() {

        // assertThat(jobAction.getDisplayName()).isEqualTo("Eclipse ECJ Warnings");
        // assertThat(jobAction.getTrendName()).isEqualTo("Eclipse ECJ Warnings Trend");


        FreeStyleProject project = createJobWithWorkspaceFile("eclipse.txt");
        enableWarnings(project);

        AnalysisResult result = scheduleBuildAndAssertStatus(project, Result.SUCCESS);

        assertThat(result).hasOverallResult(Result.SUCCESS);

        HtmlPage page = getWebPage(result);
        assertThat(page.getElementsByIdAndOrName("statistics")).hasSize(1);
    }

    /**
     * Tests if getLastAction returns the jobAction of the last build of the project.
     */
    @Test
    public void shouldReturnLastAction() {
        FreeStyleProject project = createJobWithWorkspaceFile("eclipse.txt");
        enableWarningsWithFilePattern(project);

        scheduleBuildAndAssertStatus(project, Result.SUCCESS);
        JobAction firstJobAction = project.getAction(JobAction.class);
        ResultAction firstAction = firstJobAction.getLastAction();
        String firstOwnerName = firstAction.getOwner().getDisplayName();
        assertThat(firstOwnerName).isEqualToIgnoringCase("#1");

        scheduleBuildAndAssertStatus(project, Result.SUCCESS);
        JobAction secondJobAction = project.getAction(JobAction.class);
        ResultAction secondAction = secondJobAction.getLastAction();
        String secondOwnerName = secondAction.getOwner().getDisplayName();
        assertThat(secondOwnerName).isEqualToIgnoringCase("#2");
    }

    /**
     * Tests if getLastFinishedRun returns the run of the last build of the project.
     */
    @Test
    public void shouldReturnLastFinishedRun() {
        FreeStyleProject project = createJobWithWorkspaceFile("eclipse.txt");
        enableWarningsWithFilePattern(project);

        scheduleBuildAndAssertStatus(project, Result.SUCCESS);
        JobAction firstJobAction = project.getAction(JobAction.class);
        Run<?, ?> firstRun = firstJobAction.getLastFinishedRun();
        assertThat(firstRun.number).isEqualTo(1);

        scheduleBuildAndAssertStatus(project, Result.SUCCESS);
        JobAction secondJobAction = project.getAction(JobAction.class);
        Run<?, ?> secondRun = secondJobAction.getLastFinishedRun();
        assertThat(secondRun.number).isEqualTo(2);
    }

    /**
     * IconFileName should be null if the jobAction has no results.
     */
    @Test
    public void shouldNotHaveIconFileNameWhenLastActionHasNoResults() {

        JobAction jobAction = getJobActionFromNewProject();

        String iconFileName = jobAction.getIconFileName();

        ResultAction action = jobAction.getLastAction();
        assertThat(action.getResult().getTotalSize()).isEqualTo(0);

        assertThat(iconFileName).isNull();
    }

    /**
     * IconFileName should begin with "/static/" and end with "/plugin/analysis-core/icons/analysis-24x24.png".
     * The middle part of iconFileName is generated.
     * The action result size varies between 8 (local) and 9 (server)
     */
    @Test
    public void shouldHaveIconFileName() {

        JobAction jobAction = getJobActionFromNewProjectWithWorkspaceFile();

        String iconFileName = jobAction.getIconFileName();

        ResultAction action = jobAction.getLastAction();
        assertThat(action.getResult().getTotalSize()).isBetween(8, 9);

        assertThat(iconFileName).startsWith("/static/");
        assertThat(iconFileName).endsWith("/plugin/analysis-core/icons/analysis-24x24.png");
    }

    /**
     * Returns the jobAction created during build of a new freeStyleProject with workspace file.
     * @return jobAction test object
     */
    private JobAction getJobActionFromNewProjectWithWorkspaceFile() {
        FreeStyleProject project = createJobWithWorkspaceFile("eclipse.txt");
        enableWarningsWithFilePattern(project);

        scheduleBuildAndAssertStatus(project, Result.SUCCESS);

        JobAction jobAction = project.getAction(JobAction.class);

        assertThat(jobAction).isNotNull();
        assertThat(jobAction.getLastAction().getResult().getErrorMessages().size()).isEqualTo(8);

        return jobAction;
    }

    /**
     * Returns the jobAction created during build of a new freeStyleProject without a workspace file.
     * @return jobAction test object
     */
    private JobAction getJobActionFromNewProject() {
        FreeStyleProject project = createJob();
        enableWarnings(project);

        scheduleBuildAndAssertStatus(project, Result.SUCCESS);

        JobAction jobAction = project.getAction(JobAction.class);

        assertThat(jobAction).isNotNull();

        return jobAction;
    }

    /**
     * Enables the warnings plugin for the specified job
     *
     * @param job
     *         the job to register the recorder for
     *
     * @return the created recorder
     */
    @CanIgnoreReturnValue
    private IssuesRecorder enableWarningsWithFilePattern(final FreeStyleProject job) {
        IssuesRecorder publisher = new IssuesRecorder();
        publisher.setTools(Collections.singletonList(new ToolConfiguration("*.txt", new Eclipse())));
        job.getPublishersList().add(publisher);
        return publisher;
    }
}
