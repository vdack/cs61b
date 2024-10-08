package gitlet;
import java.util.*;
import static gitlet.Repository.*;
public class Gitter {
    private String currentBranch;
    private Commit currentCommit;
    private List<String> removed;
    private Map<String, String> working;
    private Map<String, String> staged;

    public Gitter() {
        currentBranch = readCurrentBranch();
        currentCommit = readBranchCommit(currentBranch);
        working = readPlainFiles(CWD);
        staged = readPlainFiles(STAGE_DIR);
        removed = readRemovedFiles();
    }

    public String getCurrentBranch() {
        return currentBranch;
    }
    public List<String> getBranch() {
        List<String> result = getBranches();
        Collections.sort(result);
        return result;
    }
    public List<String> getStagedFiles() {
        List<String> result =  new ArrayList<String>(staged.keySet());
        Collections.sort(result);
        return result;
    }
    public List<String> getRemovedFiles() {
        List<String> result = new ArrayList<>(removed);
        Collections.sort(result);
        return result;
    }
    public List<String> getModifiedFiles() {

        List<String> modifiedFiles = new ArrayList<>();
        List<String> tempFiles = new ArrayList<>(currentCommit.getFileNameBlob().keySet());

        for (String file : staged.keySet()) {
            if (!tempFiles.contains(file)) {
                tempFiles.add(file);
            }
        }
        tempFiles.removeAll(removed);

        for (String file : tempFiles) {
            if (!working.containsKey(file)) {
                modifiedFiles.add(file + " (delete)");
                continue;
            }
            String target = currentCommit.getFileNameBlob().get(file);
            if (staged.containsKey(file)) {
                target = staged.get(file);
            }
            String current = working.get(file);
            if (!current.equals(target)) {
                modifiedFiles.add(file + " (modified)");
            }
        }
        Collections.sort(modifiedFiles);
        return modifiedFiles;
    }
    public List<String> getUntrackedFiles() {

        List<String> tempFiles = new ArrayList<>(currentCommit.getFileNameBlob().keySet());
        tempFiles.addAll(staged.keySet());
        tempFiles.removeAll(removed);
        List<String> untrackedFiles = new ArrayList<>(working.keySet());
        untrackedFiles.removeAll(tempFiles);
        Collections.sort(untrackedFiles);
        return untrackedFiles;
    }

    public void addFile(String filename) {
        String workingBlob = working.get(filename);
        String commitBlob = currentCommit.getFileNameBlob().get(filename);
        if (workingBlob == null) {
            throw new GitletException("Could not find file " + filename + " in working directory");
        }

        if (removed.contains(filename)) {
            removed.remove(filename);
            writeRemovedFiles(removed);
        }

        if (workingBlob.equals(commitBlob)) {
            if (staged.containsKey(filename)) {
                deleteFile(filename, STAGE_DIR);
            }
        } else {
            stageFile(filename);
        }

    }

    private void commit(String message, String preSubCommitId, int depth) {
        Map<String, String> filesToCommit = new HashMap<>(currentCommit.getFileNameBlob());

        for (String removedFile : removed) {
            filesToCommit.remove(removedFile);
        }
        writeFile(REMOVED_FILES, "");

        for (Map.Entry<String, String> entry : staged.entrySet()) {
            saveFile(entry.getKey());
            filesToCommit.put(entry.getKey(), entry.getValue());
        }
        String preId = readCommitId(currentBranch);
        Date date = new Date();
        Commit commit = new Commit(message, preId, preSubCommitId, depth, date);
        commit.attachBlob(filesToCommit);
        writeCommit(currentBranch, commit);
    }
    public void commit(String message) {
        if (message.isEmpty()) {
            throw new GitletException("Please enter a commit message.");
        }
        if (removed.isEmpty() && staged.isEmpty()) {
            throw new GitletException("No changes added to the commit.");
        }
        commit(message, null, currentCommit.getDepth() + 1);
    }

    public void rm(String filename) {
        boolean flag = true;
        if (staged.containsKey(filename)) {
            deleteFile(filename, STAGE_DIR);
            flag = false;
        }
        if (currentCommit.getFileNameBlob().containsKey(filename)) {
            removed.add(filename);
            writeRemovedFiles(removed);
            if (working.containsKey(filename)) {
                deleteFile(filename, CWD);
            }
            flag = false;
        }
        if (flag) {
            throw new GitletException("Could not find file " + filename);
        }
    }

