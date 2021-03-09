package se.kth.castor.ci.soraldutils;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import se.kth.castor.ci.githubapi.commits.models.SelectedCommit;
import sorald.Constants;
import sorald.Main;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ComponentScan
public class SoraldCIAdapter {
    public static final String SORALD_GIT_PATCHES_DIR = "SoraldGitPatches";
    private static final String SPOON_SNIPER_MODE = "SNIPER";

    private static final Logger logger = LoggerFactory.getLogger(SoraldCIAdapter.class);
    private static SoraldCIAdapter _instance;

    private String patchPrintingMode = SPOON_SNIPER_MODE;
    private String tmpdir;

    public SoraldCIAdapter(String tmpdir) {
        this.tmpdir = tmpdir;
    }

    public SoraldCIAdapter(String tmpdir, String patchPrintingMode) {
        this.tmpdir = tmpdir;
        this.patchPrintingMode = patchPrintingMode;
    }

    public static SoraldCIAdapter getInstance(String tmpdir, String patchPrintingMode) {
        if (_instance == null)
            _instance = patchPrintingMode == null ? new SoraldCIAdapter(tmpdir)
                    : new SoraldCIAdapter(tmpdir, patchPrintingMode);
        return _instance;
    }

    // returns list of fixed-commit generated urls
    public List<String> repairAndCreateForks(SelectedCommit commit, List<String> rules)
            throws ParseException, GitAPIException, IOException, InterruptedException {
        logger.info("repairing: " + commit.getCommitUrl());

        File repoDir = SoraldGithubInteractionsAdapter.getInstance(tmpdir)
                .cloneRepo(commit.getRepoUrl(), commit.getCommitId(), "repo");
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

            String forkUrl = SoraldGithubInteractionsAdapter.getInstance(tmpdir).createFork(patchedFiles, rule, commit);

            if (forkUrl != null)
                forkUrls.add(forkUrl);
        });

        return forkUrls;
    }



    // returns patch files
    private List<File> repair(String rule, File repoDir) {
        String[] args = new String[]{
                Constants.REPAIR_COMMAND_NAME,
                Constants.ARG_ORIGINAL_FILES_PATH, repoDir.getPath(),
                Constants.ARG_RULE_KEYS, rule,
                Constants.ARG_WORKSPACE, tmpdir,
                Constants.ARG_GIT_REPO_PATH, repoDir.getPath(),
                Constants.ARG_PRETTY_PRINTING_STRATEGY, patchPrintingMode};

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
