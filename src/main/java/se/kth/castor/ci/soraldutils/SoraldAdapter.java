package se.kth.castor.ci.soraldutils;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import se.kth.castor.ci.githubapi.commits.models.SelectedCommit;
import sorald.Constants;
import sorald.Main;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ComponentScan
public class SoraldAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SoraldAdapter.class);
    private static SoraldAdapter _instance;

    private String tmpdir;

    public SoraldAdapter(String tmpdir) {
        this.tmpdir = tmpdir;
    }

    public static SoraldAdapter getInstance(String tmpdir) {
        if (_instance == null)
            _instance = new SoraldAdapter(tmpdir);
        return _instance;
    }

    // returns list of fixed-commit generated urls
    public List<String> repair(SelectedCommit commit, List<String> rules)
            throws ParseException, GitAPIException, IOException {
        logger.info("repairing: " + commit.getCommitUrl());

        File repoDir = cloneRepo(commit.getRepoUrl());
        logger.info("repo cloned: " + commit.getRepoName());

        Map<String, Set<String>> ruleToIntroducingFiles = getIntroducedViolations(repoDir);
        logger.info("number of introduced rules: " + ruleToIntroducingFiles.entrySet().size());

        ruleToIntroducingFiles.entrySet().forEach(e -> {
            String rule = e.getKey();
            if (!rules.contains(rule))
                return;

            List<File> patchedFiles = repair(rule, repoDir);

            Set<String> violationIntroducingFiles = ruleToIntroducingFiles.get(rule);

            patchedFiles = patchedFiles.stream()
                    .filter(x -> violationIntroducingFiles.stream()
                            .anyMatch(y -> y.contains(x.getName())))
                    .collect(Collectors.toList());

            logger.info("patch files generated for rule: " + rule);

            createFork(patchedFiles, rule, commit);
        });
        return null;
    }

    private void createFork(List<File> patchedFiles, String rule, SelectedCommit commit) {
        logger.info("patched files for " + commit.getCommitUrl() + ":");
        patchedFiles.forEach(x -> logger.info(x.getName()));
    }

    // returns patch files
    private List<File> repair(String rule, File repoDir) {
        String[] args = new String[]{
                "--originalFilesPath", repoDir.getPath(),
                "--ruleKeys", rule,
                "--workspace", tmpdir,
                "--gitRepoPath", repoDir.getPath(),
                "--prettyPrintingStrategy", "SNIPER" };

        Main.main(args);

        File patchDir = new File(tmpdir + File.separator + "SoraldGitPatches");

        return Arrays.asList(patchDir.listFiles());
    }

    private File cloneRepo(String repoUrl) throws IOException, GitAPIException {
        File repoDir = new File(tmpdir + File.separator + "repo");

        if (repoDir.exists())
            FileUtils.deleteDirectory(repoDir);

        repoDir.mkdirs();

        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(repoDir)
                .call();
        git.close();

        return repoDir;
    }

    /**
     * @return A map from ruleNumber to the set of files with more violation locations in the new version.
     */
    private Map<String, Set<String>> getIntroducedViolations(File repoDir)
            throws IOException, GitAPIException, ParseException {
        File copyRepoDir = new File(Files.createTempDirectory("repo_copy").toString());
        FileUtils.copyDirectory(repoDir, copyRepoDir);

        logger.info(FileUtils.sizeOfDirectory(copyRepoDir) + "");

        Map<String, Set<String>> lastRuleToLocations = listViolationLocations(copyRepoDir);
//        Map<String, Set<String>> lastRuleToLocations = null;

        Git git = Git.open(copyRepoDir);
        ObjectId previousCommitId = git.getRepository().resolve("HEAD^");
        git.checkout().setName(previousCommitId.getName()).call();
        git.close();

        logger.info(FileUtils.sizeOfDirectory(copyRepoDir) + "");

        Map<String, Set<String>> previousRuleToLocations = listViolationLocations(copyRepoDir);
//        Map<String, Set<String>> previousRuleToLocations = null;

        FileUtils.deleteDirectory(copyRepoDir);

        Map<String, Set<String>> ret = new HashMap<String, Set<String>>();

        for (Map.Entry<String, Set<String>> e : lastRuleToLocations.entrySet()) {
            String ruleNumber = e.getKey();

            // a map from the filename to the number of violations of this type in that file
            Map<String, Long> newFileToViolationCnt =
                    e.getValue().stream().map(specifier -> specifier.split(File.pathSeparator)[1])
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            Map<String, Long> oldFileToViolationCnt =
                    previousRuleToLocations.get(ruleNumber).stream()
                            .map(specifier -> specifier.split(File.pathSeparator)[1])
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            ret.put(ruleNumber, newFileToViolationCnt.entrySet().stream()
                    .filter(x -> !oldFileToViolationCnt.containsKey(x.getKey())
                            || x.getValue() > oldFileToViolationCnt.get(x.getKey()))
                    .map(Map.Entry::getKey).collect(Collectors.toSet()));
        }

        return ret;
    }

    /**
     * @param repoDir .
     * @return A map from ruleNumber to the set of corresponding violation locations.
     */
    private Map<String, Set<String>> listViolationLocations(File repoDir) throws IOException, ParseException {
        Map<String, Set<String>> ret = new HashMap<String, Set<String>>();

        File stats = new File(Files.createTempFile("mining_stats", ".json").toString()),
                miningTmpFile = new File(Files.createTempDirectory("mining_tmp").toString());

        String[] args =
                new String[]{
                        Constants.MINE_COMMAND_NAME,
                        Constants.ARG_ORIGINAL_FILES_PATH,
                        repoDir.getPath(),
                        Constants.ARG_TEMP_DIR,
                        miningTmpFile.getPath(),
                        Constants.ARG_STATS_OUTPUT_FILE,
                        stats.getPath(),
                        Constants.ARG_HANDLED_RULES
                };

        FileUtils.deleteDirectory(miningTmpFile);

        Main.main(args);

        JSONParser parser = new JSONParser();
        JSONObject jo = (JSONObject) parser.parse(new FileReader(stats));
        JSONArray ja = (JSONArray) jo.get("minedRules");
        for (int i = 0; i < ja.size(); i++) {
            Set<String> violationLocations = new HashSet<String>();

            jo = (JSONObject) ja.get(i);
            String rule = jo.get("ruleKey").toString();

            JSONArray warningLocations = (JSONArray) jo.get("warningLocations");
            for (int j = 0; j < warningLocations.size(); j++) {
                String location = jo.get("violationSpecifier").toString();
                violationLocations.add(location);
            }

            if (violationLocations.size() > 0) {
                ret.put(rule, violationLocations);
            }
        }

        stats.delete();

        return ret;
    }
}