    public List<Commit> getHistoryCommits() {
        List<Commit> commits = new ArrayList<>();
        commits.add(currentCommit);
        String previousCommitId = currentCommit.getPreId();
        while (previousCommitId != null) {
            Commit previousCommit = readCommit(previousCommitId);
            commits.add(previousCommit);
            previousCommitId = previousCommit.getPreId();
        }
        return commits;
    }
    public String getCurrentCommitId() {
        return readCommitId(currentBranch);
    }

    public void createBranch(String branchName) {
        if (getBranches().contains(branchName)) {
            throw new GitletException("Branch " + branchName + " already exists");
        }
        writeFile(branchName, getCurrentCommitId(), BRANCH_DIR);
    }
    public void rmBranch(String branchName) {
        if (!getBranches().contains(branchName)) {
            throw new GitletException("A branch with that name does not exist.");
        }
        if (currentBranch.equals(branchName)) {
            throw new GitletException("Cannot remove the current branch.");
        }
        deleteFile(branchName, BRANCH_DIR);
    }
    private void checkout(String filename, String blobId) {
        byte[] content = readBlob(blobId);
        writeWorking(filename, content);
    }
    private void checkoutFile(Commit commit, String filename) {
        Map<String, String> filesOfCommit = commit.getFileNameBlob();
        if (!filesOfCommit.containsKey(filename)) {
            throw new GitletException("File does not exist in that commit.");
        }
        String blob = filesOfCommit.get(filename);
        checkout(filename, blob);
    }
    public void checkoutFile(String filename) {
        checkoutFile(currentCommit, filename);
    }
    public void checkoutFile(String commitId, String filename) {
        Commit commit = readCommit(commitId);
        checkoutFile(commit, filename);
    }
    public void checkoutBranch(String branchName) {
        if (!getBranches().contains(branchName)) {
            throw new GitletException("No such branch exists.");
        }
        if (currentBranch.equals(branchName)) {
            throw new GitletException("No need to checkout the current branch.");
        }

        Commit commit = readBranchCommit(branchName);
        Map<String, String> commitFiles = commit.getFileNameBlob();
        List<String> untrackedFiles = getUntrackedFiles();

        // if untracked files will be overwritten, throw an error.
        for (String fileName : untrackedFiles) {
            String untrackedBlob = working.get(fileName);
            String commitBlob = commitFiles.get(fileName);
            if (commitBlob != null && !commitBlob.equals(untrackedBlob)) {
                throw new UntrackedFilesException();
            }
        }

        for (String filename : working.keySet()) {
            if (!commitFiles.containsKey(filename) && !untrackedFiles.contains(filename)) {
                deleteFile(filename, CWD);
            }
        }
        clearDirectory(STAGE_DIR);

        for (Map.Entry<String, String> entry : commitFiles.entrySet()) {
            checkout(entry.getKey(), entry.getValue());
        }

        writeFile(REMOVED_FILES, "");
        writeFile(CURRENT_BRANCH, branchName);
    }

    public void reset(String commitId) {
        Commit commit = readCommit(commitId);
        Map<String, String> resetFiles = commit.getFileNameBlob();
        Map<String, String> currentFiles = currentCommit.getFileNameBlob();

        List<String> untrackedFiles = getUntrackedFiles();
        for (String filename : untrackedFiles) {
            if (resetFiles.containsKey(filename)) {
                throw new UntrackedFilesException();
            }
        }
        for (String filename : currentFiles.keySet()) {
            if (!resetFiles.containsKey(filename)) {
                deleteFile(filename, CWD);
            }
        }
        for (Map.Entry<String, String> entry : resetFiles.entrySet()) {
            checkout(entry.getKey(), entry.getValue());
        }
        writeFile(REMOVED_FILES, "");
        clearDirectory(STAGE_DIR);
        writeFile(currentBranch, commitId, BRANCH_DIR);

    }

    private Map<String, Integer> getCommitDepths(String commitId) {
        Map<String, Integer> depths = new HashMap<>();
        Stack<String> commitIdStack = new Stack<>();
        commitIdStack.push(commitId);
        while (!commitIdStack.isEmpty()) {
            String id = commitIdStack.pop();
            if (depths.containsKey(id)) {
                continue;
            }
            Commit commit = readCommit(id);
            depths.put(id, commit.getDepth());
            String preId = commit.getPreId();
            String preSubId = commit.getPreSubId();
            if (preId != null) {
                commitIdStack.push(preId);
            }
            if (preSubId != null) {
                commitIdStack.push(preSubId);
            }
        }
        return depths;
    }

