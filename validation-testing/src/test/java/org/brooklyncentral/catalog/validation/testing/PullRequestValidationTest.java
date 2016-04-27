package org.brooklyncentral.catalog.validation.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import io.cloudsoft.catalog.util.CatalogValidator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

public class PullRequestValidationTest {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestValidationTest.class);

    private static final String DIRECTORY_REPO_URI_ENV_VAR = "DIRECTORY_REPO_URI";
    private static final String PR_NUMBER_ENV_VAR = "ghprbPullId";
    private static final String AUTH_TOKEN_ENV_VAR = "AUTH_TOKEN";

    private static final String DEFAULT_REPOSITORIES_URI = "https://github.com/brooklyncentral/brooklyn-community-catalog";
    private static final String FILE_TO_DIFF = "directory.yaml";
    private static final String BRANCH_TO_TEST = "master";

    private File cloneDirectory;

    private Git git;

    private String masterHash;
    private String prHash;

    @BeforeClass
    public void setUp() throws Exception {
        String repoUrl = Optional.fromNullable(System.getenv(DIRECTORY_REPO_URI_ENV_VAR)).or(DEFAULT_REPOSITORIES_URI);
        String prNumber = System.getenv(PR_NUMBER_ENV_VAR);
        String authToken = System.getenv(AUTH_TOKEN_ENV_VAR);

        checkNotNull(prNumber, "PR number environment variable 'PR_NUMBER' must be set.");

        cloneDirectory = Files.createTempDir();

        CloneCommand cloneCommand = Git.cloneRepository()
                .setDirectory(cloneDirectory)
                .setURI(repoUrl)
                .setBranchesToClone(ImmutableList.of("refs/heads/master"))
                .setBranch("refs/heads/master");

        if (authToken != null) {
            cloneCommand = cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(authToken, ""));
        }

        git = cloneCommand.call();
        git.fetch().setRefSpecs(new RefSpec(String.format("+refs/pull/%s/head:refs/remotes/origin/pr/%s", prNumber, prNumber))).call();

        masterHash = git.getRepository().exactRef("refs/heads/master").getObjectId().getName();
        prHash = git.getRepository().exactRef("refs/remotes/origin/pr/" + prNumber).getObjectId().getName();
    }

    @AfterClass
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(cloneDirectory);
    }

    @Test
    public void validateNewRepos() throws Exception {
        List<String> urlsToValidate = getURLsToValidate();

        LOG.info("There are " + urlsToValidate.size() + " URL(s) to validate.");

        for (String urlToValidate : urlsToValidate) {
            LOG.info("Validating URL: '" + urlToValidate + "'");

            try {
                CatalogValidator catalogValidator = new CatalogValidator(urlToValidate, BRANCH_TO_TEST);
                catalogValidator.validate();
            } catch (Exception e) {
                fail("Validation failed for URL: '" + urlToValidate + "'", e);
            }

            LOG.info("Successfully validated URL: '" + urlToValidate + "'");
        }

        LOG.info("All URL(s) successfully validated.");
    }

    private List<String> getURLsToValidate() throws Exception {
        String diff = getDiff();
        List<String> diffLines = IOUtils.readLines(new StringReader(diff));

        List<String> urlsToValidate = new LinkedList<>();

        for (String diffLine : diffLines) {
            if (diffLine.startsWith("+-")) {
                String urlToValidate = diffLine.replace("+-", "").trim();
                urlsToValidate.add(urlToValidate);
            }
        }

        return urlsToValidate;
    }

    private String getDiff() throws Exception {
        AbstractTreeIterator oldTreeIterator = generateTreeIterator(git.getRepository(), masterHash);
        AbstractTreeIterator newTreeIterator = generateTreeIterator(git.getRepository(), prHash);

        List<DiffEntry> diff = git.diff()
                .setOldTree(oldTreeIterator)
                .setNewTree(newTreeIterator)
                .setPathFilter(PathFilter.create(FILE_TO_DIFF))
                .call();

        assertTrue(diff.size() == 1, "Exactly one diff must match for file: '" + FILE_TO_DIFF + "'.");
        DiffEntry entry = diff.get(0);

        OutputStream diffOutputStream = new ByteArrayOutputStream();

        try (DiffFormatter formatter = new DiffFormatter(diffOutputStream)) {
            formatter.setRepository(git.getRepository());
            formatter.format(entry);
        }

        return diffOutputStream.toString();
    }

    private static AbstractTreeIterator generateTreeIterator(Repository repository, String objectId) throws Exception {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit revCommit = revWalk.parseCommit(ObjectId.fromString(objectId));
            RevTree revTree = revWalk.parseTree(revCommit.getTree().getId());

            CanonicalTreeParser canonicalTreeParser = new CanonicalTreeParser();
            try (ObjectReader objectReader = repository.newObjectReader()) {
                canonicalTreeParser.reset(objectReader, revTree.getId());
            }

            revWalk.dispose();

            return canonicalTreeParser;
        }
    }
}
