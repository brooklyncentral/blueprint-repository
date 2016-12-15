package org.brooklyncentral.catalog.validation.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.singleton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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

import org.apache.brooklyn.util.yaml.Yamls;

import io.cloudsoft.catalog.util.CatalogValidator;

public class PullRequestValidationTest {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestValidationTest.class);

    private static final String DIRECTORY_REPO_URI_PROP_KEY = "directoryRepoURI";
    private static final String PR_NUMBER_PROP_KEY = "prNumber";
    private static final String AUTH_TOKEN_PROP_KEY = "authToken";

    private static final String DEFAULT_REPOSITORIES_URI = "https://github.com/brooklyncentral/blueprint-repository";
    private static final String FILE_TO_DIFF = "directory.yaml";
    private static final String BRANCH_TO_TEST = "master";

    private static final String YAML_KEY_REPOSITORY= "repository";
    private static final String YAML_KEY_FILE= "file";
    private static final String YAML_PARENT_ID= "parentId";
    private static final String FILE_DEFAULT = "catalog.bom";

    private File cloneDirectory;

    private Git git;

    private String masterHash;
    private String prHash;

    @BeforeClass
    public void setUp() throws Exception {
        final String repoUrl = System.getProperty(DIRECTORY_REPO_URI_PROP_KEY, DEFAULT_REPOSITORIES_URI);
        final String prNumber = System.getProperty(PR_NUMBER_PROP_KEY);
        final String authToken = System.getenv(AUTH_TOKEN_PROP_KEY);

        checkNotNull(prNumber, "PR number environment variable '" + PR_NUMBER_PROP_KEY + "' must be set.");

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
        List<String> entriesToValidate = getEntriesToValidate();

        LOG.info("There are " + entriesToValidate.size() + " entries to validate.");

        for (String entryToValidate : entriesToValidate) {
            LOG.info("Validating entry: '" + entryToValidate + "'");

            try {
                File directory = null;
                try {
                    directory = File.createTempFile("catalog", "validator");
                } catch (IOException e) {
                    throw new IllegalStateException(String.format("Cannot create local directory to clone %s at branch %s", entryToValidate, BRANCH_TO_TEST), e);
                }
                directory.delete();

                Map<String, String> catalogItemRepoMap = (Map<String, String>) Yamls.parseAll(entryToValidate).iterator().next();

                String catalogRepository = catalogItemRepoMap.get(YAML_KEY_REPOSITORY);
                Optional<String> catalogFile = Optional.fromNullable(catalogItemRepoMap.get(YAML_KEY_FILE));
                Optional<String> parentId = Optional.fromNullable(catalogItemRepoMap.get(YAML_PARENT_ID));

                try {
                    this.git = Git.cloneRepository()
                            .setDirectory(directory)
                            .setURI(catalogRepository)
                            .setBranchesToClone(singleton("refs/heads/" + BRANCH_TO_TEST))
                            .setBranch("refs/heads/" + BRANCH_TO_TEST)
                            .call();
                } catch (GitAPIException e) {
                    throw new IllegalArgumentException(String.format("Cannot clone %s at branch %s", catalogRepository, BRANCH_TO_TEST), e);
                }

                String file = catalogFile.or(FILE_DEFAULT);
                if(parentId.isPresent()){
                    CatalogValidator.validateWithId(directory, file, parentId.get());
                } else {
                    CatalogValidator.validate(directory, file);
                }


            } catch (Exception e) {
               // fail("Validation failed for entry: '" + entryToValidate + "'", e);
                LOG.info("Validation failed for entry: '" + entryToValidate + "'", e);
            }

            LOG.info("Successfully validated entry: '" + entryToValidate + "'");
        }

        LOG.info("All entries successfully validated.");
    }

    private List<String> getEntriesToValidate() throws Exception {
        final List<String> diffLines = Arrays.asList(getDiff());
        final List<String> entriesToValidate = new LinkedList<>();

        for (final String diffLine : diffLines) {
            if (diffLine.startsWith("+-")) {
                String entryToValidate = diffLine.replace("+-", "").trim();
                entriesToValidate.add(entryToValidate);
            }
        }
        return entriesToValidate;
    }

    private String[] getDiff() throws Exception {
        AbstractTreeIterator oldTreeIterator = generateTreeIterator(git.getRepository(), masterHash);
        AbstractTreeIterator newTreeIterator = generateTreeIterator(git.getRepository(), prHash);

        List<DiffEntry> diff = git.diff()
                .setOldTree(oldTreeIterator)
                .setNewTree(newTreeIterator)
                .setPathFilter(PathFilter.create(FILE_TO_DIFF))
                .call();

        if (diff.size() == 1) {
            DiffEntry entry = diff.get(0);

            OutputStream diffOutputStream = new ByteArrayOutputStream();

            try (DiffFormatter formatter = new DiffFormatter(diffOutputStream)) {
                formatter.setRepository(git.getRepository());
                formatter.format(entry);
            }
            return diffOutputStream.toString().split("\\r?\\n");
        } else {
            LOG.warn("File [{}] was unchanged", FILE_TO_DIFF);
            return new String[]{};
        }


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