    private String getSplitId(String currentId, String mergedId) {

        Map<String, Integer> leftDepths = getCommitDepths(currentId);
        Map<String, Integer> rightDepths = getCommitDepths(mergedId);
        Set<String> commonAncestors = new HashSet<>(leftDepths.keySet());
        commonAncestors.retainAll(rightDepths.keySet());

        int maxDepth = -1;
        String spiltId = null;
        for (String id : commonAncestors) {
            int depth = leftDepths.get(id);
            if (depth > maxDepth) {
                maxDepth = depth;
                spiltId = id;
            }
        }
        if (spiltId == null) {
            throw new GitletException("No common ancestor!");
        }
        if (spiltId.equals(mergedId)) {
            throw new GitletException("Given branch is an ancestor of the current branch.");
        }
        return spiltId;

    }
    private void mergeConflict(String filename, String blobA, String blobB) {
        String contentA = "";
        if (blobA != null) {
            contentA = readFileAsString(blobA, BLOBS_DIR);
        }
        String contentB = "";
        if (blobB != null) {
            contentB = readFileAsString(blobB, BLOBS_DIR);
        }
        String content = "<<<<<<< HEAD\n" + contentA + "=======\n" + contentB + ">>>>>>>\n";
        writeFile(filename, content, STAGE_DIR);
        writeFile(filename, content, CWD);
    }
    public String merge(String branchName) {
        if (!getBranches().contains(branchName)) {
            throw new GitletException("A branch with that name does not exist.");
        }
        if (!(staged.isEmpty() && removed.isEmpty())) {
            throw new GitletException("You have uncommitted changes.");
        }
        if (!getUntrackedFiles().isEmpty()) {
            throw new UntrackedFilesException();
        }
        if (currentBranch.equals(branchName)) {
            throw new GitletException("Cannot merge a branch with itself.");
        }
        String mergedCommitId = readCommitId(branchName);
        String currentCommitId = readCommitId(currentBranch);
        String spiltId = getSplitId(currentCommitId, mergedCommitId);
        if (spiltId.equals(currentCommitId)) {
            writeFile(currentBranch, mergedCommitId, BRANCH_DIR);
            checkoutBranch(branchName);
            throw new GitletException("Current branch fast-forwarded.");
        }
        Commit mergedCommit = readCommit(mergedCommitId);
        Map<String, String> currentFiles = currentCommit.getFileNameBlob();
        Map<String, String> mergedFiles = mergedCommit.getFileNameBlob();
        Map<String, String> spiltFiles = readCommit(spiltId).getFileNameBlob();
        Set<String> possibleFiles = new HashSet<>();
        possibleFiles.addAll(currentFiles.keySet());
        possibleFiles.addAll(mergedFiles.keySet());
        possibleFiles.addAll(spiltFiles.keySet());
        for (Map.Entry<String, String> entry : currentFiles.entrySet()) {
            if (entry.getValue().equals(mergedFiles.get(entry.getKey()))) {
                possibleFiles.remove(entry.getKey());
            }
        }
        boolean inConflict = false;
        for (String filename : possibleFiles) {
            String currentBlob = currentFiles.get(filename);
            String mergedBlob = mergedFiles.get(filename);
            String spiltBlob = spiltFiles.get(filename);
            if (currentBlob == null) {
                if (mergedBlob == null || mergedBlob.equals(spiltBlob)) {
                    continue;
                }
                if (spiltBlob == null) {
                    writeFile(filename, readBlob(mergedBlob), STAGE_DIR);
                    writeFile(filename, readBlob(mergedBlob), CWD);
                } else {
                    mergeConflict(filename, null, mergedBlob);
                    inConflict = true;
                }
            } else {
                if (spiltBlob == null) {
                    if (mergedBlob != null) {
                        mergeConflict(filename, currentBlob, mergedBlob);
                        inConflict = true;
                    }
                    continue;
                }
                if (spiltBlob.equals(currentBlob)) {
                    if (mergedBlob != null) {
                        writeFile(filename, readBlob(mergedBlob), STAGE_DIR);
                        writeFile(filename, readBlob(mergedBlob), CWD);
                    } else {
                        removed.add(filename);
                        deleteFile(filename, CWD);
                    }
                } else {
                    if (!spiltBlob.equals(mergedBlob)) {
                        mergeConflict(filename, currentBlob, mergedBlob);
                        inConflict = true;
                    }
                }
            }
        }
        staged = readPlainFiles(STAGE_DIR);
        int depth = Math.max(currentCommit.getDepth(), mergedCommit.getDepth());
        commit("Merged " + branchName + " into " + currentBranch + ".", mergedCommitId, depth + 1);
        return inConflict ? "Encountered a merge conflict." : "";
    }
}
