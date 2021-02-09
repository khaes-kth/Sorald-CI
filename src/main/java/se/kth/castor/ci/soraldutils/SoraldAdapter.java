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
import org.sonar.java.testing.FilesUtils;
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
    private static final String SORALD_GIT_PATCHES_DIR = "SoraldGitPatches";
    private static final String SORALD_CI_REPO = "Sorald-CI";
    private static final String SORALD_URL = "git@github.com:khaes-kth/Sorald-CI.git";

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
            throws ParseException, GitAPIException, IOException, InterruptedException {
        logger.info("repairing: " + commit.getCommitUrl());

        File repoDir = cloneRepo(commit.getRepoUrl(), commit.getCommitId(), "repo");
        logger.info("repo cloned: " + commit.getRepoName());

        Map<String, Set<String>> ruleToIntroducingFiles = getIntroducedViolations(repoDir);
        logger.info("number of introduced rules: " + ruleToIntroducingFiles.entrySet().size());

        List<String> forkUrls = new ArrayList<String>();

        ruleToIntroducingFiles.entrySet().forEach(e -> {
            String rule = e.getKey();
            if (!rules.contains(rule))
                return;

            List<File> patchedFiles = repair(rule, repoDir);

            Set<String> violationIntroducingFiles = ruleToIntroducingFiles.get(rule);

            patchedFiles = patchedFiles.stream()
                    .filter(x -> violationIntroducingFiles.stream()
                            .anyMatch(y -> {
                                try {
                                    return FileUtils.readFileToString(x, "UTF-8").contains(y);
                                } catch (IOException ioException) {
                                    ioException.printStackTrace();
                                    return false;
                                }
                            }))
                    .collect(Collectors.toList());

            logger.info("patch files generated for rule: " + rule);

            String forkUrl = createFork(patchedFiles, rule, commit);

            if (forkUrl != null)
                forkUrls.add(forkUrl);
        });

        return forkUrls;
    }

    private String createFork
            (
                    List<File> patchedFiles,
                    String rule,
                    SelectedCommit commit
            ) {
        logger.info("patched files for " + commit.getCommitUrl() + ":");
        patchedFiles.forEach(x -> logger.info(x.getName()));

        File repoDir = null;
        try {
            repoDir = cloneRepo(commit.getRepoUrl(), commit.getCommitId(), "repo");
        } catch (Exception e) {
            logger.error("could not clone repo");
            return null;
        }

        for (File patch : patchedFiles) {
            ProcessBuilder processBuilder =
                    new ProcessBuilder("git", "apply", patch.getAbsolutePath())
                            .directory(repoDir).inheritIO();

            try {
                Process p = processBuilder.start();
                int res = p.waitFor();
                if (res != 0) {
                    logger.error("cannot apply patch");
                    return null;
                }
            } catch (InterruptedException | IOException e) {
                logger.error("Error while executing git command to apply patch");
                return null;
            }
        }

        logger.info("all patches applied");

        File soraldRepo = new File(tmpdir + File.separator + SORALD_CI_REPO);
        try {
            FileUtils.deleteDirectory(soraldRepo);
        } catch (IOException e) {
            logger.error("error while removing old sorald-ci dir");
            return null;
        }

        try {
            ProcessBuilder processBuilder =
                    new ProcessBuilder("git", "clone", SORALD_URL)
                            .directory(new File(tmpdir)).inheritIO();
            Process p = processBuilder.start();
            int res = p.waitFor();
            if (res != 0) {
                logger.error("cannot clone sorald-ci");
                return null;
            }
        } catch (Exception e) {
            logger.error("error while cloning sorald-ci");
            return null;
        }

        String copiedFixedRepoDir = soraldRepo.getPath() + File.separator + "fixed_repo";

        try {
            FileUtils.copyDirectory(repoDir,
                    new File(copiedFixedRepoDir));
        } catch (IOException e) {
            logger.error("cannot copy patched repo into sorald-ci");
            return null;
        }

        try {
            FileUtils.deleteDirectory(new File(copiedFixedRepoDir + File.separator + ".git"));
        } catch (IOException e) {
            logger.error("error while removing .git folder");
            return null;
        }

        try {
            FileUtils.copyDirectory(new File(tmpdir + File.separator + SORALD_GIT_PATCHES_DIR),
                    new File(copiedFixedRepoDir + File.separator + SORALD_GIT_PATCHES_DIR));
        } catch (IOException e) {
            logger.error("cannot copy patched repo into sorald-ci");
            return null;
        }

        String newBranch = "fixed_" + commit.getRepoName() + "_" + commit.getCommitId() + "_" + rule;
        try {
            try {
                FileUtils.writeStringToFile(new File(copiedFixedRepoDir + File.separator + "fixed_repo_info.txt"),
                        commit.toString() + System.lineSeparator() + "rule: " + rule, "UTF-8");
            } catch (Exception e) {
                logger.error("cannot cat the fix info");
            }

            ProcessBuilder processBuilder =
                    new ProcessBuilder("git", "add", "--all")
                            .directory(new File(copiedFixedRepoDir)).inheritIO();
            Process p = processBuilder.start();
            int res = p.waitFor();
            if (res != 0) {
                logger.error("cannot git add all");
                return null;
            }

            processBuilder =
                    new ProcessBuilder("git", "checkout", "-b", newBranch)
                            .directory(soraldRepo).inheritIO();
            p = processBuilder.start();
            res = p.waitFor();
            if (res != 0) {
                logger.error("cannot checkout new branch");
                return null;
            }

            processBuilder =
                    new ProcessBuilder("git", "commit", "-m", "\"fixed\"")
                            .directory(soraldRepo).inheritIO();
            p = processBuilder.start();
            res = p.waitFor();
            if (res != 0) {
                logger.error("cannot commit fixed");
                return null;
            }

            processBuilder =
                    new ProcessBuilder("git", "push", "origin", newBranch)
                            .directory(soraldRepo).inheritIO();
            p = processBuilder.start();
            res = p.waitFor();
            if (res != 0) {
                logger.error("cannot push new branch");
                return null;
            }

        } catch (Exception e) {
            logger.error("error while pushing new fork");
            return null;
        }

        return SORALD_URL.replace(".git", "") + "/tree/" + newBranch;
    }

    // returns patch files
    private List<File> repair(String rule, File repoDir) {
        String[] args = new String[]{
                Constants.REPAIR_COMMAND_NAME,
                Constants.ARG_ORIGINAL_FILES_PATH, repoDir.getPath(),
                Constants.ARG_RULE_KEYS, rule,
                Constants.ARG_WORKSPACE, tmpdir,
                Constants.ARG_GIT_REPO_PATH, repoDir.getPath(),
                Constants.ARG_PRETTY_PRINTING_STRATEGY, "SNIPER"};

        File gitPatchesDir = new File(tmpdir + File.separator + SORALD_GIT_PATCHES_DIR);
        if (gitPatchesDir.exists()) {
            try {
                FileUtils.deleteDirectory(gitPatchesDir);
            } catch (IOException e) {
                logger.error("cannot remove SoraldGitPatches dir");
            }
        }

        Main.main(args);

        return Arrays.asList(gitPatchesDir.listFiles());
    }

    private File cloneRepo(String repoUrl, String commitId, String dirname)
            throws IOException, GitAPIException {
        File repoDir = new File(tmpdir + File.separator + dirname);

        if (repoDir.exists())
            FileUtils.deleteDirectory(repoDir);

        repoDir.mkdirs();

        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(repoDir)
                .call();

        if (commitId != null)
            git.checkout().setName(commitId).call();

        git.close();

        return repoDir;
    }

    /**
     * @return A map from ruleNumber to the set of files with more violation locations in the new version.
     */
    private Map<String, Set<String>> getIntroducedViolations(File repoDir)
            throws IOException, GitAPIException, ParseException, InterruptedException {
        File copyRepoDir = new File(tmpdir + File.separator + "copy_repo");
        if (copyRepoDir.exists())
            FileUtils.deleteDirectory(copyRepoDir);

        copyRepoDir.mkdirs();

        FileUtils.copyDirectory(repoDir, copyRepoDir);

        Map<String, Set<String>> lastRuleToLocations = listViolationLocations(copyRepoDir);
//        Map<String, Set<String>> lastRuleToLocations = null;

        ProcessBuilder processBuilder =
                new ProcessBuilder("git", "stash")
                        .directory(copyRepoDir).inheritIO();
        Process p = processBuilder.start();
        int res = p.waitFor();
        if (res != 0) {
            logger.error("cannot stash");
            return new HashMap<String, Set<String>>();
        }

        processBuilder =
                new ProcessBuilder("git", "checkout", "HEAD^")
                        .directory(copyRepoDir).inheritIO();
        p = processBuilder.start();
        res = p.waitFor();
        if (res != 0) {
            logger.error("cannot checkout to head^");
            return new HashMap<String, Set<String>>();
        }

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
                    !previousRuleToLocations.containsKey(ruleNumber) ? new HashMap<String, Long>() :
                            previousRuleToLocations.get(ruleNumber).stream()
                                    .map(specifier -> specifier.split(File.pathSeparator)[1])
                                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            ret.put(ruleNumber, newFileToViolationCnt.entrySet().stream()
                    .filter(x -> !oldFileToViolationCnt.containsKey(x.getKey())
                            || x.getValue() > oldFileToViolationCnt.get(x.getKey()))
                    .map(Map.Entry::getKey).collect(Collectors.toSet()));

            if (ret.containsKey(ruleNumber) && ret.get(ruleNumber).size() == 0)
                ret.remove(ruleNumber);
        }

        return ret;
    }

    /**
     * @param repoDir .
     * @return A map from ruleNumber to the set of corresponding violation locations.
     */
    private Map<String, Set<String>> listViolationLocations(File repoDir) throws IOException, ParseException {
        Map<String, Set<String>> ret = new HashMap<String, Set<String>>();

        File stats = new File(tmpdir + File.separator + "mining_stats.json"),
                miningTmpFile = new File(tmpdir + File.separator + "mining_tmp_dir");

        if (stats.exists())
            stats.delete();

        if (miningTmpFile.exists())
            FileUtils.deleteDirectory(miningTmpFile);

        stats.createNewFile();

        miningTmpFile.mkdirs();

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
                jo = (JSONObject) warningLocations.get(j);
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
